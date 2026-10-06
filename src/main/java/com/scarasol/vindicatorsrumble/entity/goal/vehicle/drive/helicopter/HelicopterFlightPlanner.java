package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.helicopter;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.util.VehicleAiLogger;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public final class HelicopterFlightPlanner {
    static final double ALTITUDE_SAMPLE = HelicopterFlightControl.ALTITUDE_SAMPLE;
    static final double SAFE_ALTITUDE = HelicopterFlightControl.SAFE_ALTITUDE;
    static final double VERTICAL_TOLERANCE = 1.5D;
    static final double INNER_COMBAT_TOLERANCE = 8.0D;
    static final double OUTER_COMBAT_TOLERANCE = 4.0D;
    static final double APPROACH_CRUISE_SPEED = 0.68D;

    private static final double VISIBILITY_RECOVERY_CRUISE_SPEED = 0.78D;
    private static final double DIVE_PASS_YAW_TOLERANCE = 7.0D;
    private static final double DIVE_PASS_START_STOPPING_DISTANCE = 1.0D;
    private static final double DIVE_PASS_CLEARANCE_LOOKAHEAD_TICKS = 8.0D;
    private static final double DIVE_PASS_RANGE_MARGIN = 3.0D;
    private static final double TARGET_HOVER_CLEARANCE = 6.0D;
    private static final double MOVEMENT_MIN_REACHED_DISTANCE = 3.0D;
    private static final double MOVEMENT_MAX_REACHED_DISTANCE = 6.0D;
    static final double MOVEMENT_PASS_DISTANCE = 8.0D;
    private static final double MOVEMENT_PASS_SPEED = 0.12D;
    private static final double MOVEMENT_LOOKAHEAD_TICKS = 8.0D;
    private static final double[] PASS_EXIT_ANGLES = {0.0D, -7.0D, 7.0D};
    private static final double[] RETREAT_ANGLES = {0.0D, -45.0D, 45.0D, -90.0D, 90.0D};
    private static final double[] EVASION_ANGLES = {70.0D, -70.0D, 90.0D, -90.0D, 110.0D, -110.0D};

    private final HelicopterEnvironmentProbe environmentProbe = new HelicopterEnvironmentProbe();
    private final HelicopterVisibilityRecovery visibilityRecovery = new HelicopterVisibilityRecovery();
    private int retreatTargetId = -1;
    @Nullable
    private Vec3 retreatDirection;
    private boolean retreatCrossedTarget;
    private boolean retreatPullingOut;
    private boolean retreatContinuesAfterPullOut;
    private boolean retreatCommitted;
    private long lastCombatPlanTick = Long.MIN_VALUE;
    private long lastRetreatSearchTick = Long.MIN_VALUE;
    private double hoverY = Double.NaN;
    private int hoverTargetId = -1;
    private int evasionSourceId = -1;
    private int evasionTargetId = -1;
    private boolean evasionBraking;
    @Nullable
    private Vec3 evasionDirection;
    @Nullable
    private Vec3 evasionPosition;
    @Nullable
    private Vec3 evasionSeparation;
    @Nullable
    private Vec3 evasionSourceFacing;
    @Nullable
    private Vec3 previousEvasionDirection;
    private double evasionRemaining;
    private long lastEvasionTick = Long.MIN_VALUE;

    void reset() {
        this.hoverY = Double.NaN;
        this.hoverTargetId = -1;
        this.clearEvasion();
        this.clearRetreatRoute();
        this.lastCombatPlanTick = Long.MIN_VALUE;
        this.lastRetreatSearchTick = Long.MIN_VALUE;
        this.visibilityRecovery.reset();
        this.environmentProbe.reset();
    }

    Plan resolvePlan(VehicleController controller, @Nullable Entity target, AircraftState state,
                     double hoverHeight, double followRange,
                     Predicate<HelicopterEnvironment> visibilityRecoveryBlocked,
                     BiFunction<HelicopterEnvironment, Vec3, Plan> planSelector) {
        int targetId = target == null ? -1 : target.getId();
        if (this.hoverTargetId != targetId || this.visibilityRecovery.recovering()) {
            this.hoverTargetId = targetId;
            this.hoverY = state.position().y;
        }
        Plan sensingPlan = planSelector.apply(HelicopterEnvironment.NONE, null);
        Vec3 recoveryWaypoint = this.visibilityRecovery.waypoint();
        HelicopterEnvironment environment = this.environmentProbe.sample(
                controller, target, state, sensingPlan, hoverHeight, recoveryWaypoint);
        boolean needsVisibilityRecovery = target != null
                && (!environment.hasLineOfSight() || environment.frontBlocked());
        if (needsVisibilityRecovery) this.clearEvasion();
        if (target == null || visibilityRecoveryBlocked.test(environment)) {
            this.visibilityRecovery.reset();
            recoveryWaypoint = null;
        } else if (needsVisibilityRecovery) {
            if (recoveryWaypoint != null
                    && HelicopterFlightControl.lacksObstacleStoppingRoom(
                    environment, controller.velocity().multiply(1.0D, 0.0D, 1.0D).length())) {
                this.visibilityRecovery.invalidateCurrentRoute(state);
            }
            recoveryWaypoint = this.visibilityRecovery.update(
                    controller, target, state, environment.hasLineOfSight(),
                    environment.frontBlocked(), followRange);
        } else {
            this.visibilityRecovery.reset();
            recoveryWaypoint = null;
        }

        Plan plan = planSelector.apply(environment, recoveryWaypoint);
        for (int i = 0; i < 2; i++) {
            HelicopterEnvironment aligned = this.environmentProbe.realignMovement(
                    controller, state, plan, environment);
            if (aligned == environment) {
                break;
            }
            environment = aligned;
            plan = planSelector.apply(environment, recoveryWaypoint);
        }
        this.hoverY = this.visibilityRecovery.recovering() ? state.position().y : plan.desiredPosition().y;
        return plan.withEnvironment(environment, target == null || target.getRootVehicle().onGround()
                ? HelicopterFlightControl.DIVE_PASS_PITCH_BONUS : 0.0D);
    }

    double desiredY(VehicleController controller, AircraftState state, @Nullable Entity target,
                    double hoverHeight, HelicopterEnvironment environment) {
        HelicopterEnvironment env = environment == null ? HelicopterEnvironment.NONE : environment;
        Vec3 position = state.position();
        double groundY = position.y - Math.min(state.altitude(), ALTITUDE_SAMPLE);
        double minHoverY = groundY + Math.max(SAFE_ALTITUDE, hoverHeight - 2.0D);
        if (target != null) {
            minHoverY = Math.max(minHoverY, targetFloorY(controller, target));
        }
        minHoverY = terrainAwareY(minHoverY, env);
        double maxHoverY = Math.max(groundY + hoverHeight + 10.0D,
                minHoverY + TARGET_HOVER_CLEARANCE * 2.0D);
        double referenceY = Double.isNaN(this.hoverY) ? position.y : this.hoverY;
        return referenceY < minHoverY || referenceY > maxHoverY
                ? clamp(referenceY, minHoverY + VERTICAL_TOLERANCE, maxHoverY - VERTICAL_TOLERANCE)
                : referenceY;
    }

    static double recoveryFloorY(VehicleController controller, AircraftState state, Entity target) {
        Vec3 position = state.position();
        double groundY = position.y - Math.min(state.altitude(), ALTITUDE_SAMPLE);
        return Math.max(groundY + SAFE_ALTITUDE, targetFloorY(controller, target) + VERTICAL_TOLERANCE);
    }

    static boolean needsAltitudeCorrection(AircraftState state, double desiredY) {
        return state.altitude() < SAFE_ALTITUDE || desiredY - state.position().y > VERTICAL_TOLERANCE;
    }

    static Plan altitudeCorrectionPlan(AircraftState state, @Nullable Vec3 targetCenter, double desiredY) {
        Vec3 desiredPosition = new Vec3(state.position().x, desiredY, state.position().z);
        Vec3 aimPoint = targetCenter == null ? levelForwardAim(state, desiredY) : targetCenter;
        Phase phase = state.altitude() < SAFE_ALTITUDE ? Phase.TAKEOFF : Phase.COMBAT_HOVER;
        return plan(desiredPosition, aimPoint, 0.0D, true, targetCenter != null, phase);
    }

    static Plan idlePlan(AircraftState state, double desiredY) {
        Vec3 desiredPosition = new Vec3(state.position().x, desiredY, state.position().z);
        return plan(desiredPosition, levelForwardAim(state, desiredY), 0.0D, true, false, Phase.IDLE);
    }

    @Nullable
    Plan evasionPlan(VehicleController controller, AircraftState state, @Nullable Entity pressure,
                     boolean incoming, Entity target, Vec3 targetCenter, double desiredY, double combatDistance,
                     double followRange, HelicopterEnvironment environment) {
        if (environment == HelicopterEnvironment.NONE) return null;
        int targetId = target.getId();
        long gameTime = controller.level().getGameTime();
        double evasionDistance = Math.max(0.0D, followRange - DIVE_PASS_RANGE_MARGIN);
        if (this.evasionTargetId != targetId
                || this.lastEvasionTick != Long.MIN_VALUE
                && (gameTime < this.lastEvasionTick || gameTime > this.lastEvasionTick + 1L)) {
            this.clearEvasion();
        }
        this.evasionTargetId = targetId;
        boolean alreadyPlanned = this.lastEvasionTick == gameTime;
        this.lastEvasionTick = gameTime;
        if (this.evasionDirection == null && (pressure == null || alreadyPlanned)) {
            return null;
        }
        double stoppingDistance = Math.max(0.0D, controller.estimatedStabilizedHorizontalStoppingDistance());
        Entity opponent = target.getRootVehicle();
        Vec3 separation = state.position().subtract(opponent.getBoundingBox().getCenter())
                .multiply(1.0D, 0.0D, 1.0D);
        Vec3 velocity = state.velocity().multiply(1.0D, 0.0D, 1.0D);
        Vec3 opponentVelocity = opponent.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
        VehicleController opponentController = VehicleControllers.create(opponent).orElse(null);
        double opponentStoppingDistance = opponentController == null ? 0.0D
                : opponentController.estimatedStabilizedHorizontalStoppingDistance();
        Vec3 projectedSeparation = separation
                .add(velocity.normalize().scale(stoppingDistance))
                .subtract(opponentVelocity.normalize().scale(Math.max(0.0D, opponentStoppingDistance)));
        boolean rangeUnsafe = separation.dot(velocity.subtract(opponentVelocity)) >= 0.0D
                && Math.max(separation.length(), projectedSeparation.length()) >= evasionDistance;
        if (this.evasionDirection == null) {
            if (rangeUnsafe) return null;
            if (!incoming && this.evasionSourceId == pressure.getId() && this.evasionSeparation != null) {
                Vec3 oldSide = rotate(this.evasionSeparation.normalize(), 90.0D);
                double crossing = Math.abs(separation.subtract(this.evasionSeparation).dot(oldSide));
                double tracking = Math.abs(this.evasionSourceFacing.cross(
                        pressure.getViewVector(1.0F).multiply(1.0D, 0.0D, 1.0D).normalize()).y)
                        * this.evasionSeparation.length();
                // A persistent source must track a real crossing, or regain meaningful range room.
                if (stoppingDistance > DIVE_PASS_START_STOPPING_DISTANCE || Math.abs(state.roll()) > 5.0D
                        || !(crossing >= MOVEMENT_MIN_REACHED_DISTANCE && tracking >= MOVEMENT_MIN_REACHED_DISTANCE
                        || this.evasionSeparation.length() - separation.length() >= MOVEMENT_MAX_REACHED_DISTANCE)) {
                    return null;
                }
            }
            double budget = Math.min(combatDistance, evasionDistance)
                    * (0.25D + controller.level().random.nextDouble() * 0.1D);
            // Two maneuver-capable aircraft can both contribute to the relative crossing.
            if (!incoming && opponentController != null && opponentController.supportsFlightControl()
                    && opponentController.engineStarted()) budget *= 0.5D;
            Vec3 axis = horizontalUnit(incoming ? state.velocity().subtract(pressure.getDeltaMovement())
                    : state.position().subtract(pressure.position()), state);
            Vec3 side = rotate(axis, 90.0D);
            double opponentSideSpeed = opponentVelocity.dot(side);
            int preferredSide = Math.abs(opponentSideSpeed) > MOVEMENT_PASS_SPEED ? opponentSideSpeed > 0 ? -1 : 1
                    : ((controller.entity().getUUID().hashCode() ^ opponent.getUUID().hashCode()) & 1) == 0 ? -1 : 1;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (double angle : EVASION_ANGLES) {
                Vec3 direction = rotate(axis, angle);
                Vec3 destination = state.position().add(direction.scale(budget));
                destination = new Vec3(destination.x, desiredY, destination.z);
                if (this.previousEvasionDirection != null
                        && direction.dot(this.previousEvasionDirection) > 0.0D) continue;
                double endDistance = projectedSeparation.add(direction.scale(budget)).length();
                if (budget <= stoppingDistance * Math.max(0.0D, velocity.normalize().dot(direction))
                        || endDistance >= evasionDistance
                        || !this.visibilityRecovery.directDestinationClear(
                        controller, targetCenter, state.position(), destination)) continue;
                double transverse = direction.dot(side) * VISIBILITY_RECOVERY_CRUISE_SPEED - opponentSideSpeed;
                double score = Math.abs(transverse) + 0.25D * Math.signum(angle) * preferredSide
                        - Math.max(0.0D, endDistance - combatDistance - OUTER_COMBAT_TOLERANCE) / budget
                        + controller.level().random.nextDouble() * 0.08D;
                if (score > bestScore) {
                    bestScore = score;
                    this.evasionDirection = direction;
                }
            }
            if (this.evasionDirection != null) {
                this.evasionSourceId = pressure.getId();
                this.evasionRemaining = budget;
                this.evasionPosition = state.position();
                this.evasionSeparation = separation;
                this.evasionSourceFacing = pressure.getViewVector(1.0F).multiply(1.0D, 0.0D, 1.0D).normalize();
                this.clearRetreatRoute();
                VehicleAiLogger.debug(controller.entity(),
                        "Helicopter evasion started target={} source={} incoming={} direction={} budget={} limit={} score={} separation={}",
                        targetId, this.evasionSourceId, incoming, this.evasionDirection, budget, evasionDistance,
                        bestScore, separation);
            }
        }
        if (this.evasionDirection == null) return null;
        if (pressure != null) this.evasionSourceId = pressure.getId();
        // Repeated same-tick selections consume zero extra distance and never resample the segment.
        this.evasionRemaining -= Math.max(0.0D, state.position().subtract(this.evasionPosition)
                .dot(this.evasionDirection));
        this.evasionPosition = state.position();
        double directionalStopping = stoppingDistance * Math.max(0.0D, velocity.normalize().dot(this.evasionDirection));
        Vec3 destination = state.position().add(this.evasionDirection.scale(
                Math.min(this.evasionRemaining, Math.max(MOVEMENT_PASS_DISTANCE,
                        directionalStopping + MOVEMENT_MAX_REACHED_DISTANCE))));
        destination = new Vec3(destination.x, desiredY, destination.z);
        double projectedDistance = projectedSeparation.length();
        String endReason = this.evasionRemaining <= directionalStopping ? "travel_budget"
                : rangeUnsafe ? "range"
                : !this.visibilityRecovery.directDestinationClear(
                controller, targetCenter, state.position(), destination) ? "corridor" : null;
        if (!this.evasionBraking && endReason != null) {
            this.evasionBraking = true;
            VehicleAiLogger.debug(controller.entity(),
                    "Helicopter evasive braking target={} reason={} remaining={} distance={} projectedDistance={} limit={} stoppingDistance={} opponentStoppingDistance={} directionalStopping={}",
                    targetId, endReason, this.evasionRemaining, separation.length(), projectedDistance, evasionDistance,
                    stoppingDistance, opponentStoppingDistance, directionalStopping);
        }
        if (this.evasionBraking) {
            if (stoppingDistance > DIVE_PASS_START_STOPPING_DISTANCE || Math.abs(state.roll()) > 5.0D) {
                return divePassAlignmentPlan(state, targetCenter, desiredY);
            }
            this.evasionBraking = false;
            this.previousEvasionDirection = this.evasionDirection;
            this.evasionDirection = null;
            this.evasionPosition = null;
            VehicleAiLogger.debug(controller.entity(), "Helicopter evasion finished target={} source={} remaining={}",
                    targetId, this.evasionSourceId, this.evasionRemaining);
            return null;
        }
        return plan(destination, targetCenter, VISIBILITY_RECOVERY_CRUISE_SPEED, false,
                true, Phase.EVASIVE_PASS);
    }

    private void clearEvasion() {
        this.evasionSourceId = -1;
        this.evasionTargetId = -1;
        this.evasionDirection = null;
        this.evasionBraking = false;
        this.evasionPosition = null;
        this.evasionSeparation = null;
        this.evasionSourceFacing = null;
        this.previousEvasionDirection = null;
        this.evasionRemaining = 0.0D;
        this.lastEvasionTick = Long.MIN_VALUE;
    }

    Plan navigationPlan(VehicleController controller, AircraftState state, Vec3 destination,
                        double hoverHeight, double arrivalDistance, HelicopterEnvironment environment) {
        if (!controller.level().hasChunkAt(BlockPos.containing(destination))) {
            return idlePlan(state, this.desiredY(controller, state, null, hoverHeight, environment));
        }
        double groundY = controller.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(destination.x), Mth.floor(destination.z));
        double desiredY = Math.max(groundY + hoverHeight,
                this.desiredY(controller, state, null, hoverHeight, environment));
        double stoppingDistance = controller.estimatedStabilizedHorizontalStoppingDistance();
        boolean obstruction = environment.frontClearance() < HelicopterEnvironmentProbe.RAY_RANGE;
        boolean blocked = state.altitude() < SAFE_ALTITUDE || environment.frontBlocked()
                || obstruction && environment.frontClearance() <= stoppingDistance
                + Math.max(controller.boundingBox().getXsize(), controller.boundingBox().getZsize());
        if (blocked) {
            desiredY = Math.max(desiredY, state.position().y + VERTICAL_TOLERANCE * 2.0D);
            if (obstruction && Double.isFinite(this.environmentProbe.frontObstacleY())) {
                desiredY = Math.max(desiredY, this.environmentProbe.frontObstacleY() + SAFE_ALTITUDE);
            }
        }
        Vec3 waypoint = new Vec3(destination.x, desiredY, destination.z);
        boolean arriving = horizontalDistance(state.position(), destination) <= arrivalDistance + stoppingDistance;
        return plan(waypoint, arriving ? levelForwardAim(state, desiredY) : waypoint,
                arriving || blocked ? 0.0D : APPROACH_CRUISE_SPEED, true, true,
                blocked ? Phase.PULL_OUT : arriving ? Phase.COMBAT_HOVER : Phase.APPROACH);
    }

    static Plan pullOutPlan(AircraftState state, Vec3 facingPoint, double hoverHeight) {
        Vec3 position = state.position();
        double groundY = position.y - Math.min(state.altitude(), ALTITUDE_SAMPLE);
        Vec3 desiredPosition = new Vec3(position.x, groundY + divePassPullOutTargetAltitude(hoverHeight), position.z);
        return plan(desiredPosition, facingPoint, 0.0D, true, true, Phase.PULL_OUT);
    }

    static Plan visibilityRecoveryPlan(AircraftState state, Vec3 targetCenter, double desiredY,
                                       @Nullable Vec3 recoveryWaypoint) {
        Vec3 position = state.position();
        if (recoveryWaypoint == null) {
            Vec3 holdPosition = new Vec3(position.x, desiredY, position.z);
            return plan(holdPosition, levelForwardAim(state, desiredY), 0.0D, true,
                    true, Phase.COMBAT_HOVER);
        }

        return plan(recoveryWaypoint, recoveryWaypoint, VISIBILITY_RECOVERY_CRUISE_SPEED,
                false, true, Phase.APPROACH);
    }

    Plan combatPositionPlan(VehicleController controller, int targetId, AircraftState state,
                            Vec3 targetCenter, double desiredY, double hoverHeight,
                            double combatDistance, double followRange,
                            HelicopterEnvironment environment) {
        if (environment == HelicopterEnvironment.NONE) {
            return standardCombatPositionPlan(state, targetCenter, desiredY, combatDistance);
        }

        long gameTime = controller.level().getGameTime();
        if ((this.lastCombatPlanTick != Long.MIN_VALUE
                && (gameTime < this.lastCombatPlanTick || gameTime > this.lastCombatPlanTick + 1L))
                || (this.retreatTargetId >= 0 && this.retreatTargetId != targetId)
                || this.visibilityRecovery.recovering()) {
            this.clearRetreatRoute();
        }
        this.lastCombatPlanTick = gameTime;

        if (this.visibilityRecovery.recovering()) {
            return standardCombatPositionPlan(state, targetCenter, desiredY, combatDistance);
        }

        double horizontalDistance = horizontalDistance(state.position(), targetCenter);
        boolean retreating = horizontalDistance < combatDistance - INNER_COMBAT_TOLERANCE;
        double exitDistance = Math.min(combatDistance, Math.max(0.0D, followRange - DIVE_PASS_RANGE_MARGIN));

        if (this.retreatDirection != null) {
            this.updateRetreatCrossing(state, targetCenter);
            if (!this.retreatCommitted && this.retreatCrossedTarget && !this.retreatContinuesAfterPullOut) {
                this.retreatPullingOut = true;
                this.retreatContinuesAfterPullOut = true;
            }
            if (!this.retreatCrossedTarget && horizontalDistance > combatDistance + OUTER_COMBAT_TOLERANCE) {
                this.clearRetreatRoute();
            } else {
                double horizontalSpeed = Math.hypot(state.velocity().x, state.velocity().z);
                boolean safetyPullOut = needsPassPullOut(state);

                if (this.retreatPullingOut) {
                    if (safetyPullOut || horizontalSpeed > HelicopterFlightControl.STABILIZED_HORIZONTAL_SPEED) {
                        return pullOutPlan(state, targetCenter, hoverHeight);
                    }
                    this.retreatPullingOut = false;
                    if (!this.retreatContinuesAfterPullOut) {
                        this.clearRetreatRoute();
                        return standardCombatPositionPlan(state, targetCenter, desiredY, combatDistance);
                    }
                }

                if (this.retreatContinuesAfterPullOut) {
                    if (!retreating) {
                        this.clearRetreatRoute();
                        return standardCombatPositionPlan(state, targetCenter, desiredY, combatDistance);
                    }
                    return retreatContinuationPlan(state, targetCenter, desiredY, combatDistance);
                }

                Vec3 exitPoint = divePassExitPoint(
                        targetCenter, this.retreatDirection, desiredY, exitDistance);
                if (safetyPullOut || this.retreatCrossedTarget
                        && horizontalDistance + Math.max(0.0D,
                        controller.estimatedStabilizedHorizontalStoppingDistance()) >= exitDistance) {
                    this.retreatPullingOut = true;
                    this.retreatContinuesAfterPullOut = safetyPullOut && !this.retreatCrossedTarget;
                    return pullOutPlan(state, targetCenter, hoverHeight);
                }
                if (!this.retreatCommitted && !passReadyToCommit(controller, state, exitPoint)) {
                    return divePassAlignmentPlan(state, exitPoint, desiredY);
                }
                this.retreatCommitted = true;
                return divePassPlan(exitPoint, this.retreatCrossedTarget ? exitPoint : targetCenter);
            }
        }

        if (retreating && exitDistance > 1.0D && this.lastRetreatSearchTick != gameTime) {
            this.lastRetreatSearchTick = gameTime;
            Vec3 direction = this.findRetreatDirection(
                    controller, state, targetCenter, desiredY, exitDistance);
            if (direction != null) {
                this.retreatTargetId = targetId;
                this.retreatDirection = direction;
                this.retreatCrossedTarget = false;
                this.retreatPullingOut = false;
                this.retreatCommitted = false;
                Vec3 exitPoint = divePassExitPoint(targetCenter, direction, desiredY, exitDistance);
                if (!passReadyToCommit(controller, state, exitPoint)) {
                    return divePassAlignmentPlan(state, exitPoint, desiredY);
                }
                this.retreatCommitted = true;
                return divePassPlan(exitPoint, targetCenter);
            }
        }

        if (retreating) {
            return this.safeRetreatPlan(controller, state, targetCenter, desiredY, combatDistance);
        }

        return standardCombatPositionPlan(state, targetCenter, desiredY, combatDistance);
    }

    private static Plan divePassAlignmentPlan(AircraftState state, Vec3 facingPoint, double desiredY) {
        Vec3 desiredPosition = new Vec3(state.position().x, desiredY, state.position().z);
        return plan(desiredPosition, facingPoint, 0.0D, true, true, Phase.COMBAT_HOVER);
    }

    static Plan fireHoldPlan(AircraftState state, Vec3 targetCenter, double desiredY) {
        Vec3 desiredPosition = new Vec3(state.position().x, desiredY, state.position().z);
        return plan(desiredPosition, targetCenter, 0.0D, true, true, Phase.FIRE_HOLD);
    }

    static Plan divePassPlan(Vec3 exitPoint, Vec3 aimPoint) {
        return plan(exitPoint, aimPoint, 1.0D, false, true, Phase.DIVE_PASS);
    }

    static Plan retreatContinuationPlan(AircraftState state, Vec3 targetCenter,
                                        double desiredY, double combatDistance) {
        return standardCombatPositionPlan(state, targetCenter, desiredY, combatDistance);
    }

    static Plan attackRunPlan(Vec3 targetCenter, double desiredY) {
        Vec3 desiredPosition = new Vec3(targetCenter.x, desiredY, targetCenter.z);
        return plan(desiredPosition, targetCenter, 1.0D, false, true, Phase.ATTACK_RUN);
    }

    static Vec3 divePassExitPoint(Vec3 targetCenter, Vec3 exitDirection,
                                  double desiredY, double exitDistance) {
        return new Vec3(
                targetCenter.x + exitDirection.x * exitDistance,
                desiredY,
                targetCenter.z + exitDirection.z * exitDistance);
    }

    private void updateRetreatCrossing(AircraftState state, Vec3 targetCenter) {
        if (!this.retreatCrossedTarget) {
            Vec3 offset = state.position().subtract(targetCenter);
            this.retreatCrossedTarget = offset.x * this.retreatDirection.x
                    + offset.z * this.retreatDirection.z >= 0.0D;
        }
    }

    @Nullable
    private Vec3 findRetreatDirection(VehicleController controller, AircraftState state,
                                      Vec3 targetCenter, double desiredY, double exitDistance) {
        Vec3 direct = horizontalUnit(targetCenter.subtract(state.position()), state);
        for (double angle : PASS_EXIT_ANGLES) {
            Vec3 direction = angle == 0.0D ? direct : rotate(direct, angle);
            Vec3 exit = divePassExitPoint(targetCenter, direction, desiredY, exitDistance);
            if (this.visibilityRecovery.directDestinationClear(
                    controller, targetCenter, state.position(), exit)) {
                return direction;
            }
        }
        return null;
    }

    private Plan safeRetreatPlan(VehicleController controller, AircraftState state,
                                 Vec3 targetCenter, double desiredY, double combatDistance) {
        Vec3 direct = horizontalUnit(state.position().subtract(targetCenter), state);
        for (double angle : RETREAT_ANGLES) {
            Vec3 direction = angle == 0.0D ? direct : rotate(direct, angle);
            Vec3 destination = divePassExitPoint(targetCenter, direction, desiredY, combatDistance);
            if (this.visibilityRecovery.directDestinationClear(
                    controller, targetCenter, state.position(), destination)) {
                return plan(destination, targetCenter, APPROACH_CRUISE_SPEED, true,
                        true, Phase.APPROACH);
            }
        }
        return divePassAlignmentPlan(state, targetCenter, desiredY);
    }

    private void clearRetreatRoute() {
        this.retreatTargetId = -1;
        this.retreatDirection = null;
        this.retreatCrossedTarget = false;
        this.retreatPullingOut = false;
        this.retreatContinuesAfterPullOut = false;
        this.retreatCommitted = false;
    }

    private static Plan plan(Vec3 destination, Vec3 facingPoint, double cruiseSpeed, boolean stopAtDestination,
                             boolean hasTarget, Phase phase) {
        return new Plan(destination, facingPoint, cruiseSpeed, stopAtDestination,
                hasTarget, phase);
    }

    static boolean needsPassPullOut(AircraftState state) {
        double projectedClearance = state.altitude()
                + Math.min(0.0D, state.velocity().y) * DIVE_PASS_CLEARANCE_LOOKAHEAD_TICKS;
        return projectedClearance < SAFE_ALTITUDE;
    }

    static boolean reachedOrPassed(AircraftState state, Vec3 destination, boolean threeDimensional) {
        Vec3 delta = destination.subtract(state.position());
        Vec3 velocity = state.velocity();
        double distance = threeDimensional ? delta.length() : Math.hypot(delta.x, delta.z);
        double speed = threeDimensional ? velocity.length() : Math.hypot(velocity.x, velocity.z);
        double progress = threeDimensional
                ? velocity.dot(delta)
                : velocity.x * delta.x + velocity.z * delta.z;
        double reachedDistance = Math.min(
                MOVEMENT_MAX_REACHED_DISTANCE,
                Math.max(MOVEMENT_MIN_REACHED_DISTANCE, speed * MOVEMENT_LOOKAHEAD_TICKS));
        return distance <= reachedDistance
                || speed > MOVEMENT_PASS_SPEED
                && distance <= MOVEMENT_PASS_DISTANCE
                && progress <= 0.0D;
    }

    static boolean fixedWeaponAttackYawAligned(AircraftState state, Vec3 targetCenter) {
        return passYawAligned(state, targetCenter);
    }

    private static boolean passYawAligned(AircraftState state, Vec3 targetCenter) {
        return Math.abs(yawDiffTo(state, targetCenter)) <= DIVE_PASS_YAW_TOLERANCE;
    }

    private static boolean passReadyToCommit(VehicleController controller, AircraftState state, Vec3 facingPoint) {
        return passYawAligned(state, facingPoint)
                && Math.max(0.0D, controller.estimatedHorizontalStoppingDistance())
                <= DIVE_PASS_START_STOPPING_DISTANCE;
    }

    private static Plan standardCombatPositionPlan(AircraftState state, Vec3 targetCenter,
                                                   double desiredY, double combatDistance) {
        double horizontalDistance = horizontalDistance(state.position(), targetCenter);
        boolean outsideCombatBand = horizontalDistance < combatDistance - INNER_COMBAT_TOLERANCE
                || horizontalDistance > combatDistance + OUTER_COMBAT_TOLERANCE;
        if (outsideCombatBand) {
            Vec3 away = horizontalUnit(state.position().subtract(targetCenter), state);
            Vec3 desiredPosition = new Vec3(
                    targetCenter.x + away.x * combatDistance,
                    desiredY,
                    targetCenter.z + away.z * combatDistance);
            return plan(desiredPosition, targetCenter, APPROACH_CRUISE_SPEED, true,
                    true, Phase.APPROACH);
        }
        Vec3 desiredPosition = new Vec3(state.position().x, desiredY, state.position().z);
        return plan(desiredPosition, targetCenter, 0.0D, true, true, Phase.COMBAT_HOVER);
    }

    private static double divePassPullOutTargetAltitude(double hoverHeight) {
        return Math.max(SAFE_ALTITUDE + 6.0D, hoverHeight + 4.0D);
    }

    private static Vec3 levelForwardAim(AircraftState state, double y) {
        Vec3 aimPoint = state.position().add(yawForward(state).scale(16.0D));
        return new Vec3(aimPoint.x, y, aimPoint.z);
    }

    private static double targetFloorY(VehicleController controller, Entity target) {
        Entity vehicle = target.getRootVehicle();
        double centerY = vehicle.getBoundingBox().getCenter().y;
        boolean airborne = !vehicle.onGround() && VehicleControllers.create(vehicle)
                .map(VehicleController::supportsFlightControl).orElse(false);
        // Air combat compares body centers; ground targets retain the LOS clearance margin.
        return airborne ? centerY - (controller.boundingBox().getCenter().y - controller.position().y)
                - TARGET_HOVER_CLEARANCE : centerY + TARGET_HOVER_CLEARANCE - VERTICAL_TOLERANCE;
    }

    private static double terrainAwareY(double desiredY, HelicopterEnvironment environment) {
        if (environment.hasTerrainPreferredY()) {
            desiredY = Math.max(desiredY, environment.terrainPreferredY());
        }
        return desiredY;
    }

    private static double yawDiffTo(AircraftState state, Vec3 point) {
        Vec3 position = state.position();
        double dx = point.x - position.x;
        double dz = point.z - position.z;
        if (dx * dx + dz * dz < 1.0E-6D) {
            return 0.0D;
        }
        float targetYaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90.0F;
        return Mth.wrapDegrees(targetYaw - state.yaw());
    }

    private static Vec3 horizontalUnit(Vec3 vector, AircraftState state) {
        double length = Math.sqrt(vector.x * vector.x + vector.z * vector.z);
        if (length < 1.0E-4D) {
            return yawForward(state);
        }
        return new Vec3(vector.x / length, 0.0D, vector.z / length);
    }

    private static Vec3 yawForward(AircraftState state) {
        double yawRadians = Math.toRadians(state.yaw());
        return new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
    }

    private static Vec3 rotate(Vec3 vector, double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return new Vec3(vector.x * cos - vector.z * sin, 0.0D,
                vector.x * sin + vector.z * cos);
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    record AircraftState(Vec3 position, Vec3 velocity, float yaw, float pitch, float roll,
                         boolean hoverMode, boolean engineStarted, float power, double altitude) {
    }

    record Plan(Vec3 desiredPosition, Vec3 aimPoint, double cruiseSpeed, boolean stopAtDestination,
                boolean hasTarget, Phase phase, HelicopterEnvironment environment, double divePitchBoost) {
        Plan(Vec3 desiredPosition, Vec3 aimPoint, double cruiseSpeed, boolean stopAtDestination,
             boolean hasTarget, Phase phase) {
            this(desiredPosition, aimPoint, cruiseSpeed, stopAtDestination,
                    hasTarget, phase, HelicopterEnvironment.NONE, HelicopterFlightControl.DIVE_PASS_PITCH_BONUS);
        }

        Plan withEnvironment(HelicopterEnvironment environment, double divePitchBoost) {
            return new Plan(this.desiredPosition, this.aimPoint, this.cruiseSpeed,
                    this.stopAtDestination, this.hasTarget, this.phase, environment, divePitchBoost);
        }

        FlightRequest flightRequest() {
            return this.phase.flightRequest(this);
        }
    }

    record FlightRequest(Vec3 desiredPosition, Vec3 aimPoint, double cruiseSpeed, boolean stopAtDestination,
                         boolean hasTarget, boolean approach, boolean takeoff, boolean forceForward,
                         boolean pullOutPower, boolean hoverLift, boolean stableHover,
                         boolean holdPitch, boolean divePitch, double divePitchBoost, boolean pullOutPitch,
                         boolean throttleThroughBrake, boolean sprint, boolean takeoffYawLimit) {
    }

    public enum Phase {
        TAKEOFF(false, true, false, false, false, false, false, false, false, true, false, true),
        APPROACH(true, false, false, false, false, false, false, false, false, false, false, false),
        ATTACK_RUN(false, false, true, false, false, false, false, true, false, true, true, false),
        DIVE_PASS(false, false, true, false, false, false, false, true, false, true, true, false),
        EVASIVE_PASS(true, false, false, false, true, false, false, false, false, false, true, false),
        PULL_OUT(false, false, false, true, false, true, false, false, true, true, false, false),
        FIRE_HOLD(false, false, false, false, true, true, true, false, false, false, false, false),
        COMBAT_HOVER(false, false, false, false, true, true, false, false, false, false, false, false),
        IDLE(false, false, false, false, false, true, false, false, false, false, false, false);

        private final boolean approach;
        private final boolean takeoff;
        private final boolean forceForward;
        private final boolean pullOutPower;
        private final boolean hoverLift;
        private final boolean stableHover;
        private final boolean holdPitch;
        private final boolean divePitch;
        private final boolean pullOutPitch;
        private final boolean throttleThroughBrake;
        private final boolean sprint;
        private final boolean takeoffYawLimit;

        Phase(boolean approach, boolean takeoff, boolean forceForward, boolean pullOutPower,
              boolean hoverLift, boolean stableHover, boolean holdPitch, boolean divePitch,
              boolean pullOutPitch, boolean throttleThroughBrake, boolean sprint,
              boolean takeoffYawLimit) {
            this.approach = approach;
            this.takeoff = takeoff;
            this.forceForward = forceForward;
            this.pullOutPower = pullOutPower;
            this.hoverLift = hoverLift;
            this.stableHover = stableHover;
            this.holdPitch = holdPitch;
            this.divePitch = divePitch;
            this.pullOutPitch = pullOutPitch;
            this.throttleThroughBrake = throttleThroughBrake;
            this.sprint = sprint;
            this.takeoffYawLimit = takeoffYawLimit;
        }

        private FlightRequest flightRequest(Plan plan) {
            boolean unboostedPass = this.divePitch && plan.divePitchBoost() <= 0.0D;
            return new FlightRequest(plan.desiredPosition(),
                    plan.aimPoint(),
                    plan.cruiseSpeed(),
                    plan.stopAtDestination(),
                    plan.hasTarget(),
                    this.approach || unboostedPass,
                    this.takeoff,
                    this.forceForward && !unboostedPass,
                    this.pullOutPower,
                    this.hoverLift || unboostedPass,
                    this.stableHover,
                    this.holdPitch,
                    this.divePitch,
                    plan.divePitchBoost(),
                    this.pullOutPitch,
                    this.throttleThroughBrake,
                    this.sprint,
                    this.takeoffYawLimit);
        }
    }
}
