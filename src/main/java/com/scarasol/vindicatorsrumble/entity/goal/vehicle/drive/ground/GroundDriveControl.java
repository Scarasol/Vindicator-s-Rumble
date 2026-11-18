package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground;

import com.scarasol.vindicatorsrumble.api.vehicle.DriveIntent;
import com.scarasol.vindicatorsrumble.api.vehicle.DriveRequest;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleDimensions;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleInput;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleMovementType;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.util.VehicleAiLogger;
import com.scarasol.vindicatorsrumble.util.VehicleAiTuning;
import com.scarasol.vindicatorsrumble.util.VehiclePathContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class GroundDriveControl {
    private static final double DIRECT_RAM_DISTANCE_SQR = 144.0D;
    private static final double RAM_WINDUP_START_DISTANCE_SQR = 36.0D;
    private static final double RAM_WINDUP_RELEASE_DISTANCE_SQR = 121.0D;
    private static final double RAM_FRONT_ARC_DEGREES = 12.0D;
    private static final double MANEUVER_BLOCKED_DISTANCE = 1.35D;
    private static final double MANEUVER_CONTACT_DISTANCE = 0.15D;
    private static final double MANEUVER_SIDE_SQUEEZE_DISTANCE = 1.45D;
    private static final double MANEUVER_EXIT_CLEARANCE = 2.25D;
    private static final double MANEUVER_PROGRESS_DISTANCE_SQR = 0.01D;
    private static final double MANEUVER_PROGRESS_YAW_DEGREES = 4.0D;
    private static final double PATH_TARGET_REPLAN_DISTANCE_SQR = 16.0D;
    private static final double CHASE_BAND_MIN_BRAKE_LEAD = 3.0D;
    private static final double CHASE_BAND_SPEED_BRAKE_LEAD_SCALE = 16.0D;
    private static final double CHASE_BAND_MAX_BRAKE_LEAD_RATIO = 0.45D;
    private static final int MANEUVER_PROBE_INTERVAL = 5;
    private static final int MANEUVER_STEP_MIN_TICKS = 4;

    private final Mob mob;
    private final Map<PathSegment, Boolean> pathClearance = new HashMap<>();
    private VehicleController controller;
    private State state = State.IDLE;
    private Path path;
    private boolean narrowPath;
    private boolean narrowPathAttempted;
    private PathPlanKey lastPathPlanKey;
    private int replanTicks;
    private int stuckTicks;
    private int turnDirection;
    private int ramWindupTicks;
    private int maneuverTicks;
    private int maneuverStepTicks;
    private int maneuverNoProgressTicks;
    private int maneuverTurnDirection;
    private int lastRadarTick;
    private boolean retreatingFromTarget;
    private boolean closingToTarget;
    private boolean brakingChaseBand;
    private GroundDrivePlan.Action action = GroundDrivePlan.Action.HOLD;
    private boolean smoke;
    private boolean maneuverMultiPointTurn;
    private GroundManeuverStep maneuverStep = GroundManeuverStep.BRAKE;
    private GroundClearance maneuverClearance = GroundClearance.open(GroundObstacleRadar.RANGE);
    private Vec3 lastPosition = Vec3.ZERO;
    private Vec3 reverseTurnWaypoint;
    private Vec3 navigationBrakeTarget;
    private Vec3 maneuverLastPosition = Vec3.ZERO;
    private boolean maneuverReverseStarted;
    private double maneuverReverseDistance;
    private float maneuverLastYaw;

    GroundDriveControl(Mob mob) {
        this.mob = mob;
    }

    public void start(VehicleController controller) {
        this.controller = controller;
        this.state = State.PLAN_PATH;
        this.path = null;
        this.lastPathPlanKey = null;
        this.replanTicks = 0;
        this.stuckTicks = 0;
        this.lastPosition = controller.position();
        this.turnDirection = 0;
        this.ramWindupTicks = 0;
        this.retreatingFromTarget = false;
        this.closingToTarget = false;
        this.brakingChaseBand = false;
        this.action = GroundDrivePlan.Action.HOLD;
        this.smoke = false;
        this.navigationBrakeTarget = null;
        this.resetManeuver(controller.position());
    }

    public void stop(VehicleController controller) {
        this.controller = controller;
        this.pathClearance.clear();
        this.path = null;
        this.lastPathPlanKey = null;
        this.state = State.IDLE;
        this.turnDirection = 0;
        this.ramWindupTicks = 0;
        this.stuckTicks = 0;
        this.retreatingFromTarget = false;
        this.closingToTarget = false;
        this.brakingChaseBand = false;
        this.smoke = false;
        this.navigationBrakeTarget = null;
        this.resetManeuver(controller.position());
        controller.stopDriving(this.mob);
    }

    enum Result {
        RUNNING,
        COMPLETE
    }

    Result execute(VehicleController controller, GroundDrivePlan plan, boolean smoke) {
        this.controller = controller;
        this.smoke = smoke;
        this.pathClearance.clear();
        if (this.action != plan.action()) {
            this.brakingChaseBand = false;
            this.navigationBrakeTarget = null;
        }
        this.action = plan.action();
        this.closingToTarget = plan.action() == GroundDrivePlan.Action.APPROACH;
        this.retreatingFromTarget = plan.action() == GroundDrivePlan.Action.RETREAT;
        DriveRequest request = plan.request();
        if (controller.isWreck() || plan.action() == GroundDrivePlan.Action.HOLD) {
            return this.holdPosition();
        }
        if (plan.navigation() && this.shouldBrakeForNavigation(request)) {
            Result result = this.holdPosition();
            if (result == Result.COMPLETE) {
                Vec3 target = this.navigationBrakeTarget;
                this.navigationBrakeTarget = null;
                double arrival = VehicleAiTuning.navigationArrivalDistance(controller, request.desiredDistance());
                if (this.horizontalDistanceSqr(controller.position(), target) > arrival * arrival) {
                    return Result.RUNNING;
                }
            }
            return result;
        }
        if (plan.action() == GroundDrivePlan.Action.AIM) {
            this.aimAtTarget(request);
            return Result.RUNNING;
        }
        if ((this.closingToTarget || this.retreatingFromTarget)
                && (this.brakingChaseBand || this.reachedCombatBand(plan))) {
            this.brakingChaseBand = true;
            Result result = this.brake();
            if (result == Result.COMPLETE) this.brakingChaseBand = false;
            this.stuckTicks = 0;
            this.lastPosition = controller.position();
            return result;
        }

        this.updateStuckState(request);
        if (this.state == State.MANEUVER) {
            this.tickManeuver(request);
        } else if (plan.action() == GroundDrivePlan.Action.RETREAT) {
            this.retreat(request);
        } else if (plan.action() == GroundDrivePlan.Action.RAM
                && request.targetEntity().filter(this::shouldUseRamWindup).isPresent()) {
            this.tickRamWindup(request.targetEntity().orElseThrow());
        } else {
            this.ramWindupTicks = 0;
            this.followRoute(request);
        }
        return Result.RUNNING;
    }

    private Result holdPosition() {
        this.prepareHold();
        return this.brake();
    }

    private void prepareHold() {
        this.path = null;
        this.state = State.IDLE;
        this.stuckTicks = 0;
        this.turnDirection = 0;
        this.ramWindupTicks = 0;
        this.brakingChaseBand = false;
        this.lastPosition = this.controller.position();
        this.resetManeuver(this.lastPosition);
    }

    private Result brake() {
        VehicleInput input = GroundSteering.brake(this.controller);
        if (input.equals(VehicleInput.NONE)) {
            this.submitInput(VehicleInput.NONE);
            return Result.COMPLETE;
        }
        this.submitInput(input);
        return Result.RUNNING;
    }

    private void submitInput(VehicleInput input) {
        if (this.smoke && !input.decoy()) {
            input = new VehicleInput(input.forward(), input.back(), input.left(), input.right(), input.brake(),
                    input.sprint(), input.up(), input.down(), input.fire(), true);
        }
        if (input.equals(VehicleInput.NONE)) {
            this.controller.stopDriving(this.mob);
        } else {
            this.controller.drive(this.mob, input);
        }
    }

    private boolean reachedCombatBand(GroundDrivePlan plan) {
        Entity target = plan.request().targetEntity().orElse(null);
        if (target == null || !target.isAlive()) return true;
        double distanceSqr = this.horizontalDistanceSqr(this.controller.position(), target.position());
        return shouldBrakeChaseBand(distanceSqr, plan.request().desiredDistance(), plan.outerDistance(),
                this.closingToTarget, this.retreatingFromTarget, this.brakingChaseBand,
                GroundSteering.shouldBrake(this.controller), this.horizontalSpeed());
    }

    private void aimAtTarget(DriveRequest request) {
        Vec3 target = this.driveTargetPosition(request).orElse(null);
        if (target == null) {
            this.holdPosition();
            return;
        }
        if (this.controller.estimatedGroundTurningRadius() <= 0.0D
                || Math.abs(this.yawDiffTo(target)) < 8.0D) {
            this.prepareHold();
            this.submitInput(GroundSteering.brakeToward(this.controller, target, true));
            return;
        }
        this.updateStuckState(request);
        if (this.state == State.MANEUVER) {
            this.tickManeuver(request);
            return;
        }
        GroundSteering.SteeringResult steering = GroundSteering.steerRamToward(
                this.controller, target, this.turnDirection);
        this.turnDirection = steering.turnDirection();
        if (!this.tickBlockedReverseTurn(request, steering.input())) {
            this.submitInput(steering.input());
        }
    }

    private void retreat(DriveRequest request) {
        Entity target = request.targetEntity().orElse(null);
        if (target == null || !target.isAlive()) {
            this.holdPosition();
            return;
        }
        GroundSteering.SteeringResult steering = GroundSteering.retreatFrom(
                this.controller, target.position(), this.turnDirection);
        this.turnDirection = steering.turnDirection();
        this.refreshManeuverClearance(false);
        if (isRetreatInputBlocked(steering.input(), this.maneuverClearance, MANEUVER_EXIT_CLEARANCE)) {
            this.beginManeuver(request, "retreat blocked");
            this.tickManeuver(request);
            return;
        }
        this.path = null;
        this.submitInput(steering.input());
    }

    private boolean tickBlockedReverseTurn(DriveRequest request, VehicleInput input) {
        if (!input.back()) return false;
        this.refreshManeuverClearance(false);
        double clearance = Math.max(MANEUVER_EXIT_CLEARANCE,
                0.5D + this.controller.estimatedHorizontalStoppingDistance());
        if (!isRetreatInputBlocked(input, this.maneuverClearance, clearance)) return false;
        this.beginManeuver(request, "turning reverse blocked");
        this.tickManeuver(request);
        return true;
    }

    private boolean shouldUseRamWindup(Entity target) {
        if (this.controller.estimatedGroundTurningRadius() <= 0.0D) {
            this.ramWindupTicks = 0;
            return false;
        }
        double distanceSqr = this.horizontalDistanceSqr(this.controller.position(), target.position());
        double yawDiff = Math.abs(this.yawDiffTo(target.position()));
        if (this.ramWindupTicks > 0) {
            boolean shouldContinue = shouldContinueRamWindup(distanceSqr, yawDiff);
            if (!shouldContinue || !this.hasRamRunupSpace(target.position())) {
                this.ramWindupTicks = 0;
                return false;
            }
            return shouldContinue;
        }
        return shouldStartRamWindup(distanceSqr, yawDiff) && this.hasRamRunupSpace(target.position());
    }

    private boolean hasRamRunupSpace(Vec3 target) {
        this.refreshManeuverClearance(false);
        VehicleInput input = GroundSteering.ramWindupAway(this.controller, target, this.turnDirection).input();
        Vec3 away = this.forwardDirection().scale(input.back() ? -1.0D : 1.0D);
        Vec3 offset = this.controller.position().subtract(target);
        double along = offset.dot(away);
        double distance = Math.max(0.0D, -along + Math.sqrt(Math.max(0.0D,
                along * along + RAM_WINDUP_RELEASE_DISTANCE_SQR - offset.horizontalDistanceSqr())));
        double required = Math.min(GroundObstacleRadar.RANGE,
                Math.max(MANEUVER_EXIT_CLEARANCE, distance + this.controller.estimatedHorizontalStoppingDistance()));
        return !isRetreatInputBlocked(input, this.maneuverClearance, required);
    }

    public static boolean shouldStartRamWindup(double distanceSqr, double yawDiffDegrees) {
        return distanceSqr < RAM_WINDUP_START_DISTANCE_SQR
                && !isTargetInRamFrontArc(yawDiffDegrees);
    }

    public static boolean shouldContinueRamWindup(double distanceSqr, double yawDiffDegrees) {
        return distanceSqr < RAM_WINDUP_RELEASE_DISTANCE_SQR
                && !isTargetInRamFrontArc(yawDiffDegrees);
    }

    private static boolean isTargetInRamFrontArc(double yawDiffDegrees) {
        return Math.abs(yawDiffDegrees) <= RAM_FRONT_ARC_DEGREES;
    }

    private void tickRamWindup(Entity target) {
        GroundSteering.SteeringResult steering = GroundSteering.ramWindupAway(this.controller, target.position(), this.turnDirection);
        VehicleInput input = steering.input();
        this.refreshManeuverClearance(false);
        if (isRetreatInputBlocked(input, this.maneuverClearance,
                Math.max(MANEUVER_EXIT_CLEARANCE, 0.5D + this.controller.estimatedHorizontalStoppingDistance()))) {
            this.beginManeuver(DriveRequest.ram(target), "ram run-up blocked");
            this.tickManeuver(DriveRequest.ram(target));
            return;
        }
        this.ramWindupTicks++;
        this.turnDirection = steering.turnDirection();
        this.path = null;
        if (VehicleAiLogger.shouldLog(this.mob, 20)) {
            VehicleAiLogger.debugEvery(this.mob, 20,
                    "Ram windup target={} ticks={} distanceSqr={} yawDiff={} input={} velocity={}",
                    target.getId(),
                    this.ramWindupTicks,
                    this.horizontalDistanceSqr(this.controller.position(), target.position()),
                    Math.abs(this.yawDiffTo(target.position())),
                    input,
                    this.controller.velocity());
        }
        this.submitInput(input);
    }

    private boolean shouldBrakeForNavigation(DriveRequest request) {
        if (request.desiredDistance() <= 0.0D) {
            return false;
        }
        Vec3 target = this.navigationTargetPosition(request).orElse(null);
        if (target == null) return false;
        if (!target.equals(this.navigationBrakeTarget)) this.navigationBrakeTarget = null;
        if (this.navigationBrakeTarget != null) return true;
        double arrival = VehicleAiTuning.navigationArrivalDistance(this.controller, request.desiredDistance());
        Vec3 offset = target.subtract(this.controller.position()).multiply(1.0D, 0.0D, 1.0D);
        Vec3 direction = this.controller.velocity().multiply(1.0D, 0.0D, 1.0D).normalize();
        double travel = Mth.clamp(offset.dot(direction), 0.0D, this.controller.estimatedHorizontalStoppingDistance());
        // Sideways or departing motion must not be mistaken for an imminent arrival.
        if (offset.subtract(direction.scale(travel)).lengthSqr() <= arrival * arrival) {
            this.navigationBrakeTarget = target;
        }
        return this.navigationBrakeTarget != null;
    }

    private void planPath(DriveRequest request) {
        this.narrowPath = false;
        this.narrowPathAttempted = false;
        VehicleDimensions dimensions = this.controller.pathingDimensions();
        PathTarget pathTarget = this.pathTarget(request);
        PathPlanKey nextKey = pathTarget == null ? null : this.pathPlanKey(request, pathTarget, dimensions);
        if (this.lastPathPlanKey == null || nextKey == null
                || this.lastPathPlanKey.routeIdentityChanged(nextKey)
                || this.lastPathPlanKey.targetMovedSignificantly(nextKey)) {
            this.reverseTurnWaypoint = null;
        }
        if (pathTarget == null) {
            this.path = null;
        } else if (request.path().isPresent() && this.path != request.path().get()) {
            this.path = request.path().get();
            this.path.setNextNodeIndex(0);
        } else if (request.path().isEmpty()) {
            try (VehiclePathContext.Scope ignored = VehiclePathContext.push(this.mob, dimensions)) {
                this.path = this.mob.getNavigation().createPath(pathTarget.targetBlock(), 1);
            }
        }
        this.lastPathPlanKey = nextKey;
        if (pathTarget != null && (this.path == null || this.path.getNodeCount() <= 1
                || (!this.path.canReach() && this.horizontalDistanceSqr(
                nodeCenter(this.path.getEndNode()), pathTarget.targetPosition()) > this.nodePassDistanceSqr()))) {
            boolean noProgress = this.path == null || this.path.getNodeCount() <= 1
                    || Math.sqrt(this.horizontalDistanceSqr(nodeCenter(this.path.getEndNode()), pathTarget.targetPosition()))
                    + Math.sqrt(this.nodePassDistanceSqr())
                    >= Math.sqrt(this.horizontalDistanceSqr(this.controller.position(), pathTarget.targetPosition()));
            if (noProgress || (this.hasStraightPathPrefix() && !this.hasConservativeStraightPathPrefix(dimensions))) {
                this.planNarrowPath(pathTarget, dimensions);
            }
        }
        this.replanTicks = CommonConfig.VEHICLE_PATH_REPLAN_INTERVAL.get();
        if (VehicleAiLogger.isDebugEnabled()) {
            VehicleAiLogger.debug(this.mob,
                    "Planned vehicle path detail intent={} source={} targetBlock={} targetEntity={} targetPos={} vehiclePos={} narrow={} dimensions={} pathTarget={} nodes={} done={} nextIndex={} firstNode={} nextNode={} lastNode={} replanTicks={}",
                    request.intent(),
                    pathTarget == null ? "none" : pathTarget.source(),
                    pathTarget == null ? null : pathTarget.targetBlock(),
                    pathTarget == null ? -1 : pathTarget.targetEntityId(),
                    pathTarget == null ? null : pathTarget.targetPosition(),
                    this.controller.position(),
                    this.narrowPath,
                    dimensions,
                    this.pathTargetSummary(this.path),
                    this.nodeCount(this.path),
                    this.path != null && this.path.isDone(),
                    this.nextNodeIndex(this.path),
                    this.nodeSummary(this.path, 0),
                    this.nextNodeSummary(this.path),
                    this.nodeSummary(this.path, this.nodeCount(this.path) - 1),
                    this.replanTicks);
        }
    }

    private boolean hasStraightPathPrefix() {
        Vec3 from = this.controller.position();
        return this.isPathSegmentClear(from,
                from.add(this.forwardDirection().scale(8.0D)), this.controller.pathingDimensions());
    }

    private boolean hasConservativeStraightPathPrefix(VehicleDimensions dimensions) {
        Vec3 from = this.controller.position();
        double span = Math.ceil(dimensions.conservativeHorizontalSize());
        return this.isPathSegmentClear(from,
                from.add(this.forwardDirection().scale(8.0D)),
                new VehicleDimensions(span, dimensions.height(), span));
    }

    private void planNarrowPath(PathTarget target, VehicleDimensions dimensions) {
        this.narrowPathAttempted = true;
        try (VehiclePathContext.Scope ignored = VehiclePathContext.push(this.mob, dimensions, true)) {
            Path candidate = this.mob.getNavigation().createPath(target.targetBlock(), 1);
            if (candidate != null && candidate.getNodeCount() > 1
                    && (candidate.canReach()
                    || Math.sqrt(this.horizontalDistanceSqr(nodeCenter(candidate.getEndNode()), target.targetPosition()))
                    + Math.sqrt(this.nodePassDistanceSqr())
                    < Math.sqrt(this.horizontalDistanceSqr(this.controller.position(), target.targetPosition())))) {
                this.path = candidate;
                this.narrowPath = true;
            }
        }
    }

    private boolean shouldReplanPath(DriveRequest request) {
        return shouldReplanPath(
                this.lastPathPlanKey,
                this.pathPlanKey(request),
                this.replanTicks,
                this.path != null && !this.path.isDone());
    }

    static boolean shouldReplanPath(PathPlanKey lastKey, PathPlanKey nextKey, int replanTicks, boolean pathReady) {
        if (!pathReady || lastKey == null || nextKey == null) {
            return true;
        }
        if (lastKey.routeIdentityChanged(nextKey)) {
            return true;
        }
        if (replanTicks > 0) {
            return false;
        }
        return lastKey.targetMovedSignificantly(nextKey);
    }

    private PathPlanKey pathPlanKey(DriveRequest request) {
        PathTarget target = this.pathTarget(request);
        return target == null ? null : this.pathPlanKey(request, target, this.controller.pathingDimensions());
    }

    private PathPlanKey pathPlanKey(DriveRequest request, PathTarget target, VehicleDimensions dimensions) {
        return new PathPlanKey(
                request.intent(),
                target.targetEntityId(),
                target.targetBlock(),
                target.targetPosition(),
                dimensions,
                request.path().map(System::identityHashCode).orElse(0));
    }

    private PathTarget pathTarget(DriveRequest request) {
        if (request.targetPosition().isPresent()) {
            Vec3 position = request.targetPosition().get();
            return new PathTarget(BlockPos.containing(position), -1, position, "position");
        }
        if (request.targetEntity().isPresent()) {
            Entity target = request.targetEntity().get();
            if (!target.isAlive()) {
                return null;
            }
            return new PathTarget(target.blockPosition(), target.getId(), target.position(), "entity");
        }
        return null;
    }

    private Vec3 nextWaypoint(DriveRequest request, boolean directRamTarget) {
        if (directRamTarget) {
            Entity target = request.targetEntity().get();
            Vec3 waypoint = target.position();
            VehicleAiLogger.debugEvery(this.mob, 10,
                    "Drive waypoint direct ram target={} vehiclePos={} targetPos={} horizontalDistance={} waypoint={}",
                    target.getId(),
                    this.controller.position(),
                    waypoint,
                    Math.sqrt(this.horizontalDistanceSqr(this.controller.position(), waypoint)),
                    waypoint);
            return waypoint;
        }
        if (this.path != null) {
            Vec3 position = this.controller.position();
            double passDistanceSqr = this.nodePassDistanceSqr();
            int beforeIndex = this.path.getNextNodeIndex();
            this.advancePastReachedNodes();
            int advanced = this.path.getNextNodeIndex() - beforeIndex;
            if (!this.path.isDone()) {
                Vec3 waypoint = this.lookaheadWaypoint(position);
                if (waypoint == null) {
                    return null;
                }
                if (!this.narrowPath && !this.narrowPathAttempted
                        && !this.isPathSegmentClear(
                        position, waypoint, this.controller.pathingDimensions())
                        && this.hasStraightPathPrefix()) {
                    PathTarget target = this.pathTarget(request);
                    if (target != null) {
                        this.planNarrowPath(target, this.controller.pathingDimensions());
                        this.advancePastReachedNodes();
                        if (!this.path.isDone()) {
                            waypoint = this.lookaheadWaypoint(position);
                            if (waypoint == null) return null;
                        }
                    }
                }
                if (VehicleAiLogger.shouldLog(this.mob, 10)) {
                    VehicleAiLogger.debug(this.mob,
                            "Drive waypoint path intent={} vehiclePos={} pathTarget={} nodes={} indexBefore={} indexAfter={} advanced={} nextNode={} waypoint={} waypointDistance={} lookahead={} passDistance={} speed={} dimensions={}",
                            request.intent(),
                            position,
                            this.pathTargetSummary(this.path),
                            this.path.getNodeCount(),
                            beforeIndex,
                            this.path.getNextNodeIndex(),
                            advanced,
                            this.nextNodeSummary(this.path),
                            waypoint,
                            Math.sqrt(this.horizontalDistanceSqr(position, waypoint)),
                            this.lookaheadDistance(),
                            Math.sqrt(passDistanceSqr),
                            this.horizontalSpeed(),
                            this.controller.pathingDimensions());
                }
                return waypoint;
            }
            VehicleAiLogger.debugEvery(this.mob, 10,
                    "Drive waypoint path exhausted intent={} vehiclePos={} pathTarget={} nodes={} indexBefore={} advanced={} targetPos={} targetEntity={}",
                    request.intent(),
                    position,
                    this.pathTargetSummary(this.path),
                    this.path.getNodeCount(),
                    beforeIndex,
                    advanced,
                    request.targetPosition().orElse(null),
                    request.targetEntity().map(Entity::getId).orElse(-1));
            if (request.intent() == DriveIntent.MOVE_TO && request.path().isEmpty() && !this.path.canReach()) {
                this.planPath(request);
                if (this.path != null && this.path.getNodeCount() > 1
                        && this.horizontalDistanceSqr(position, nodeCenter(this.path.getEndNode())) > passDistanceSqr) {
                    return this.lookaheadWaypoint(position);
                }
            }
            if (this.shouldRecoverFromExhaustedPath(request, directRamTarget)) {
                this.beginManeuver(request, "path exhausted");
                return null;
            }
        }
        if (request.targetPosition().isPresent()) {
            Vec3 waypoint = request.targetPosition().get();
            VehicleAiLogger.debugEvery(this.mob, 10,
                    "Drive waypoint fallback position intent={} vehiclePos={} waypoint={} horizontalDistance={}",
                    request.intent(),
                    this.controller.position(),
                    waypoint,
                    Math.sqrt(this.horizontalDistanceSqr(this.controller.position(), waypoint)));
            return waypoint;
        }
        if (request.targetEntity().isPresent()) {
            Entity target = request.targetEntity().get();
            Vec3 waypoint = target.position();
            VehicleAiLogger.debugEvery(this.mob, 10,
                    "Drive waypoint fallback entity intent={} target={} vehiclePos={} waypoint={} horizontalDistance={}",
                    request.intent(),
                    target.getId(),
                    this.controller.position(),
                    waypoint,
                    Math.sqrt(this.horizontalDistanceSqr(this.controller.position(), waypoint)));
            return waypoint;
        }
        return null;
    }

    private boolean shouldUseDirectRam(Entity target) {
        double distanceSqr = this.horizontalDistanceSqr(this.controller.position(), target.position());
        if (distanceSqr > DIRECT_RAM_DISTANCE_SQR) {
            return false;
        }
        Entity aimEntity = target.getVehicle() == null ? target : target.getVehicle();
        return this.controller.hasLineOfSight(this.mob, target, aimEntity.getBoundingBox().getCenter());
    }

    private void advancePastReachedNodes() {
        Vec3 position = this.controller.position();
        double passDistanceSqr = this.nodePassDistanceSqr();
        while (!this.path.isDone()
                && this.shouldAdvancePastNode(position, nodeCenter(this.path.getNextNode()), passDistanceSqr)) {
            if (this.narrowPath && this.path.getNextNodeIndex() + 1 < this.path.getNodeCount()
                    && !this.isPathSegmentClear(position,
                    nodeCenter(this.path.getNode(this.path.getNextNodeIndex() + 1)), this.controller.pathingDimensions())) {
                // Keep the straight lead until the complete body can take the next corner.
                break;
            }
            this.path.advance();
        }
    }

    private boolean shouldAdvancePastNode(Vec3 position, Vec3 waypoint, double passDistanceSqr) {
        double distanceSqr = position.distanceToSqr(waypoint);
        if (this.path.getNextNodeIndex() + 1 >= this.path.getNodeCount()) {
            // A partial path's end is not a corner that can be skipped at cruising lookahead.
            return !this.path.canReach() && distanceSqr <= 1.0D;
        }
        if (distanceSqr <= passDistanceSqr) {
            return true;
        }
        if (this.path.getNextNodeIndex() + 1 >= this.path.getNodeCount()) {
            return false;
        }
        double lookaheadDistance = this.lookaheadDistance();
        return distanceSqr <= lookaheadDistance * lookaheadDistance && this.isBehindVehicle(position, waypoint);
    }

    private boolean isBehindVehicle(Vec3 position, Vec3 waypoint) {
        double dx = waypoint.x - position.x;
        double dz = waypoint.z - position.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4D) {
            return false;
        }

        double yawRadians = Math.toRadians(this.controller.yaw());
        double forwardX = -Math.sin(yawRadians);
        double forwardZ = Math.cos(yawRadians);
        double dot = (forwardX * dx + forwardZ * dz) / length;
        return dot < -0.25D;
    }

    private Vec3 lookaheadWaypoint(Vec3 position) {
        int index = this.path.getNextNodeIndex();
        int nodeCount = this.path.getNodeCount();
        double remaining = this.lookaheadDistance();
        Vec3 previous = position;
        Vec3 fallback = null;
        for (int i = index; i < nodeCount; i++) {
            Vec3 point = nodeCenter(this.path.getNode(i));
            double segmentLength = previous.distanceTo(point);
            if (segmentLength >= remaining && segmentLength > 1.0E-4D) {
                Vec3 direction = point.subtract(previous).scale(1.0D / segmentLength);
                return this.clearLookahead(position, previous.add(direction.scale(remaining)), index);
            }
            remaining -= segmentLength;
            previous = point;
            fallback = point;
        }
        return this.clearLookahead(position, fallback, index);
    }

    private Vec3 clearLookahead(Vec3 position, Vec3 candidate, int index) {
        if (!this.narrowPath) {
            return candidate;
        }
        VehicleDimensions dimensions = this.controller.pathingDimensions();
        if (this.isPathSegmentClear(position, candidate, dimensions)) {
            return candidate;
        }
        Vec3 fallback = null;
        double lookaheadSqr = this.lookaheadDistance() * this.lookaheadDistance();
        // Only shorten an obstructed shortcut; do not cut across a narrow-road corner.
        for (int i = index; i < this.path.getNodeCount(); i++) {
            Vec3 point = nodeCenter(this.path.getNode(i));
            if (this.horizontalDistanceSqr(position, point) > lookaheadSqr) {
                break;
            }
            if (this.isPathSegmentClear(position, point, dimensions)) {
                fallback = point;
            }
        }
        if (fallback == null) {
            this.path = null;
            this.replanTicks = 0;
        }
        return fallback;
    }

    private boolean isPathSegmentClear(Vec3 from, Vec3 to, VehicleDimensions dimensions) {
        PathSegment segment = new PathSegment(from, to, dimensions);
        return this.pathClearance.computeIfAbsent(segment, ignored ->
                GroundPathCollision.isClear(this.controller.level(), this.controller.entity(), from, to, dimensions));
    }

    private boolean shouldStopAtWaypoint(DriveRequest request, Vec3 waypoint) {
        if (request.intent() == DriveIntent.RAM_TARGET) {
            return false;
        }
        // A speed-dependent lookahead is guidance, not an arrival point.
        return this.path == null || this.path.isDone()
                || (this.path.canReach() && waypoint.equals(nodeCenter(this.path.getEndNode())));
    }

    private double lookaheadDistance() {
        double speed = this.horizontalSpeed();
        return switch (this.controller.movementType()) {
            case TRACK -> clamp(6.0D + speed * 24.0D, 6.0D, 14.0D);
            case WHEEL -> clamp(8.0D + speed * 28.0D, 8.0D, 18.0D);
            default -> clamp(6.0D + speed * 20.0D, 6.0D, 14.0D);
        };
    }

    private double nodePassDistanceSqr() {
        double speed = this.horizontalSpeed();
        double distance = clamp(3.0D + speed * 8.0D, 3.0D, 6.0D);
        return distance * distance;
    }

    private double horizontalSpeed() {
        Vec3 velocity = this.controller.velocity();
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }

    private double longitudinalSpeed() {
        return this.controller.velocity().dot(this.forwardDirection());
    }

    private Vec3 forwardDirection() {
        double yaw = Math.toRadians(this.controller.yaw());
        return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
    }

    private double horizontalDistanceSqr(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return dx * dx + dz * dz;
    }

    private static Vec3 nodeCenter(Node node) {
        return new Vec3(node.x + 0.5D, node.y, node.z + 0.5D);
    }

    private int nodeCount(Path path) {
        return path == null ? 0 : path.getNodeCount();
    }

    private int nextNodeIndex(Path path) {
        return path == null ? -1 : path.getNextNodeIndex();
    }

    private Object pathTargetSummary(Path path) {
        return path == null ? null : path.getTarget();
    }

    private Object nextNodeSummary(Path path) {
        if (path == null || path.isDone() || path.getNodeCount() <= 0) {
            return null;
        }
        return this.nodeSummary(path, path.getNextNodeIndex());
    }

    private Object nodeSummary(Path path, int index) {
        if (path == null || index < 0 || index >= path.getNodeCount()) {
            return null;
        }
        Node node = path.getNode(index);
        return new BlockPos(node.x, node.y, node.z);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    static boolean shouldBrakeChaseBand(double distanceSqr, double stopDistance, double startDistance,
                                        boolean closing, boolean retreating, boolean alreadyBraking,
                                        boolean moving, double speed) {
        if (!moving || stopDistance <= 0.0D || startDistance <= stopDistance) {
            return false;
        }
        if (alreadyBraking) {
            return true;
        }
        return (closing && shouldBrakeClosingBeforeInnerBand(distanceSqr, stopDistance, startDistance, speed))
                || (retreating && shouldBrakeRetreatingBeforeOuterBand(distanceSqr, stopDistance, startDistance, speed));
    }

    static boolean shouldBrakeClosingBeforeInnerBand(double distanceSqr, double stopDistance, double startDistance, double speed) {
        if (stopDistance <= 0.0D || startDistance <= stopDistance) {
            return false;
        }
        double distance = Math.sqrt(distanceSqr);
        return distance <= stopDistance + chaseBandBrakeLeadDistance(speed, stopDistance, startDistance);
    }

    static boolean shouldBrakeRetreatingBeforeOuterBand(double distanceSqr, double stopDistance, double startDistance, double speed) {
        if (stopDistance <= 0.0D || startDistance <= stopDistance) {
            return false;
        }
        double distance = Math.sqrt(distanceSqr);
        return distance >= startDistance - chaseBandBrakeLeadDistance(speed, stopDistance, startDistance);
    }

    static double chaseBandBrakeLeadDistance(double speed, double stopDistance, double startDistance) {
        double bandWidth = startDistance - stopDistance;
        if (bandWidth <= 0.0D) {
            return 0.0D;
        }
        double distance = CHASE_BAND_MIN_BRAKE_LEAD + Math.max(0.0D, speed) * CHASE_BAND_SPEED_BRAKE_LEAD_SCALE;
        return clamp(distance, CHASE_BAND_MIN_BRAKE_LEAD, bandWidth * CHASE_BAND_MAX_BRAKE_LEAD_RATIO);
    }

    private double yawDiffTo(Vec3 target) {
        Vec3 position = this.controller.position();
        double dx = target.x - position.x;
        double dz = target.z - position.z;
        if (dx * dx + dz * dz < 1.0E-6D) {
            return 0.0D;
        }
        float targetYaw = (float) (Math.atan2(dz, dx) * (180F / Math.PI)) - 90.0F;
        return net.minecraft.util.Mth.wrapDegrees(targetYaw - this.controller.yaw());
    }

    private Optional<Vec3> driveTargetPosition(DriveRequest request) {
        if (request.targetEntity().isPresent() && request.targetEntity().get().isAlive()) {
            return Optional.of(request.targetEntity().get().position());
        }
        if (request.targetPosition().isPresent()) {
            return request.targetPosition();
        }
        return Optional.empty();
    }

    private Optional<Vec3> navigationTargetPosition(DriveRequest request) {
        if (request.targetPosition().isPresent()) {
            return request.targetPosition();
        }
        return request.targetEntity()
                .filter(Entity::isAlive)
                .map(Entity::position);
    }

    private void updateStuckState(DriveRequest request) {
        if (this.state == State.MANEUVER) {
            return;
        }
        Vec3 position = this.controller.position();
        if (!this.canUseManeuver(request)) {
            this.stuckTicks = 0;
            this.lastPosition = position;
            return;
        }
        if (this.controller.hasHorizontalCollision()
                && this.controller.velocity().horizontalDistanceSqr() < 0.003D) {
            this.beginManeuver(request, "ground collision");
            return;
        }
        if (this.mob.tickCount % 10 != 0) {
            return;
        }

        if (this.horizontalDistanceSqr(position, this.lastPosition) < 0.003D) {
            this.stuckTicks += 10;
        } else {
            this.stuckTicks = Math.max(0, this.stuckTicks - 10);
            this.lastPosition = position;
        }
        if (this.stuckTicks > 30) {
            this.beginManeuver(request, "no movement");
        }
    }

    private boolean canUseManeuver(DriveRequest request) {
        if (request.intent() == DriveIntent.NONE) {
            return false;
        }
        return this.driveTargetPosition(request).isPresent();
    }

    private void beginManeuver(DriveRequest request, String reason) {
        this.reverseTurnWaypoint = null;
        this.state = State.MANEUVER;
        this.path = null;
        this.replanTicks = 0;
        this.stuckTicks = 0;
        int previousTurnDirection = this.turnDirection;
        this.turnDirection = 0;
        this.maneuverTicks = 0;
        this.maneuverMultiPointTurn = false;
        this.maneuverStepTicks = 0;
        this.maneuverNoProgressTicks = 0;
        this.maneuverLastPosition = this.controller.position();
        this.maneuverLastYaw = this.controller.yaw();
        this.refreshManeuverClearance(true);

        double yawDiff = this.driveTargetPosition(request)
                .map(this::yawDiffTo)
                .orElse(0.0D);
        this.maneuverTurnDirection = selectManeuverTurnDirection(this.maneuverClearance, yawDiff, previousTurnDirection);
        GroundManeuverStep initialStep = selectInitialManeuverStep(this.maneuverClearance, false);
        boolean horizontalCollision = this.controller.hasHorizontalCollision();
        this.maneuverReverseStarted = horizontalCollision || initialStep == GroundManeuverStep.BACK_STRAIGHT;
        this.maneuverReverseDistance = 0.0D;
        double longitudinalSpeed = this.longitudinalSpeed();
        boolean changingDirection = initialStep == GroundManeuverStep.BACK_STRAIGHT
                ? longitudinalSpeed > 0.01D : longitudinalSpeed < -0.01D;
        this.maneuverStep = horizontalCollision
                || (changingDirection && this.controller.velocity().horizontalDistanceSqr() > 0.0225D)
                ? GroundManeuverStep.BRAKE : initialStep;
        VehicleAiLogger.debug(this.mob,
                "Drive entering maneuver vehicle={} reason={} intent={} step={} turn={} clearance={} velocity={} position={}",
                this.controller.entity().getId(),
                reason,
                request.intent(),
                this.maneuverStep,
                this.maneuverTurnDirection,
                this.maneuverClearance,
                this.controller.velocity(),
                this.controller.position());
    }

    private void tickManeuver(DriveRequest request) {
        if (!this.canUseManeuver(request)) {
            this.finishManeuver("request ended");
            return;
        }
        this.maneuverTicks++;
        this.refreshManeuverClearance(false);
        if (this.replanTicks > 0) {
            this.replanTicks--;
        }
        VehicleInput input = this.maneuverInput(request);
        boolean changingGear = input.brake() && this.maneuverStep != GroundManeuverStep.BRAKE;
        if (!changingGear) {
            this.updateManeuverProgress();
        }
        if (input.forward() && (input.left() || input.right())) {
            this.maneuverTurnDirection = input.left() ? -1 : 1;
        }
        VehicleAiLogger.debugEvery(this.mob, 10,
                "Drive maneuver step={} ticks={} stepTicks={} noProgress={} clearance={} input={} velocity={}",
                this.maneuverStep,
                this.maneuverTicks,
                this.maneuverStepTicks,
                this.maneuverNoProgressTicks,
                this.maneuverClearance,
                input,
                this.controller.velocity());
        this.submitInput(input);

        if (changingGear) {
            return;
        }
        this.maneuverStepTicks++;
        this.advanceManeuverStepIfNeeded(request, input);
    }

    private VehicleInput maneuverInput(DriveRequest request) {
        VehicleInput input = switch (this.maneuverStep) {
            case BRAKE -> VehicleInput.BRAKE;
            case BACK_STRAIGHT -> GroundSteering.maneuverBack(0);
            case BACK_TURN -> GroundSteering.maneuverBack(this.maneuverTurnDirection);
            case SWING_FORWARD -> {
                Vec3 waypoint = null;
                if (this.path != null && !this.path.isDone() && this.path.getNodeCount() > 1) {
                    this.advancePastReachedNodes();
                    if (!this.path.isDone()) {
                        waypoint = this.lookaheadWaypoint(this.controller.position());
                        if (waypoint != null && !this.isPathSegmentClear(this.controller.position(),
                                waypoint, this.controller.pathingDimensions())) {
                            waypoint = null;
                        }
                    }
                }
                boolean pathGuidance = waypoint != null;
                if (this.maneuverMultiPointTurn && !pathGuidance
                        && !this.maneuverClearance.isOpen(MANEUVER_EXIT_CLEARANCE)) {
                    // Both legs of a multi-point turn must add yaw, not undo the previous leg.
                    yield GroundSteering.maneuverForward(this.maneuverTurnDirection, false);
                }
                if (waypoint == null) waypoint = this.driveTargetPosition(request).orElse(null);
                int turn = waypoint == null ? this.maneuverTurnDirection
                        : selectManeuverForwardTurn(pathGuidance ? 0 : this.maneuverTurnDirection,
                        this.yawDiffTo(waypoint), this.maneuverClearance);
                if (turn == 0 && !pathGuidance && this.maneuverReverseStarted
                        && this.maneuverClearance.front() < GroundObstacleRadar.RANGE) {
                    turn = this.maneuverTurnDirection;
                }
                yield GroundSteering.maneuverForward(turn, false);
            }
        };
        double speed = this.longitudinalSpeed();
        if (this.maneuverMultiPointTurn
                && ((input.forward() && speed < -0.05D) || (input.back() && speed > 0.05D))) {
            return VehicleInput.BRAKE;
        }
        return input;
    }

    private void advanceManeuverStepIfNeeded(DriveRequest request, VehicleInput input) {
        if (input.brake() && this.maneuverStep != GroundManeuverStep.BRAKE) {
            return;
        }
        if (input.forward() && this.maneuverStepTicks >= MANEUVER_STEP_MIN_TICKS
                && this.controller.hasHorizontalCollision()
                && this.controller.velocity().horizontalDistanceSqr() < 0.003D
                && !this.maneuverClearance.backBlocked(MANEUVER_BLOCKED_DISTANCE)) {
            this.changeManeuverStep(GroundManeuverStep.BACK_STRAIGHT, "forward collision");
            return;
        }
        if (shouldAbortManeuverPulse(input, this.maneuverClearance, this.maneuverStepTicks)) {
            this.abortManeuverPulse(input);
            return;
        }
        if (input.back() && this.canSwingAfterBacking(request)) {
            this.changeManeuverStep(GroundManeuverStep.SWING_FORWARD, "front cleared after backing");
            return;
        }

        switch (this.maneuverStep) {
            case BRAKE -> {
                if (this.maneuverStepTicks >= 3
                        && !(this.maneuverClearance.frontBlocked(MANEUVER_CONTACT_DISTANCE)
                        && this.maneuverClearance.backBlocked(MANEUVER_CONTACT_DISTANCE))) {
                    if (!this.maneuverMultiPointTurn) {
                        double yawDiff = this.driveTargetPosition(request).map(this::yawDiffTo).orElse(0.0D);
                        this.maneuverTurnDirection = selectManeuverTurnDirection(this.maneuverClearance, yawDiff, 0);
                    }
                    GroundManeuverStep next = this.maneuverReverseStarted
                            && !this.maneuverClearance.backBlocked(MANEUVER_BLOCKED_DISTANCE)
                            ? GroundManeuverStep.BACK_STRAIGHT : selectInitialManeuverStep(this.maneuverClearance, false);
                    this.changeManeuverStep(next, "brake complete");
                }
            }
            case BACK_STRAIGHT -> {
                GroundManeuverStep nextStep = nextBackStraightStep(this.maneuverClearance, this.maneuverStepTicks, this.maneuverNoProgressTicks);
                if (nextStep == GroundManeuverStep.SWING_FORWARD) {
                    this.changeManeuverStep(GroundManeuverStep.SWING_FORWARD, "front clearance preferred");
                } else if (nextStep == GroundManeuverStep.BACK_TURN && this.maneuverNoProgressTicks >= 10) {
                    this.changeManeuverStep(GroundManeuverStep.BACK_TURN, "straight back no progress");
                } else if (nextStep == GroundManeuverStep.BACK_TURN
                        && !this.maneuverClearance.sideSqueezed(MANEUVER_SIDE_SQUEEZE_DISTANCE)
                        && this.maneuverClearance.front() < MANEUVER_EXIT_CLEARANCE) {
                    this.changeManeuverStep(GroundManeuverStep.BACK_TURN, "straight back complete");
                }
            }
            case BACK_TURN -> {
                GroundManeuverStep nextStep = nextBackTurnStep(this.maneuverClearance, this.maneuverStepTicks, this.maneuverNoProgressTicks);
                if (nextStep == GroundManeuverStep.SWING_FORWARD) {
                    this.changeManeuverStep(GroundManeuverStep.SWING_FORWARD, "front clearance preferred");
                } else if (this.maneuverStepTicks >= 16 && this.maneuverNoProgressTicks >= 10) {
                    this.reverseManeuverTurnDirection("turn back no progress");
                } else if (nextStep == GroundManeuverStep.BACK_STRAIGHT) {
                    this.changeManeuverStep(GroundManeuverStep.BACK_STRAIGHT, "keep backing through rear clearance");
                }
            }
            case SWING_FORWARD -> {
                if (this.maneuverStepTicks >= MANEUVER_STEP_MIN_TICKS
                        && this.maneuverClearance.front() < MANEUVER_EXIT_CLEARANCE
                        && this.maneuverClearance.back() > this.maneuverClearance.front() + 0.75D) {
                    this.changeManeuverStep(GroundManeuverStep.BACK_TURN, "back clearance preferred");
                } else if (this.maneuverStepTicks >= 16 && this.maneuverNoProgressTicks >= 10) {
                    this.reverseManeuverTurnDirection("forward no progress");
                } else {
                    boolean pulseExpired = this.maneuverStepTicks >= this.forwardPulseTicks();
                    boolean earlyExit = this.shouldFinishManeuverEarly(request);
                    if (pulseExpired || earlyExit) {
                        this.logManeuverExitDecision(request, pulseExpired, earlyExit);
                        Optional<Vec3> target = this.driveTargetPosition(request);
                        if (target.isPresent() && this.canResumePath(request, target.get())) {
                            this.finishManeuver("reacquire path");
                        }
                    }
                }
            }
        }
    }

    private boolean canSwingAfterBacking(DriveRequest request) {
        if (this.maneuverStepTicks < 6 || this.maneuverNoProgressTicks > 0
                || !this.maneuverReverseStarted || this.longitudinalSpeed() > -0.05D
                || this.maneuverClearance.front() < MANEUVER_EXIT_CLEARANCE
                || this.lastRadarTick != this.mob.tickCount) {
            return false;
        }
        double requiredDistance = Math.max(1.5D, this.controller.localCollisionBounds().getZsize() * 0.35D);
        if (this.maneuverReverseDistance < requiredDistance) {
            return false;
        }
        double yawDifference = this.driveTargetPosition(request).map(this::yawDiffTo).orElse(0.0D);
        int turn = this.maneuverMultiPointTurn ? this.maneuverTurnDirection
                : selectManeuverForwardTurn(this.maneuverTurnDirection, yawDifference, this.maneuverClearance);
        if (turn == 0 && this.maneuverClearance.front() < GroundObstacleRadar.RANGE) {
            turn = this.maneuverTurnDirection;
        }
        return (turn == 0 || (turn < 0 ? this.maneuverClearance.left() : this.maneuverClearance.right())
                >= MANEUVER_SIDE_SQUEEZE_DISTANCE)
                && GroundObstacleRadar.hasForwardTurnSpace(this.controller, turn, yawDifference);
    }

    private boolean canResumePath(DriveRequest request, Vec3 target) {
        if (this.longitudinalSpeed() < 0.1D || this.controller.hasHorizontalCollision()) {
            return false;
        }
        Vec3 waypoint;
        VehicleInput nextInput;
        if (request.intent() == DriveIntent.RAM_TARGET
                && request.targetEntity().filter(this::shouldUseDirectRam).isPresent()) {
            waypoint = target;
            nextInput = this.postManeuverInput(request, target);
        } else {
            if (this.replanTicks > 0 && this.path == null) {
                return false;
            }
            if (this.replanTicks <= 0 && this.shouldReplanPath(request)) {
                this.planPath(request);
            }
            if (this.path == null) {
                return false;
            }
            this.advancePastReachedNodes();
            if (this.path.getNodeCount() <= 1 || this.path.isDone() || (!this.path.canReach()
                    && Math.sqrt(this.horizontalDistanceSqr(nodeCenter(this.path.getEndNode()), target))
                    + Math.sqrt(this.nodePassDistanceSqr())
                    >= Math.sqrt(this.horizontalDistanceSqr(this.controller.position(), target)))) {
                // A partial path back to the same dead end is not a handoff out of recovery.
                this.path = null;
                return false;
            }
            waypoint = this.lookaheadWaypoint(this.controller.position());
            if (waypoint == null) {
                return false;
            }
            GroundSteering.SteeringResult steering = GroundSteering.steerToward(this.controller, waypoint,
                    this.horizontalDistanceSqr(this.controller.position(), waypoint),
                    this.shouldStopAtWaypoint(request, waypoint),
                    request.preferStableFire(), this.turnDirection);
            // A brake-before-reverse command still needs a clear reverse leg.
            if (steering.requiresReverse() && this.maneuverClearance.back()
                    < Math.max(MANEUVER_EXIT_CLEARANCE, 0.5D + this.controller.estimatedHorizontalStoppingDistance())) {
                return false;
            }
            nextInput = steering.input();
        }
        boolean openSpace = this.maneuverClearance.isOpen(GroundObstacleRadar.RANGE);
        // A wall beside a safe exit is not a reason to keep the recovery turn running.
        if ((this.maneuverMultiPointTurn && (!nextInput.forward() || nextInput.back() || nextInput.brake()))
                || (!openSpace && nextInput.back())
                || (request.intent() == DriveIntent.RAM_TARGET && nextInput.brake()
                && this.maneuverClearance.back() < MANEUVER_EXIT_CLEARANCE)
                || (!nextInput.forward() && !nextInput.back() && !nextInput.brake())
                || (this.maneuverMultiPointTurn && (!this.narrowPath || this.path == null)
                && Math.abs(this.yawDiffTo(waypoint)) >= 35.0D)
                || (this.maneuverMultiPointTurn && !this.isPathSegmentClear(this.controller.position(),
                waypoint, this.controller.pathingDimensions()))
                || !canExitManeuverWithInput(nextInput, this.maneuverClearance)) {
            return false;
        }
        int nextTurn = nextInput.left() ? -1 : nextInput.right() ? 1 : 0;
        return openSpace || nextTurn == 0 || (!this.maneuverMultiPointTurn && nextTurn == this.maneuverTurnDirection)
                || (this.lastRadarTick == this.mob.tickCount
                && GroundObstacleRadar.hasForwardTurnSpace(this.controller, nextTurn, this.yawDiffTo(waypoint)));
    }

    private void abortManeuverPulse(VehicleInput input) {
        if (this.maneuverClearance.frontBlocked(MANEUVER_CONTACT_DISTANCE)
                && this.maneuverClearance.backBlocked(MANEUVER_CONTACT_DISTANCE)) {
            this.changeManeuverStep(GroundManeuverStep.BRAKE, "both directions blocked");
            return;
        }
        if (this.maneuverClearance.frontBlocked(MANEUVER_BLOCKED_DISTANCE)
                && this.maneuverClearance.backBlocked(MANEUVER_BLOCKED_DISTANCE)) {
            double movingClearance = input.forward() ? this.maneuverClearance.front() : this.maneuverClearance.back();
            double oppositeClearance = input.forward() ? this.maneuverClearance.back() : this.maneuverClearance.front();
            if (movingClearance >= oppositeClearance) {
                return;
            }
        }
        if (input.forward()) {
            this.changeManeuverStep(GroundManeuverStep.BACK_TURN, "forward pulse blocked");
        } else if (input.back()) {
            this.maneuverMultiPointTurn = true;
            this.changeManeuverStep(GroundManeuverStep.SWING_FORWARD, "back pulse blocked");
        }
    }

    private boolean shouldFinishManeuverEarly(DriveRequest request) {
        if (this.maneuverStepTicks < 6 || this.maneuverNoProgressTicks > 0) {
            return false;
        }
        Optional<Vec3> target = this.driveTargetPosition(request);
        if (target.isEmpty()) {
            return false;
        }
        double yawDiff = this.yawDiffTo(target.get());
        if (Math.abs(yawDiff) >= 35.0D) {
            return false;
        }
        VehicleInput nextInput = this.postManeuverInput(request, target.get());
        boolean canExit = canExitManeuverWithInput(nextInput, this.maneuverClearance);
        if (!canExit) {
            VehicleAiLogger.debugEvery(this.mob, 10,
                    "Drive maneuver holding exit: nextInput={} clearance={} yawDiff={}",
                    nextInput,
                    this.maneuverClearance,
                    yawDiff);
        }
        return canExit;
    }

    private VehicleInput postManeuverInput(DriveRequest request, Vec3 target) {
        if (request.intent() == DriveIntent.RAM_TARGET) {
            double distanceSqr = this.horizontalDistanceSqr(this.controller.position(), target);
            double yawDiff = Math.abs(this.yawDiffTo(target));
            if (this.controller.estimatedGroundTurningRadius() > 0.0D
                    && (this.ramWindupTicks > 0 ? shouldContinueRamWindup(distanceSqr, yawDiff)
                    : shouldStartRamWindup(distanceSqr, yawDiff))) {
                if (this.hasRamRunupSpace(target)) {
                    return GroundSteering.ramWindupAway(this.controller, target, this.turnDirection).input();
                }
            }
            return GroundSteering.steerRamToward(this.controller, target, this.turnDirection).input();
        }
        if (this.action == GroundDrivePlan.Action.RETREAT) {
            return GroundSteering.retreatFrom(this.controller, target, this.turnDirection).input();
        }
        return GroundSteering.steerToward(this.controller, target,
                this.horizontalDistanceSqr(this.controller.position(), target), request.preferStableFire(), this.turnDirection).input();
    }

    static boolean canExitManeuverWithInput(VehicleInput input, GroundClearance clearance) {
        if (input.forward() && clearance.front() < MANEUVER_EXIT_CLEARANCE) {
            return false;
        }
        if (input.back() && clearance.back() < MANEUVER_EXIT_CLEARANCE) {
            return false;
        }
        return true;
    }

    static boolean isRetreatInputBlocked(VehicleInput input, GroundClearance clearance, double requiredClearance) {
        if (clearance == null) {
            return false;
        }
        return (input.back() && clearance.back() < requiredClearance)
                || (input.forward() && clearance.front() < requiredClearance);
    }

    private void changeManeuverStep(GroundManeuverStep nextStep, String reason) {
        if (this.maneuverStep == nextStep) {
            return;
        }
        VehicleAiLogger.debug(this.mob,
                "Drive maneuver step change {} -> {} reason={} clearance={} turn={} position={}",
                this.maneuverStep,
                nextStep,
                reason,
                this.maneuverClearance,
                this.maneuverTurnDirection,
                this.controller.position());
        if ((nextStep == GroundManeuverStep.BACK_STRAIGHT || nextStep == GroundManeuverStep.BACK_TURN)
                && this.maneuverStep != GroundManeuverStep.BACK_STRAIGHT && this.maneuverStep != GroundManeuverStep.BACK_TURN) {
            this.maneuverReverseStarted = true;
            this.maneuverReverseDistance = 0.0D;
        }
        this.maneuverStep = nextStep;
        this.maneuverStepTicks = 0;
        this.maneuverNoProgressTicks = 0;
        this.maneuverLastPosition = this.controller.position();
        this.maneuverLastYaw = this.controller.yaw();
    }

    private void reverseManeuverTurnDirection(String reason) {
        int previousDirection = this.maneuverTurnDirection;
        this.maneuverTurnDirection = previousDirection == 0 ? this.maneuverClearance.openSideDirection(1) : -previousDirection;
        VehicleAiLogger.debug(this.mob,
                "Drive maneuver turn change {} -> {} reason={} clearance={} position={}",
                previousDirection,
                this.maneuverTurnDirection,
                reason,
                this.maneuverClearance,
                this.controller.position());
        this.maneuverStepTicks = 0;
        this.maneuverNoProgressTicks = 0;
        this.maneuverLastPosition = this.controller.position();
        this.maneuverLastYaw = this.controller.yaw();
    }

    private void finishManeuver(String reason) {
        VehicleAiLogger.debug(this.mob,
                "Drive maneuver finished reason={} ticks={} clearance={} velocity={} position={}",
                reason,
                this.maneuverTicks,
                this.maneuverClearance,
                this.controller.velocity(),
                this.controller.position());
        this.state = this.path == null ? State.PLAN_PATH : State.FOLLOW_PATH;
        this.stuckTicks = 0;
        this.lastPosition = this.controller.position();
        this.resetManeuver(this.controller.position());
    }

    private void logManeuverExitDecision(DriveRequest request, boolean pulseExpired, boolean earlyExit) {
        if (!VehicleAiLogger.shouldLog(this.mob, 5)) {
            return;
        }
        Optional<Vec3> target = this.driveTargetPosition(request);
        VehicleInput nextInput = target.map(value -> this.postManeuverInput(request, value)).orElse(VehicleInput.NONE);
        double yawDiff = target.map(this::yawDiffTo).orElse(0.0D);
        boolean canExit = target.isPresent() && canExitManeuverWithInput(nextInput, this.maneuverClearance);
        VehicleAiLogger.debug(this.mob,
                "Drive maneuver exit decision step={} pulseExpired={} earlyExit={} canExit={} nextInput={} forwardPulseTicks={} stepTicks={} noProgress={} yawDiff={} clearance={} retreating={} closing={} velocity={} position={}",
                this.maneuverStep,
                pulseExpired,
                earlyExit,
                canExit,
                nextInput,
                this.forwardPulseTicks(),
                this.maneuverStepTicks,
                this.maneuverNoProgressTicks,
                yawDiff,
                this.maneuverClearance,
                this.retreatingFromTarget,
                this.closingToTarget,
                this.controller.velocity(),
                this.controller.position());
    }

    private void refreshManeuverClearance(boolean force) {
        if (this.lastRadarTick == this.mob.tickCount
                || (!force && this.mob.tickCount - this.lastRadarTick < MANEUVER_PROBE_INTERVAL)) {
            return;
        }
        this.maneuverClearance = GroundObstacleRadar.scan(this.controller);
        this.lastRadarTick = this.mob.tickCount;
        if (VehicleAiLogger.isDebugEnabled() && (force || this.mob.tickCount % 10 == 0)) {
            VehicleAiLogger.debug(this.mob,
                    "Drive maneuver radar vehicle={} yaw={} localBounds={} clearance={} bbox={} velocity={}",
                    this.controller.entity().getId(),
                    this.controller.yaw(),
                    this.controller.localCollisionBounds(),
                    this.maneuverClearance,
                    this.controller.boundingBox(),
                    this.controller.velocity());
        }
    }

    private void updateManeuverProgress() {
        if (this.maneuverStepTicks == 0 || this.maneuverStepTicks % MANEUVER_PROBE_INTERVAL != 0) {
            return;
        }
        Vec3 position = this.controller.position();
        if (this.maneuverStep == GroundManeuverStep.BACK_STRAIGHT || this.maneuverStep == GroundManeuverStep.BACK_TURN) {
            // Count each reverse segment in its live heading, including turns within the same reverse leg.
            this.maneuverReverseDistance += Math.max(0.0D,
                    this.maneuverLastPosition.subtract(position).dot(this.forwardDirection()));
        }
        double movedSqr = this.horizontalDistanceSqr(position, this.maneuverLastPosition);
        double yawDelta = Math.abs(net.minecraft.util.Mth.wrapDegrees(this.controller.yaw() - this.maneuverLastYaw));
        boolean progressByMove = movedSqr >= MANEUVER_PROGRESS_DISTANCE_SQR
                || (movedSqr >= 0.0004D && this.longitudinalSpeed() < -0.02D);
        boolean progressByYaw = yawDelta >= MANEUVER_PROGRESS_YAW_DEGREES;
        VehicleAiLogger.debug(this.mob,
                "Drive maneuver progress sample step={} movedSqr={} yawDelta={} progressByMove={} progressByYaw={} noProgressBefore={} position={} lastPosition={} yaw={} lastYaw={}",
                this.maneuverStep,
                movedSqr,
                yawDelta,
                progressByMove,
                progressByYaw,
                this.maneuverNoProgressTicks,
                position,
                this.maneuverLastPosition,
                this.controller.yaw(),
                this.maneuverLastYaw);
        if (progressByMove || progressByYaw) {
            this.maneuverNoProgressTicks = 0;
        } else {
            this.maneuverNoProgressTicks += MANEUVER_PROBE_INTERVAL;
        }
        this.maneuverLastPosition = position;
        this.maneuverLastYaw = this.controller.yaw();
    }

    private void resetManeuver(Vec3 position) {
        this.reverseTurnWaypoint = null;
        this.maneuverTicks = 0;
        this.maneuverMultiPointTurn = false;
        this.maneuverStepTicks = 0;
        this.maneuverNoProgressTicks = 0;
        this.maneuverTurnDirection = 0;
        this.lastRadarTick = this.mob.tickCount - MANEUVER_PROBE_INTERVAL;
        this.maneuverStep = GroundManeuverStep.BRAKE;
        this.maneuverClearance = GroundClearance.open(GroundObstacleRadar.RANGE);
        this.maneuverLastPosition = position;
        this.maneuverLastYaw = this.controller == null ? 0.0F : this.controller.yaw();
        this.maneuverReverseStarted = false;
        this.maneuverReverseDistance = 0.0D;
    }

    static GroundManeuverStep selectInitialManeuverStep(GroundClearance clearance, boolean shouldBrake) {
        if (shouldBrake) {
            return GroundManeuverStep.BRAKE;
        }
        if (clearance.front() < MANEUVER_EXIT_CLEARANCE && clearance.prefersBack()) {
            return GroundManeuverStep.BACK_STRAIGHT;
        }
        return GroundManeuverStep.SWING_FORWARD;
    }

    static int selectManeuverTurnDirection(GroundClearance clearance, double yawDiffDegrees, int previousTurnDirection) {
        int fallback = previousTurnDirection;
        if (fallback == 0) {
            fallback = yawDiffDegrees < 0.0D ? -1 : 1;
        }
        return clearance.squeezedOpenSide(clearance.openSideDirection(fallback), MANEUVER_SIDE_SQUEEZE_DISTANCE);
    }

    static GroundManeuverStep preferredMovementStep(GroundClearance clearance, boolean useTurnWhenBacking) {
        if (clearance.prefersBack()) {
            return useTurnWhenBacking ? GroundManeuverStep.BACK_TURN : GroundManeuverStep.BACK_STRAIGHT;
        }
        return GroundManeuverStep.SWING_FORWARD;
    }

    static GroundManeuverStep nextBackStraightStep(GroundClearance clearance, int stepTicks, int noProgressTicks) {
        if (stepTicks >= MANEUVER_STEP_MIN_TICKS && clearance.backBlocked(MANEUVER_BLOCKED_DISTANCE)
                && clearance.front() >= MANEUVER_EXIT_CLEARANCE) {
            return GroundManeuverStep.SWING_FORWARD;
        }
        if (stepTicks >= MANEUVER_STEP_MIN_TICKS && noProgressTicks >= MANEUVER_PROBE_INTERVAL) {
            return GroundManeuverStep.BACK_TURN;
        }
        return stepTicks >= 10 ? GroundManeuverStep.BACK_TURN : GroundManeuverStep.BACK_STRAIGHT;
    }

    static GroundManeuverStep nextBackTurnStep(GroundClearance clearance, int stepTicks, int noProgressTicks) {
        if (stepTicks >= MANEUVER_STEP_MIN_TICKS && clearance.backBlocked(MANEUVER_BLOCKED_DISTANCE)
                && clearance.front() >= MANEUVER_EXIT_CLEARANCE) {
            return GroundManeuverStep.SWING_FORWARD;
        }
        if (stepTicks >= 16 && noProgressTicks >= 10) {
            return GroundManeuverStep.BACK_TURN;
        }
        return stepTicks >= 18 && noProgressTicks == 0 ? GroundManeuverStep.BACK_STRAIGHT : GroundManeuverStep.BACK_TURN;
    }

    static int selectManeuverForwardTurn(int previousTurnDirection, double yawDiffDegrees, GroundClearance clearance) {
        if (clearance.sideSqueezed(MANEUVER_SIDE_SQUEEZE_DISTANCE)) {
            return clearance.squeezedOpenSide(previousTurnDirection, MANEUVER_SIDE_SQUEEZE_DISTANCE);
        }
        if (Math.abs(yawDiffDegrees) < 8.0D) {
            return 0;
        }
        if (Math.abs(yawDiffDegrees) < 35.0D && previousTurnDirection != 0) {
            return previousTurnDirection;
        }
        return yawDiffDegrees < 0.0D ? -1 : 1;
    }

    static boolean shouldAbortManeuverPulse(VehicleInput input, GroundClearance clearance, int stepTicks) {
        if (!input.forward() && !input.back()) {
            return false;
        }
        if (stepTicks < 2) {
            return false;
        }
        return (input.forward() && clearance.frontBlocked(MANEUVER_BLOCKED_DISTANCE))
                || (input.back() && clearance.backBlocked(MANEUVER_BLOCKED_DISTANCE));
    }

    private int forwardPulseTicks() {
        if (this.maneuverClearance.frontBlocked(MANEUVER_BLOCKED_DISTANCE)) {
            return 4;
        }
        if (this.maneuverClearance.front() < 2.25D) {
            return 7;
        }
        return 12;
    }

    private boolean shouldRecoverFromBlockedPath(DriveRequest request, boolean directRamTarget) {
        if (!this.canUseManeuver(request)) {
            return false;
        }
        if (request.intent() == DriveIntent.RAM_TARGET && request.targetEntity().isPresent()) {
            Entity target = request.targetEntity().get();
            if (!target.isAlive() || directRamTarget) {
                return false;
            }
        }
        return this.path == null || this.path.isDone() || this.path.getNodeCount() <= 1;
    }

    private boolean shouldRecoverFromExhaustedPath(DriveRequest request, boolean directRamTarget) {
        if (!this.canUseManeuver(request)) {
            return false;
        }
        if (request.intent() == DriveIntent.RAM_TARGET && request.targetEntity().isPresent()) {
            Entity target = request.targetEntity().get();
            return target.isAlive() && !directRamTarget;
        }
        return true;
    }

    static record PathPlanKey(DriveIntent intent, int targetEntityId, BlockPos targetBlock,
                              Vec3 targetPosition, VehicleDimensions dimensions, int providedPathIdentity) {
        boolean routeIdentityChanged(PathPlanKey other) {
            if (other == null) {
                return true;
            }
            if (this.intent != other.intent
                    || this.targetEntityId != other.targetEntityId
                    || Mth.ceil(this.dimensions.width()) != Mth.ceil(other.dimensions.width())
                    || Mth.ceil(this.dimensions.length()) != Mth.ceil(other.dimensions.length())
                    || Mth.ceil(this.dimensions.height()) != Mth.ceil(other.dimensions.height())
                    || this.providedPathIdentity != other.providedPathIdentity) {
                return true;
            }
            return this.targetEntityId < 0 && !this.targetBlock.equals(other.targetBlock);
        }

        boolean targetMovedSignificantly(PathPlanKey other) {
            if (other == null) {
                return true;
            }
            double dx = this.targetPosition.x - other.targetPosition.x;
            double dz = this.targetPosition.z - other.targetPosition.z;
            return dx * dx + dz * dz >= PATH_TARGET_REPLAN_DISTANCE_SQR;
        }
    }

    private record PathTarget(BlockPos targetBlock, int targetEntityId, Vec3 targetPosition, String source) {
    }

    private record PathSegment(Vec3 from, Vec3 to, VehicleDimensions dimensions) {
    }

    private void followRoute(DriveRequest request) {
        boolean directRamTarget = request.intent() == DriveIntent.RAM_TARGET
                && request.targetEntity().filter(Entity::isAlive).filter(this::shouldUseDirectRam).isPresent();
        if (this.replanTicks > 0) {
            this.replanTicks--;
        }
        if (this.shouldReplanPath(request)) {
            if (!directRamTarget || this.state == State.PLAN_PATH) {
                this.state = State.PLAN_PATH;
                this.planPath(request);
            } else {
                this.replanTicks = CommonConfig.VEHICLE_PATH_REPLAN_INTERVAL.get();
            }
        } else if (this.replanTicks <= 0) {
            this.replanTicks = CommonConfig.VEHICLE_PATH_REPLAN_INTERVAL.get();
            VehicleAiLogger.debugEvery(this.mob, 40,
                    "Reusing vehicle path intent={} targetEntity={} targetPos={} key={} replanTicks={}",
                    request.intent(),
                    request.targetEntity().map(Entity::getId).orElse(-1),
                    request.targetPosition().orElse(null),
                    this.lastPathPlanKey,
                    this.replanTicks);
        }
        if (this.shouldRecoverFromBlockedPath(request, directRamTarget)) {
            this.beginManeuver(request, "path unavailable");
            this.tickManeuver(request);
            return;
        }

        this.state = State.FOLLOW_PATH;
        Vec3 waypoint = this.nextWaypoint(request, directRamTarget);
        if (this.state == State.MANEUVER) {
            this.tickManeuver(request);
            return;
        }
        if (waypoint == null) {
            VehicleAiLogger.debugEvery(this.mob, 40, "Drive stopping: no waypoint for request {}", request.intent());
            this.submitInput(VehicleInput.NONE);
            return;
        }
        double remainingDistanceSqr = this.horizontalDistanceSqr(controller.position(),
                request.intent() == DriveIntent.MOVE_TO
                        ? this.navigationTargetPosition(request).orElse(waypoint) : waypoint);
        if (this.reverseTurnWaypoint != null
                && this.horizontalDistanceSqr(controller.position(), this.reverseTurnWaypoint) <= 1.0D) {
            this.reverseTurnWaypoint = null;
        }
        boolean directRam = this.reverseTurnWaypoint == null && request.intent() == DriveIntent.RAM_TARGET
                && request.targetEntity().map(Entity::position).filter(waypoint::equals).isPresent();
        Vec3 steeringWaypoint = this.reverseTurnWaypoint == null ? waypoint : this.reverseTurnWaypoint;
        boolean stopAtWaypoint = this.reverseTurnWaypoint == null && this.shouldStopAtWaypoint(request, waypoint);
        GroundSteering.SteeringResult steering = directRam
                ? GroundSteering.steerRamToward(controller, waypoint, this.turnDirection)
                : GroundSteering.steerToward(controller, steeringWaypoint, remainingDistanceSqr, stopAtWaypoint,
                        request.preferStableFire(), this.turnDirection, this.reverseTurnWaypoint != null);
        VehicleInput input = steering.input();
        this.turnDirection = steering.turnDirection();
        if (!directRam && input.back() && this.reverseTurnWaypoint == null && Math.abs(this.yawDiffTo(waypoint)) < 155.0D) {
            this.reverseTurnWaypoint = waypoint;
        } else if (this.reverseTurnWaypoint != null && input.forward() && this.longitudinalSpeed() > 0.05D) {
            this.reverseTurnWaypoint = null;
        }
        if (this.tickBlockedReverseTurn(request, input)) return;
        VehicleAiLogger.debugEvery(this.mob, 20, "Drive following waypoint {} with input {}", steeringWaypoint, input);
        this.submitInput(input);
    }

    private enum State {
        IDLE,
        PLAN_PATH,
        FOLLOW_PATH,
        MANEUVER
    }
}
