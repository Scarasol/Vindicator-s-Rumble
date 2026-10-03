package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground;

import com.scarasol.vindicatorsrumble.api.vehicle.DriveIntent;
import com.scarasol.vindicatorsrumble.api.vehicle.DriveRequest;
import com.scarasol.vindicatorsrumble.api.vehicle.CurrentWeaponSnapshot;
import com.scarasol.vindicatorsrumble.api.vehicle.SeatHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleDimensions;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleInput;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleMovementType;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponHandle;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.DriveStrategy;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.DriveVehicleGoal;
import com.scarasol.vindicatorsrumble.util.VehicleAiLogger;
import com.scarasol.vindicatorsrumble.util.VehicleAiMemory;
import com.scarasol.vindicatorsrumble.util.VehicleAiTuning;
import com.scarasol.vindicatorsrumble.util.VehiclePathContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public final class GroundDriveStrategy implements DriveStrategy {
    private static final double DIRECT_RAM_DISTANCE_SQR = 144.0D;
    private static final double RAM_WINDUP_START_DISTANCE_SQR = 36.0D;
    private static final double RAM_WINDUP_RELEASE_DISTANCE_SQR = 121.0D;
    private static final double RAM_FRONT_ARC_DEGREES = 12.0D;
    private static final double MANEUVER_BLOCKED_DISTANCE = 1.35D;
    private static final double MANEUVER_SIDE_SQUEEZE_DISTANCE = 1.45D;
    private static final double MANEUVER_EXIT_CLEARANCE = 2.25D;
    private static final double MANEUVER_PROGRESS_DISTANCE_SQR = 0.01D;
    private static final double MANEUVER_PROGRESS_YAW_DEGREES = 4.0D;
    private static final double PATH_TARGET_REPLAN_DISTANCE_SQR = 16.0D;
    private static final double CHASE_BAND_MIN_BRAKE_LEAD = 3.0D;
    private static final double CHASE_BAND_SPEED_BRAKE_LEAD_SCALE = 16.0D;
    private static final double CHASE_BAND_MAX_BRAKE_LEAD_RATIO = 0.45D;
    private static final int MANEUVER_PROBE_INTERVAL = 5;
    private static final int MANEUVER_MAX_TICKS = 80;
    private static final int MANEUVER_STEP_MIN_TICKS = 4;
    private static final int FIRE_WINDOW_HOLD_TICKS = 20;

    private final Mob mob;
    private final DriveVehicleGoal.Mode mode;
    private VehicleController controller;
    private State state = State.IDLE;
    private Path path;
    private PathPlanKey lastPathPlanKey;
    private int replanTicks;
    private int stuckTicks;
    private int turnDirection;
    private int ramWindupTicks;
    private int retreatBlockedRamTicks;
    private int fireWindowHoldTicks;
    private int maneuverTicks;
    private int maneuverStepTicks;
    private int maneuverNoProgressTicks;
    private int maneuverTurnDirection;
    private int lastRadarTick;
    private boolean retreatingFromTarget;
    private boolean closingToTarget;
    private boolean brakingChaseBand;
    private GroundManeuverStep maneuverStep = GroundManeuverStep.BRAKE;
    private GroundClearance maneuverClearance = GroundClearance.open(GroundObstacleRadar.RANGE);
    private Vec3 lastPosition = Vec3.ZERO;
    private Vec3 maneuverLastPosition = Vec3.ZERO;
    private float maneuverLastYaw;

    public GroundDriveStrategy(Mob mob, DriveVehicleGoal.Mode mode) {
        this.mob = mob;
        this.mode = mode;
    }

    @Override
    public String name() {
        return "ground";
    }

    @Override
    public boolean canUse(VehicleController controller, boolean starting) {
        this.controller = controller;
        if (!controller.usesGroundNavigation()) {
            return false;
        }
        if (this.mode == DriveVehicleGoal.Mode.COMBAT) {
            return this.shouldUseCombatDrive(starting);
        }
        return !this.hasValidCombatTarget() && this.shouldUseNavigationDrive(starting);
    }

    @Override
    public boolean canContinueToUse(VehicleController controller) {
        this.controller = controller;
        if (!controller.usesGroundNavigation()) {
            return false;
        }
        return this.mode == DriveVehicleGoal.Mode.COMBAT
                ? this.shouldUseCombatDrive(false)
                : !this.hasValidCombatTarget() && this.shouldUseNavigationDrive(false);
    }

    @Override
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
        this.retreatBlockedRamTicks = 0;
        this.fireWindowHoldTicks = 0;
        this.retreatingFromTarget = false;
        this.closingToTarget = false;
        this.brakingChaseBand = false;
        this.resetManeuver(controller.position());
    }

    @Override
    public void stop(VehicleController controller) {
        this.controller = controller;
        this.path = null;
        this.lastPathPlanKey = null;
        this.state = State.IDLE;
        this.turnDirection = 0;
        this.ramWindupTicks = 0;
        this.retreatBlockedRamTicks = 0;
        this.fireWindowHoldTicks = 0;
        this.stuckTicks = 0;
        this.retreatingFromTarget = false;
        this.closingToTarget = false;
        this.brakingChaseBand = false;
        this.resetManeuver(controller.position());
    }

    @Override
    public void tick(VehicleController controller) {
        this.controller = controller;
        if (controller.isWreck() && this.state != State.RECOVER) {
            VehicleAiLogger.debug(this.mob, "Drive goal entering recover because vehicle {} is wrecked", controller.entity().getId());
            this.state = State.RECOVER;
        }
        if (this.retreatBlockedRamTicks > 0) {
            this.retreatBlockedRamTicks--;
        }
        if (this.fireWindowHoldTicks > 0) {
            this.fireWindowHoldTicks--;
        }

        DriveRequest request = this.effectiveDriveRequest(this.currentDriveRequest());
        VehicleAiLogger.debugEvery(this.mob, 40, "{} drive request intent={} desiredDistance={} targetEntity={} pathNodes={}",
                this.mode,
                request.intent(),
                request.desiredDistance(),
                request.targetEntity().map(Entity::getId).orElse(-1),
                this.path == null ? 0 : this.path.getNodeCount());
        if (this.mode == DriveVehicleGoal.Mode.NAVIGATION && this.navigationTargetPosition(request).isEmpty()) {
            VehicleAiLogger.debugEvery(this.mob, 40, "Drive stopping: navigation request has no valid target");
            controller.stopDriving(this.mob);
            this.path = null;
            VehicleAiMemory.clearNavigationDriveRequest(this.mob);
            return;
        }
        if (request.intent() == DriveIntent.NONE) {
            VehicleAiLogger.debugEvery(this.mob, 40, "Drive stopping: no drive request");
            controller.stopDriving(this.mob);
            this.path = null;
            if (this.mode == DriveVehicleGoal.Mode.NAVIGATION) {
                VehicleAiMemory.clearNavigationDriveRequest(this.mob);
            }
            return;
        }
        if (request.intent() != DriveIntent.RAM_TARGET) {
            this.ramWindupTicks = 0;
        }
        if (!isTargetDistanceIntent(request.intent())) {
            this.retreatingFromTarget = false;
            this.closingToTarget = false;
            this.brakingChaseBand = false;
        }

        this.updateStuckState(request);
        if (this.state == State.MANEUVER) {
            this.tickManeuver(request);
            return;
        }

        if (this.state == State.RECOVER) {
            controller.stopDriving(this.mob);
            return;
        }

        if (this.tickChaseBandBrake(request)) {
            return;
        }

        if (this.tickRetreatFromTarget(request)) {
            return;
        }

        if (request.preferStableFire() && request.targetEntity().isPresent()) {
            Entity target = request.targetEntity().get();
            if (target.isAlive() && this.hasRequiredLineOfSight(request, target)) {
                VehicleInput aimInput = GroundSteering.brakeToward(controller, target.position(), true);
                if (!aimInput.equals(VehicleInput.NONE)) {
                    VehicleAiLogger.debugEvery(this.mob, 10,
                            "Drive stable fire aiming target={} distance={} horizontalDistance={} yawDiff={} tolerance={} input={} velocity={} weapon={}",
                            target.getId(),
                            Math.sqrt(this.controller.position().distanceToSqr(target.position())),
                            Math.sqrt(this.horizontalDistanceSqr(this.controller.position(), target.position())),
                            Math.abs(this.yawDiffTo(target.position())),
                            this.stableFireYawTolerance(),
                            aimInput,
                            controller.velocity(),
                            this.currentUsableFixedWeapon(target).map(GroundDriveStrategy::weaponSummary).orElse("none"));
                    controller.drive(this.mob, aimInput);
                } else {
                    VehicleAiLogger.debugEvery(this.mob, 20,
                            "Drive stable fire holding aim on target {} distance={} horizontalDistance={} yawDiff={} weapon={}",
                            target.getId(),
                            Math.sqrt(this.controller.position().distanceToSqr(target.position())),
                            Math.sqrt(this.horizontalDistanceSqr(this.controller.position(), target.position())),
                            Math.abs(this.yawDiffTo(target.position())),
                            this.currentUsableFixedWeapon(target).map(GroundDriveStrategy::weaponSummary).orElse("none"));
                    controller.stopDriving(this.mob);
                }
                this.path = null;
                return;
            }
        }

        if (request.intent() == DriveIntent.RAM_TARGET && request.targetEntity().isPresent()) {
            Entity target = request.targetEntity().get();
            if (target.isAlive() && this.shouldUseRamWindup(target)) {
                this.tickRamWindup(target);
                return;
            }
        }

        if (this.tickHoldChaseDistance(request)) {
            return;
        }

        if (this.isWithinDesiredDistance(request)) {
            if (isTargetDistanceIntent(request.intent()) || this.mode == DriveVehicleGoal.Mode.NAVIGATION) {
                VehicleInput brakeInput = this.activeBrakeInput(request);
                if (!brakeInput.equals(VehicleInput.NONE)) {
                    VehicleAiLogger.debugEvery(this.mob, 10, "Drive active braking: within desired distance {} input={} velocity={}",
                            request.desiredDistance(),
                            brakeInput,
                            controller.velocity());
                    controller.drive(this.mob, brakeInput);
                    this.path = null;
                    return;
                }
            }

            VehicleAiLogger.debugEvery(this.mob, 20, "Drive stopping: within desired distance {}", request.desiredDistance());
            controller.stopDriving(this.mob);
            this.path = null;
            if (this.mode == DriveVehicleGoal.Mode.NAVIGATION) {
                VehicleAiMemory.clearNavigationDriveRequest(this.mob);
            }
            return;
        }

        if (this.replanTicks > 0) {
            this.replanTicks--;
        }
        if (this.shouldReplanPath(request)) {
            this.state = State.PLAN_PATH;
            this.planPath(request);
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
        if (this.shouldRecoverFromBlockedPath(request)) {
            this.beginManeuver(request, "path unavailable");
            this.tickManeuver(request);
            return;
        }

        this.state = State.FOLLOW_PATH;
        Vec3 waypoint = this.nextWaypoint(request);
        if (this.state == State.MANEUVER) {
            this.tickManeuver(request);
            return;
        }
        if (waypoint == null) {
            VehicleAiLogger.debugEvery(this.mob, 40, "Drive stopping: no waypoint for request {}", request.intent());
            controller.stopDriving(this.mob);
            return;
        }

        double remainingDistanceSqr = this.horizontalDistanceSqr(controller.position(),
                request.intent() == DriveIntent.MOVE_TO
                        ? this.navigationTargetPosition(request).orElse(waypoint) : waypoint);
        GroundSteering.SteeringResult steering = request.intent() == DriveIntent.RAM_TARGET
                ? GroundSteering.steerRamToward(controller, waypoint, this.turnDirection)
                : GroundSteering.steerToward(controller, waypoint, remainingDistanceSqr, request.preferStableFire(), this.turnDirection);
        VehicleInput input = steering.input();
        this.turnDirection = steering.turnDirection();
        VehicleAiLogger.debugEvery(this.mob, 20, "Drive following waypoint {} with input {}", waypoint, input);
        controller.drive(this.mob, input);
    }

    private boolean shouldUseCombatDrive(boolean starting) {
        return this.shouldUseCombatDriveRequest(this.selectCombatDriveRequest(), starting);
    }

    private boolean shouldUseCombatDriveRequest(DriveRequest request, boolean starting) {
        if (request.targetEntity().isPresent()) {
            Entity target = request.targetEntity().get();
            if (!target.isAlive()) {
                return false;
            }
            if (target instanceof LivingEntity livingTarget && !VehicleAiTuning.isValidVehicleDriveTarget(this.mob, livingTarget)) {
                return false;
            }
            double distanceSqr = this.controller.position().distanceToSqr(target.position());
            if (request.intent() == DriveIntent.RAM_TARGET) {
                boolean shouldRam = this.shouldUsePressureRam(target, false)
                        || (this.retreatBlockedRamTicks > 0 && target.getVehicle() == null && this.canUseGroundRamMovement())
                        || this.shouldUseRamming();
                VehicleAiLogger.debugEvery(this.mob, 20,
                        "Ram drive {} target={} distanceSqr={} result={}",
                        starting ? "start check" : "continue check",
                        target.getId(),
                        distanceSqr,
                        shouldRam);
                return shouldRam;
            }
            if (isTargetDistanceIntent(request.intent())) {
                if (!this.hasRequiredLineOfSight(request, target)) {
                    VehicleAiLogger.debugEvery(this.mob, 20,
                            "Combat drive continuing: target {} lacks line of sight within follow range",
                            target.getId());
                    return true;
                }
                double stopDistance = request.desiredDistance() > 0.0D ? request.desiredDistance() : VehicleAiTuning.chaseStopDistance(this.mob);
                double startDistance = VehicleAiTuning.chaseStartDistance(this.mob);
                boolean retreat = shouldRetreatFromChase(distanceSqr, stopDistance, startDistance, this.retreatingFromTarget);
                boolean close = shouldCloseToChase(distanceSqr, stopDistance, startDistance, this.closingToTarget);
                boolean finishRetreat = this.retreatingFromTarget && !retreat;
                boolean needsBrake = this.shouldBrakeNear(distanceSqr, stopDistance)
                        || shouldBrakeChaseBand(
                                distanceSqr,
                                stopDistance,
                                startDistance,
                                this.closingToTarget,
                                this.retreatingFromTarget,
                                this.brakingChaseBand,
                                GroundSteering.shouldBrake(this.controller),
                                this.horizontalSpeed());
                boolean shouldDrive = retreat
                        || close
                        || finishRetreat
                        || needsBrake
                        || (starting && distanceSqr > startDistance * startDistance);
                if (!shouldDrive && request.preferStableFire()) {
                    shouldDrive = this.needsStableFireControl(target);
                }
                VehicleAiLogger.debugEvery(this.mob, 40,
                        "Combat drive {} target={} distanceSqr={} startDistance={} stopDistance={} retreat={} close={} finishRetreat={} retreating={} closing={} stableFire={} result={}",
                        starting ? "start check" : "continue check",
                        target.getId(),
                        distanceSqr,
                        startDistance,
                        stopDistance,
                        retreat,
                        close,
                        finishRetreat,
                        this.retreatingFromTarget,
                        this.closingToTarget,
                        request.preferStableFire(),
                        shouldDrive);
                return shouldDrive;
            }
            return request.desiredDistance() <= 0.0D || distanceSqr > request.desiredDistance() * request.desiredDistance();
        }
        if (request.targetPosition().isPresent()) {
            return request.desiredDistance() <= 0.0D
                    || this.controller.position().distanceToSqr(request.targetPosition().get()) > request.desiredDistance() * request.desiredDistance();
        }
        return false;
    }

    private boolean shouldUseNavigationDrive(boolean starting) {
        if (this.controller != null && !this.controller.usesGroundNavigation()) {
            return false;
        }
        if (starting) {
            return this.hasSignificantNavigationDriveRequest();
        }

        DriveRequest request = VehicleAiMemory.getNavigationDriveRequest(this.mob);
        if (request.intent() == DriveIntent.NONE) {
            return false;
        }
        if (this.navigationTargetPosition(request).isEmpty()) {
            VehicleAiMemory.clearNavigationDriveRequest(this.mob);
            return false;
        }
        return true;
    }

    private boolean hasValidCombatTarget() {
        return VehicleAiTuning.isValidVehicleDriveTarget(this.mob, this.mob.getTarget());
    }

    private LivingEntity validCombatTarget() {
        LivingEntity target = this.mob.getTarget();
        return VehicleAiTuning.isValidVehicleDriveTarget(this.mob, target) ? target : null;
    }

    private boolean shouldUseRamming() {
        if (this.controller == null) {
            return false;
        }
        VehicleMovementType movementType = this.controller.movementType();
        Optional<SeatHandle> currentSeat = this.controller.seatOf(this.mob);
        List<SeatHandle> seats = this.controller.seats();
        boolean currentSeatCanDrive = currentSeat.map(SeatHandle::canDrive).orElse(false);
        boolean currentSeatHasWeapon = currentSeat.map(SeatHandle::hasWeapon).orElse(false);
        boolean occupiedWeaponSeat = seats.stream()
                .filter(SeatHandle::hasWeapon)
                .anyMatch(SeatHandle::occupied);
        boolean ammoConsumptionEnabled = CommonConfig.VEHICLE_AI_NEEDS_AMMO.get();
        boolean currentSeatHasUsableWeapon = this.currentSeatHasUsableWeapon(currentSeatHasWeapon, ammoConsumptionEnabled);
        boolean hasAnyVehicleWeaponAmmo = this.controller.hasAnyWeaponAmmo(this.mob);
        boolean result = shouldUseRamming(
                movementType,
                currentSeatCanDrive,
                currentSeatHasWeapon,
                currentSeatHasUsableWeapon,
                occupiedWeaponSeat,
                ammoConsumptionEnabled,
                hasAnyVehicleWeaponAmmo);
        VehicleAiLogger.debugEvery(this.mob, 40,
                "Ram eligibility movement={} seatPresent={} canDrive={} seatWeapon={} currentSeatUsableWeapon={} occupiedWeaponSeat={} ammoRequired={} hasAnyVehicleWeaponAmmo={} result={}",
                movementType,
                currentSeat.isPresent(),
                currentSeatCanDrive,
                currentSeatHasWeapon,
                currentSeatHasUsableWeapon,
                occupiedWeaponSeat,
                ammoConsumptionEnabled,
                hasAnyVehicleWeaponAmmo,
                result);
        return result;
    }

    private boolean shouldUsePressureRam(Entity target, boolean stableFireRequest) {
        double distanceSqr = this.horizontalDistanceSqr(this.controller.position(), target.position());
        double stopDistance = VehicleAiTuning.chaseStopDistance(this.mob);
        return !stableFireRequest
                && this.canUseGroundRamMovement()
                && target.getVehicle() == null
                && distanceSqr <= stopDistance * stopDistance
                && !this.isBehindVehicle(this.controller.position(), target.position())
                && !(this.hasCurrentVehicleFireWindow(target) && this.hasFireWindowLineOfSight(target));
    }

    private boolean currentSeatHasUsableWeapon(boolean currentSeatHasWeapon, boolean ammoConsumptionEnabled) {
        if (!currentSeatHasWeapon) {
            return false;
        }
        if (!ammoConsumptionEnabled) {
            return true;
        }
        List<WeaponHandle> weapons = this.controller.weapons(this.mob);
        for (WeaponHandle weapon : weapons) {
            if (this.controller.weaponStatus(this.mob, weapon).hasAmmo()) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldUseRamming(VehicleMovementType movementType, Optional<SeatHandle> currentSeat, List<SeatHandle> seats) {
        return shouldUseRamming(movementType, currentSeat, seats, false, true);
    }

    public static boolean shouldUseRamming(VehicleMovementType movementType, Optional<SeatHandle> currentSeat, List<SeatHandle> seats,
                                           boolean ammoConsumptionEnabled, boolean hasAnyWeaponAmmo) {
        if (currentSeat.isEmpty()) {
            return false;
        }
        boolean occupiedWeaponSeat = seats.stream()
                .filter(SeatHandle::hasWeapon)
                .anyMatch(SeatHandle::occupied);
        return shouldUseRamming(
                movementType,
                currentSeat.get().canDrive(),
                currentSeat.get().hasWeapon(),
                occupiedWeaponSeat,
                ammoConsumptionEnabled,
                hasAnyWeaponAmmo);
    }

    public static boolean shouldUseRamming(VehicleMovementType movementType, boolean currentSeatCanDrive,
                                           boolean currentSeatHasWeapon, boolean occupiedWeaponSeat) {
        return shouldUseRamming(movementType, currentSeatCanDrive, currentSeatHasWeapon, occupiedWeaponSeat, false, true);
    }

    public static boolean shouldUseRamming(VehicleMovementType movementType, boolean currentSeatCanDrive,
                                           boolean currentSeatHasWeapon, boolean occupiedWeaponSeat,
                                           boolean ammoConsumptionEnabled, boolean hasAnyWeaponAmmo) {
        boolean currentSeatHasUsableWeapon = currentSeatHasWeapon && (!ammoConsumptionEnabled || hasAnyWeaponAmmo);
        return shouldUseRamming(
                movementType,
                currentSeatCanDrive,
                currentSeatHasWeapon,
                currentSeatHasUsableWeapon,
                occupiedWeaponSeat,
                ammoConsumptionEnabled,
                hasAnyWeaponAmmo);
    }

    public static boolean shouldUseRamming(VehicleMovementType movementType, boolean currentSeatCanDrive,
                                           boolean currentSeatHasWeapon, boolean currentSeatHasUsableWeapon,
                                           boolean occupiedWeaponSeat, boolean ammoConsumptionEnabled,
                                           boolean hasAnyVehicleWeaponAmmo) {
        boolean currentSeatOutOfAmmo = ammoConsumptionEnabled && currentSeatHasWeapon && !currentSeatHasUsableWeapon;
        boolean weaponSeatsCannotCover = !occupiedWeaponSeat || (ammoConsumptionEnabled && !hasAnyVehicleWeaponAmmo);
        return (movementType == VehicleMovementType.WHEEL || movementType == VehicleMovementType.TRACK)
                && currentSeatCanDrive
                && ((!currentSeatHasWeapon && weaponSeatsCannotCover) || currentSeatOutOfAmmo);
    }

    private boolean shouldUseRamWindup(Entity target) {
        double distanceSqr = this.horizontalDistanceSqr(this.controller.position(), target.position());
        double yawDiff = Math.abs(this.yawDiffTo(target.position()));
        if (this.ramWindupTicks > 0) {
            boolean shouldContinue = shouldContinueRamWindup(distanceSqr, yawDiff);
            if (!shouldContinue) {
                this.ramWindupTicks = 0;
            }
            return shouldContinue;
        }
        return shouldStartRamWindup(distanceSqr, yawDiff);
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
        this.ramWindupTicks++;
        GroundSteering.SteeringResult steering = GroundSteering.ramWindupAway(this.controller, target.position(), this.turnDirection);
        VehicleInput input = steering.input();
        this.turnDirection = steering.turnDirection();
        this.path = null;
        VehicleAiLogger.debugEvery(this.mob, 10,
                "Ram windup target={} ticks={} distanceSqr={} yawDiff={} input={} velocity={}",
                target.getId(),
                this.ramWindupTicks,
                this.horizontalDistanceSqr(this.controller.position(), target.position()),
                Math.abs(this.yawDiffTo(target.position())),
                input,
                this.controller.velocity());
        this.controller.drive(this.mob, input);
    }

    private DriveRequest currentDriveRequest() {
        if (this.mode == DriveVehicleGoal.Mode.NAVIGATION) {
            return VehicleAiMemory.getNavigationDriveRequest(this.mob);
        }
        return this.selectCombatDriveRequest();
    }

    private DriveRequest selectCombatDriveRequest() {
        DriveRequest request = this.effectiveDriveRequest(this.preferredTacticalDriveRequest());
        LivingEntity target = this.validCombatTarget();
        boolean stableFireRequest = this.isActiveStableFireRequest(request);
        DriveIntent requestedIntent = request.intent() == DriveIntent.SUPPORT_FIRE && !stableFireRequest
                ? DriveIntent.CHASE_TARGET
                : request.intent();
        boolean pressureRam = target != null && this.shouldUsePressureRam(target, stableFireRequest);
        if (pressureRam) {
            requestedIntent = DriveIntent.RAM_TARGET;
        }
        boolean retreatBlockedRam = target != null
                && this.retreatBlockedRamTicks > 0
                && target.getVehicle() == null
                && this.canUseGroundRamMovement()
                && !stableFireRequest;
        boolean canUseRamming = target != null
                && (pressureRam || retreatBlockedRam || this.shouldUseRamming())
                && (!stableFireRequest || pressureRam);
        boolean hasValidDriveTarget = this.hasValidDriveTarget(request, target);
        DriveIntent intent = combatDriveIntent(requestedIntent, hasValidDriveTarget, canUseRamming);
        VehicleAiLogger.debugEvery(this.mob, 20,
                "Drive combat selection memoryIntent={} requestedIntent={} resultIntent={} stableFireRequest={} hasValidTarget={} canUseRamming={} pressureRam={} retreatBlockedRam={} retreatBlockedRamTicks={} target={} memoryWeapon={} vehicleWeapon={}",
                request.intent(),
                requestedIntent,
                intent,
                stableFireRequest,
                hasValidDriveTarget,
                canUseRamming,
                pressureRam,
                retreatBlockedRam,
                this.retreatBlockedRamTicks,
                request.targetEntity().map(Entity::getId).orElse(target == null ? -1 : target.getId()),
                request.targetEntity().flatMap(value -> VehicleAiMemory.getCurrentWeapon(this.mob, this.controller, value))
                        .map(GroundDriveStrategy::weaponSummary)
                        .orElse("none"),
                request.targetEntity().flatMap(value -> VehicleAiMemory.getVehicleCurrentWeapon(this.mob, this.controller, value))
                        .map(GroundDriveStrategy::weaponSummary)
                        .orElse("none"));
        if (intent == DriveIntent.RAM_TARGET) {
            return DriveRequest.ram(target);
        }
        if (intent == request.intent() && request.intent() != DriveIntent.NONE) {
            return request;
        }
        Entity driveTarget = request.targetEntity().filter(Entity::isAlive).orElse(target);
        if (intent == DriveIntent.CHASE_TARGET && driveTarget != null) {
            return DriveRequest.chase(driveTarget, VehicleAiTuning.chaseDistance(this.mob));
        }
        VehicleAiLogger.debugEvery(this.mob, 40, "Drive has no target and no memory request");
        return DriveRequest.NONE;
    }

    private DriveRequest preferredTacticalDriveRequest() {
        DriveRequest ownRequest = VehicleAiMemory.getDriveRequest(this.mob);
        DriveRequest vehicleRequest = VehicleAiMemory.getVehicleDriveRequest(this.mob, this.controller);
        if (vehicleRequest.intent() == DriveIntent.SUPPORT_FIRE
                && vehicleRequest.preferStableFire()
                && this.hasValidDriveTarget(vehicleRequest, null)
                && ownRequest.intent() != DriveIntent.SUPPORT_FIRE) {
            VehicleAiLogger.debugEvery(this.mob, 20,
                    "Drive using vehicle shared support-fire request target={} ownIntent={}",
                    vehicleRequest.targetEntity().map(Entity::getId).orElse(-1),
                    ownRequest.intent());
            return vehicleRequest;
        }
        return ownRequest;
    }

    private DriveRequest effectiveDriveRequest(DriveRequest request) {
        if (!request.preferStableFire()) {
            return request;
        }
        Entity target = request.targetEntity().orElse(null);
        Optional<CurrentWeaponSnapshot> fixedWeapon = this.currentUsableFixedWeapon(target);
        if (fixedWeapon.isPresent()) {
            VehicleAiLogger.debugEvery(this.mob, 20,
                    "Drive stable fire enabled: target={} weapon={}",
                    target == null ? -1 : target.getId(),
                    weaponSummary(fixedWeapon.get()));
            return request;
        }
        VehicleAiLogger.debugEvery(this.mob, 20,
                "Drive stable fire disabled: no current usable fixed weapon for request target={}",
                target == null ? -1 : target.getId());
        return request.withPreferStableFire(false);
    }

    private Optional<CurrentWeaponSnapshot> currentUsableFixedWeapon(Entity target) {
        Optional<CurrentWeaponSnapshot> ownWeapon = VehicleAiMemory.getCurrentWeapon(this.mob, this.controller, target)
                .filter(CurrentWeaponSnapshot::canUseFixedWeaponAttackPosture);
        if (ownWeapon.isPresent()) {
            return ownWeapon;
        }
        return VehicleAiMemory.getVehicleCurrentWeapon(this.mob, this.controller, target)
                .filter(CurrentWeaponSnapshot::canUseFixedWeaponAttackPosture);
    }

    private boolean isActiveStableFireRequest(DriveRequest request) {
        return request.intent() == DriveIntent.SUPPORT_FIRE
                && request.preferStableFire()
                && this.hasValidDriveTarget(request, null);
    }

    private boolean hasValidDriveTarget(DriveRequest request, LivingEntity fallbackTarget) {
        if (fallbackTarget != null) {
            return true;
        }
        if (request.targetEntity().isEmpty()) {
            return false;
        }
        Entity target = request.targetEntity().get();
        if (!target.isAlive()) {
            return false;
        }
        return !(target instanceof LivingEntity livingTarget)
                || VehicleAiTuning.isValidVehicleDriveTarget(this.mob, livingTarget);
    }

    static DriveIntent combatDriveIntent(DriveIntent memoryIntent, boolean hasValidCombatTarget, boolean canUseRamming) {
        if (hasValidCombatTarget && memoryIntent == DriveIntent.SUPPORT_FIRE) {
            return DriveIntent.SUPPORT_FIRE;
        }
        if (hasValidCombatTarget && canUseRamming) {
            return DriveIntent.RAM_TARGET;
        }
        if (hasValidCombatTarget && memoryIntent != DriveIntent.NONE) {
            return memoryIntent;
        }
        return hasValidCombatTarget ? DriveIntent.CHASE_TARGET : DriveIntent.NONE;
    }

    private static boolean isTargetDistanceIntent(DriveIntent intent) {
        return intent == DriveIntent.CHASE_TARGET || intent == DriveIntent.SUPPORT_FIRE;
    }

    private boolean isWithinDesiredDistance(DriveRequest request) {
        if (request.desiredDistance() <= 0.0D) {
            return false;
        }
        double desiredDistance = request.desiredDistance();
        if (this.mode == DriveVehicleGoal.Mode.NAVIGATION) {
            desiredDistance += this.controller.estimatedHorizontalStoppingDistance();
        }
        double desiredDistanceSqr = desiredDistance * desiredDistance;
        if (request.targetEntity().isPresent()) {
            Entity target = request.targetEntity().get();
            return target.isAlive()
                    && this.controller.position().distanceToSqr(target.position()) <= desiredDistanceSqr
                    && this.hasRequiredLineOfSight(request, target);
        }
        if (request.targetPosition().isPresent()) {
            return this.controller.position().distanceToSqr(request.targetPosition().get()) <= desiredDistanceSqr;
        }
        return false;
    }

    private boolean canUseGroundRamMovement() {
        if (this.controller == null) {
            return false;
        }
        VehicleMovementType movementType = this.controller.movementType();
        return movementType == VehicleMovementType.WHEEL || movementType == VehicleMovementType.TRACK;
    }

    private void planPath(DriveRequest request) {
        VehicleDimensions dimensions = this.controller.pathingDimensions();
        PathTarget pathTarget = this.pathTarget(request);
        if (pathTarget == null) {
            this.path = null;
            this.lastPathPlanKey = null;
        } else if (request.path().isPresent() && this.path != request.path().get()) {
            this.path = request.path().get();
            this.path.setNextNodeIndex(0);
            this.lastPathPlanKey = this.pathPlanKey(request, pathTarget, dimensions);
        } else if (request.path().isPresent()) {
            this.lastPathPlanKey = this.pathPlanKey(request, pathTarget, dimensions);
        } else {
            try (VehiclePathContext.Scope ignored = VehiclePathContext.push(this.mob, dimensions)) {
                this.path = this.mob.getNavigation().createPath(pathTarget.targetBlock(), 1);
            }
            this.lastPathPlanKey = this.pathPlanKey(request, pathTarget, dimensions);
        }
        this.replanTicks = CommonConfig.VEHICLE_PATH_REPLAN_INTERVAL.get();
        VehicleAiLogger.debug(this.mob,
                "Planned vehicle path detail intent={} source={} targetBlock={} targetEntity={} targetPos={} vehiclePos={} dimensions={} pathTarget={} nodes={} done={} nextIndex={} firstNode={} nextNode={} lastNode={} replanTicks={}",
                request.intent(),
                pathTarget == null ? "none" : pathTarget.source(),
                pathTarget == null ? null : pathTarget.targetBlock(),
                pathTarget == null ? -1 : pathTarget.targetEntityId(),
                pathTarget == null ? null : pathTarget.targetPosition(),
                this.controller.position(),
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
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return null;
        }
        return new PathTarget(target.blockPosition(), target.getId(), target.position(), "mobTarget");
    }

    private Vec3 nextWaypoint(DriveRequest request) {
        if (request.intent() == DriveIntent.RAM_TARGET && request.targetEntity().isPresent()) {
            Entity target = request.targetEntity().get();
            if (target.isAlive() && this.shouldUseDirectRam(target)) {
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
        }
        if (this.path != null) {
            Vec3 position = this.controller.position();
            double passDistanceSqr = this.nodePassDistanceSqr();
            int beforeIndex = this.path.getNextNodeIndex();
            int advanced = 0;
            while (!this.path.isDone() && this.shouldAdvancePastNode(position, nodeCenter(this.path.getNextNode()), passDistanceSqr)) {
                this.path.advance();
                advanced++;
            }
            if (!this.path.isDone()) {
                Vec3 waypoint = this.lookaheadWaypoint(position);
                VehicleAiLogger.debugEvery(this.mob, 10,
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
            if (this.shouldRecoverFromExhaustedPath(request)) {
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
        Vec3 waypoint = this.mob.getTarget() == null ? null : this.mob.getTarget().position();
        VehicleAiLogger.debugEvery(this.mob, 20,
                "Drive waypoint fallback mobTarget intent={} target={} waypoint={}",
                request.intent(),
                this.mob.getTarget() == null ? -1 : this.mob.getTarget().getId(),
                waypoint);
        return waypoint;
    }

    private boolean shouldUseDirectRam(Entity target) {
        double distanceSqr = this.horizontalDistanceSqr(this.controller.position(), target.position());
        if (distanceSqr > DIRECT_RAM_DISTANCE_SQR) {
            return false;
        }
        Entity aimEntity = target.getVehicle() == null ? target : target.getVehicle();
        return this.controller.hasLineOfSight(this.mob, target, aimEntity.getBoundingBox().getCenter());
    }

    private boolean shouldAdvancePastNode(Vec3 position, Vec3 waypoint, double passDistanceSqr) {
        double distanceSqr = position.distanceToSqr(waypoint);
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
        Vec3 fallback = nodeCenter(this.path.getNode(index));
        for (int i = index; i < nodeCount; i++) {
            Vec3 point = nodeCenter(this.path.getNode(i));
            double segmentLength = previous.distanceTo(point);
            if (segmentLength >= remaining && segmentLength > 1.0E-4D) {
                Vec3 direction = point.subtract(previous).scale(1.0D / segmentLength);
                return previous.add(direction.scale(remaining));
            }
            remaining -= segmentLength;
            previous = point;
            fallback = point;
        }
        return fallback;
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

    private boolean shouldBrakeNear(double distanceSqr, double desiredDistance) {
        return desiredDistance > 0.0D
                && distanceSqr <= desiredDistance * desiredDistance
                && GroundSteering.shouldBrake(this.controller);
    }

    private boolean tickRetreatFromTarget(DriveRequest request) {
        if (!isTargetDistanceIntent(request.intent()) || request.targetEntity().isEmpty() || request.desiredDistance() <= 0.0D) {
            this.retreatingFromTarget = false;
            this.closingToTarget = false;
            this.clearFireWindowHold();
            return false;
        }
        Entity target = request.targetEntity().get();
        if (!target.isAlive()) {
            this.retreatingFromTarget = false;
            this.closingToTarget = false;
            this.clearFireWindowHold();
            return false;
        }

        double distanceSqr = this.horizontalDistanceSqr(this.controller.position(), target.position());
        double startDistance = VehicleAiTuning.chaseStartDistance(this.mob);
        boolean retreat = shouldRetreatFromChase(distanceSqr, request.desiredDistance(), startDistance, this.retreatingFromTarget);
        boolean wasRetreating = this.retreatingFromTarget;
        this.retreatingFromTarget = retreat;
        if (retreat) {
            this.closingToTarget = false;
            this.brakingChaseBand = false;
        }
        GroundClearance clearance = retreat ? GroundObstacleRadar.scan(this.controller) : null;
        if (VehicleAiLogger.isDebugEnabled() && this.mob.tickCount % 10 == 0) {
            VehicleAiLogger.debug(this.mob,
                    "Drive retreat decision target={} distance={} distanceSqr={} stopDistance={} startDistance={} retreat={} wasRetreating={} turnDirection={} clearance={} velocity={}",
                    target.getId(),
                    Math.sqrt(distanceSqr),
                    distanceSqr,
                    request.desiredDistance(),
                    startDistance,
                    retreat,
                    wasRetreating,
                    this.turnDirection,
                    clearance == null ? "not_scanned" : clearance,
                    this.controller.velocity());
        }
        if (!retreat) {
            this.retreatBlockedRamTicks = 0;
            this.clearFireWindowHold();
            if (wasRetreating) {
                VehicleInput brakeInput = GroundSteering.brake(this.controller);
                VehicleAiLogger.debugEvery(this.mob, 10,
                        "Drive retreat complete target={} distanceSqr={} stopDistance={} startDistance={} input={} velocity={}",
                        target.getId(),
                        distanceSqr,
                        request.desiredDistance(),
                        startDistance,
                        brakeInput,
                        this.controller.velocity());
                if (brakeInput.equals(VehicleInput.NONE)) {
                    this.controller.stopDriving(this.mob);
                } else {
                    this.controller.drive(this.mob, brakeInput);
                }
                this.path = null;
                return true;
            }
            return false;
        }

        GroundSteering.SteeringResult steering = GroundSteering.retreatFrom(this.controller, target.position(), this.turnDirection);
        this.turnDirection = steering.turnDirection();
        VehicleInput input = steering.input();
        if (isRetreatInputBlocked(input, clearance, MANEUVER_EXIT_CLEARANCE)) {
            boolean fireWindow = this.hasCurrentVehicleFireWindow(target);
            boolean lineOfSight = fireWindow && this.hasFireWindowLineOfSight(target);
            VehicleAiLogger.debugEvery(this.mob, 5,
                    "Drive retreat blocked target={} input={} clearance={} fireWindow={} lineOfSight={} targetVehicle={} velocity={} weaponWindows={}",
                    target.getId(),
                    input,
                    clearance,
                    fireWindow,
                    lineOfSight,
                    target.getVehicle() == null ? -1 : target.getVehicle().getId(),
                    this.controller.velocity(),
                    this.vehicleWeaponWindowSummary(target));
            this.path = null;
            if (fireWindow && lineOfSight) {
                this.retreatBlockedRamTicks = 0;
                this.beginFireWindowHold(target, clearance);
                VehicleInput brakeInput = GroundSteering.brake(this.controller);
                if (brakeInput.equals(VehicleInput.NONE)) {
                    this.controller.stopDriving(this.mob);
                } else {
                    this.controller.drive(this.mob, brakeInput);
                }
                return true;
            }
            if (target.getVehicle() == null && this.canUseGroundRamMovement()) {
                this.clearFireWindowHold();
                this.retreatBlockedRamTicks = 20;
                return false;
            }
            this.retreatBlockedRamTicks = 0;
            this.clearFireWindowHold();
            this.beginManeuver(request, "retreat blocked");
            this.tickManeuver(request);
            return true;
        }
        this.retreatBlockedRamTicks = 0;
        this.clearFireWindowHold();
        VehicleAiLogger.debugEvery(this.mob, 10,
                "Drive retreating from target={} distanceSqr={} stopDistance={} startDistance={} input={} velocity={}",
                target.getId(),
                distanceSqr,
                request.desiredDistance(),
                startDistance,
                input,
                this.controller.velocity());
        if (input.equals(VehicleInput.NONE)) {
            this.controller.stopDriving(this.mob);
        } else {
            this.controller.drive(this.mob, input);
        }
        this.path = null;
        return true;
    }

    private boolean tickHoldChaseDistance(DriveRequest request) {
        if (!isTargetDistanceIntent(request.intent()) || request.targetEntity().isEmpty() || request.desiredDistance() <= 0.0D) {
            return false;
        }
        Entity target = request.targetEntity().get();
        if (!target.isAlive()) {
            return false;
        }

        double distanceSqr = this.horizontalDistanceSqr(this.controller.position(), target.position());
        double stopDistance = request.desiredDistance();
        double startDistance = VehicleAiTuning.chaseStartDistance(this.mob);
        boolean close = shouldCloseToChase(distanceSqr, stopDistance, startDistance, this.closingToTarget);
        this.closingToTarget = close;
        if (close) {
            this.brakingChaseBand = false;
        }
        if (close || distanceSqr <= stopDistance * stopDistance || distanceSqr > startDistance * startDistance) {
            return false;
        }

        VehicleInput brakeInput = GroundSteering.brake(this.controller);
        VehicleAiLogger.debugEvery(this.mob, 10,
                "Drive holding chase band target={} distanceSqr={} stopDistance={} startDistance={} input={} velocity={}",
                target.getId(),
                distanceSqr,
                stopDistance,
                startDistance,
                brakeInput,
                this.controller.velocity());
        if (brakeInput.equals(VehicleInput.NONE)) {
            this.controller.stopDriving(this.mob);
        } else {
            this.controller.drive(this.mob, brakeInput);
        }
        this.path = null;
        return true;
    }

    private boolean tickChaseBandBrake(DriveRequest request) {
        if (!isTargetDistanceIntent(request.intent()) || request.targetEntity().isEmpty() || request.desiredDistance() <= 0.0D) {
            this.brakingChaseBand = false;
            return false;
        }
        Entity target = request.targetEntity().get();
        if (!target.isAlive()) {
            this.brakingChaseBand = false;
            return false;
        }

        double distanceSqr = this.horizontalDistanceSqr(this.controller.position(), target.position());
        double stopDistance = request.desiredDistance();
        double startDistance = VehicleAiTuning.chaseStartDistance(this.mob);
        if (stopDistance <= 0.0D || startDistance <= stopDistance) {
            this.brakingChaseBand = false;
            return false;
        }

        double speed = this.horizontalSpeed();
        boolean finishedClosing = this.closingToTarget && shouldBrakeClosingBeforeInnerBand(distanceSqr, stopDistance, startDistance, speed);
        boolean finishedRetreating = this.retreatingFromTarget && shouldBrakeRetreatingBeforeOuterBand(distanceSqr, stopDistance, startDistance, speed);
        if (finishedClosing || finishedRetreating) {
            String reason = finishedClosing ? "approaching inner band" : "approaching outer band";
            this.closingToTarget = false;
            this.retreatingFromTarget = false;
            this.brakingChaseBand = true;
            this.path = null;
            VehicleAiLogger.debug(this.mob,
                    "Drive chase band brake started reason={} target={} distance={} distanceSqr={} stopDistance={} startDistance={} brakeLead={} velocity={}",
                    reason,
                    target.getId(),
                    Math.sqrt(distanceSqr),
                    distanceSqr,
                    stopDistance,
                    startDistance,
                    chaseBandBrakeLeadDistance(speed, stopDistance, startDistance),
                    this.controller.velocity());
        }

        if (!this.brakingChaseBand) {
            return false;
        }

        VehicleInput brakeInput = GroundSteering.brake(this.controller);
        if (brakeInput.equals(VehicleInput.NONE)) {
            this.brakingChaseBand = false;
            VehicleAiLogger.debugEvery(this.mob, 10,
                    "Drive chase band brake complete target={} distance={} distanceSqr={} stopDistance={} startDistance={}",
                    target.getId(),
                    Math.sqrt(distanceSqr),
                    distanceSqr,
                    stopDistance,
                    startDistance);
            this.controller.stopDriving(this.mob);
            return true;
        }

        VehicleAiLogger.debugEvery(this.mob, 10,
                "Drive chase band braking target={} distance={} distanceSqr={} stopDistance={} startDistance={} brakeLead={} input={} velocity={}",
                target.getId(),
                Math.sqrt(distanceSqr),
                distanceSqr,
                stopDistance,
                startDistance,
                chaseBandBrakeLeadDistance(speed, stopDistance, startDistance),
                brakeInput,
                this.controller.velocity());
        this.controller.drive(this.mob, brakeInput);
        return true;
    }

    static boolean shouldRetreatFromChase(double distanceSqr, double stopDistance, double startDistance, boolean alreadyRetreating) {
        if (stopDistance <= 0.0D || startDistance <= stopDistance) {
            return false;
        }
        double threshold = alreadyRetreating ? startDistance : stopDistance;
        return distanceSqr < threshold * threshold;
    }

    static boolean shouldCloseToChase(double distanceSqr, double stopDistance, double startDistance, boolean alreadyClosing) {
        if (stopDistance <= 0.0D || startDistance <= stopDistance) {
            return false;
        }
        double threshold = alreadyClosing ? stopDistance : startDistance;
        return distanceSqr > threshold * threshold;
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

    private boolean needsStableFireControl(Entity target) {
        double yawDiff = Math.abs(this.yawDiffTo(target.position()));
        boolean needsControl = yawDiff > this.stableFireYawTolerance() || GroundSteering.shouldBrake(this.controller);
        VehicleAiLogger.debugEvery(this.mob, 20,
                "Stable fire control target={} yawDiff={} tolerance={} velocity={} result={}",
                target.getId(),
                yawDiff,
                this.stableFireYawTolerance(),
                this.controller.velocity(),
                needsControl);
        return needsControl;
    }

    private double stableFireYawTolerance() {
        return this.controller.movementType() == VehicleMovementType.TRACK ? 6.0D : 9.0D;
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

    private boolean hasRequiredLineOfSight(DriveRequest request, Entity target) {
        if (!request.needsLineOfSight()) {
            return true;
        }
        Entity aimEntity = target.getVehicle() == null ? target : target.getVehicle();
        Vec3 aimPoint = request.preferStableFire()
                ? this.controller.aimPoint(this.mob, target)
                : aimEntity.getBoundingBox().getCenter();
        boolean clear = this.controller.hasLineOfSight(this.mob, target, aimPoint);
        VehicleAiLogger.debugEvery(this.mob, 20,
                "Drive line of sight target={} required={} clear={}",
                target.getId(),
                request.needsLineOfSight(),
                clear);
        return clear;
    }

    private VehicleInput activeBrakeInput(DriveRequest request) {
        Optional<Vec3> targetPosition = this.driveTargetPosition(request);
        return targetPosition
                .map(position -> GroundSteering.brakeToward(this.controller, position))
                .orElseGet(() -> GroundSteering.brake(this.controller));
    }

    private Optional<Vec3> driveTargetPosition(DriveRequest request) {
        if (request.targetEntity().isPresent() && request.targetEntity().get().isAlive()) {
            return Optional.of(request.targetEntity().get().position());
        }
        if (request.targetPosition().isPresent()) {
            return request.targetPosition();
        }
        return Optional.ofNullable(this.mob.getTarget()).filter(Entity::isAlive).map(Entity::position);
    }

    private boolean hasSignificantNavigationDriveRequest() {
        DriveRequest request = VehicleAiMemory.getNavigationDriveRequest(this.mob);
        if (this.isSignificantNavigationRequest(request)) {
            return true;
        }
        if (request.intent() != DriveIntent.NONE) {
            VehicleAiMemory.clearNavigationDriveRequest(this.mob);
        }
        return false;
    }

    private boolean isSignificantNavigationRequest(DriveRequest request) {
        if (request.intent() == DriveIntent.NONE) {
            return false;
        }
        double minDistance = CommonConfig.VEHICLE_NAVIGATION_MIN_DISTANCE.get();
        if (request.path().isEmpty()) {
            minDistance = Math.min(minDistance, request.desiredDistance());
        }
        if (minDistance <= 0.0D) {
            return true;
        }
        Optional<Vec3> targetPosition = this.navigationTargetPosition(request);
        if (targetPosition.isEmpty()) {
            return false;
        }
        return this.controller.position().distanceToSqr(targetPosition.get()) >= minDistance * minDistance;
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
        if (this.state == State.MANEUVER || this.state == State.RECOVER) {
            return;
        }
        Vec3 position = this.controller.position();
        if (this.isFireWindowHoldActive()) {
            this.stuckTicks = 0;
            this.lastPosition = position;
            VehicleAiLogger.debugEvery(this.mob, 20,
                    "Drive stuck check suppressed by fire-window hold ticks={} intent={} target={} velocity={}",
                    this.fireWindowHoldTicks,
                    request.intent(),
                    request.targetEntity().map(Entity::getId).orElse(-1),
                    this.controller.velocity());
            return;
        }
        if (!this.canUseManeuver(request)) {
            this.stuckTicks = 0;
            this.lastPosition = position;
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
        if (this.mode == DriveVehicleGoal.Mode.NAVIGATION && !this.isSignificantNavigationRequest(request)) {
            return false;
        }
        return this.driveTargetPosition(request).isPresent();
    }

    private void beginManeuver(DriveRequest request, String reason) {
        this.state = State.MANEUVER;
        this.path = null;
        this.replanTicks = 0;
        this.stuckTicks = 0;
        this.clearFireWindowHold();
        int previousTurnDirection = this.turnDirection;
        this.turnDirection = 0;
        this.maneuverTicks = 0;
        this.maneuverStepTicks = 0;
        this.maneuverNoProgressTicks = 0;
        this.lastRadarTick = this.mob.tickCount - MANEUVER_PROBE_INTERVAL;
        this.maneuverLastPosition = this.controller.position();
        this.maneuverLastYaw = this.controller.yaw();
        this.refreshManeuverClearance(true);

        double yawDiff = this.driveTargetPosition(request)
                .map(this::yawDiffTo)
                .orElse(0.0D);
        this.maneuverTurnDirection = selectManeuverTurnDirection(this.maneuverClearance, yawDiff, previousTurnDirection);
        this.maneuverStep = selectInitialManeuverStep(this.maneuverClearance, GroundSteering.shouldBrake(this.controller));
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
        if (this.maneuverTicks++ >= MANEUVER_MAX_TICKS) {
            this.finishManeuver("timeout");
            return;
        }

        this.refreshManeuverClearance(false);
        this.updateManeuverProgress();
        VehicleInput input = this.maneuverInput(request);
        VehicleAiLogger.debugEvery(this.mob, 10,
                "Drive maneuver step={} ticks={} stepTicks={} noProgress={} clearance={} input={} velocity={}",
                this.maneuverStep,
                this.maneuverTicks,
                this.maneuverStepTicks,
                this.maneuverNoProgressTicks,
                this.maneuverClearance,
                input,
                this.controller.velocity());
        if (input.equals(VehicleInput.NONE)) {
            this.controller.stopDriving(this.mob);
        } else {
            this.controller.drive(this.mob, input);
        }

        this.maneuverStepTicks++;
        this.advanceManeuverStepIfNeeded(request);
    }

    private VehicleInput maneuverInput(DriveRequest request) {
        return switch (this.maneuverStep) {
            case BRAKE -> GroundSteering.brake(this.controller);
            case BACK_STRAIGHT -> GroundSteering.maneuverBack(0);
            case BACK_TURN -> GroundSteering.maneuverBack(this.maneuverTurnDirection);
            case SWING_FORWARD -> {
                int turn = this.driveTargetPosition(request)
                        .map(target -> selectManeuverForwardTurn(this.maneuverTurnDirection, this.yawDiffTo(target), this.maneuverClearance))
                        .orElse(this.maneuverTurnDirection);
                yield GroundSteering.maneuverForward(turn, false);
            }
        };
    }

    private void advanceManeuverStepIfNeeded(DriveRequest request) {
        VehicleInput input = this.maneuverInput(request);
        if (shouldAbortManeuverPulse(input, this.maneuverClearance, this.maneuverStepTicks, this.maneuverNoProgressTicks)) {
            this.abortManeuverPulse(input);
            return;
        }

        switch (this.maneuverStep) {
            case BRAKE -> {
                if (this.maneuverStepTicks >= 3 || !GroundSteering.shouldBrake(this.controller)) {
                    this.changeManeuverStep(selectInitialManeuverStep(this.maneuverClearance, false), "brake complete");
                }
            }
            case BACK_STRAIGHT -> {
                GroundManeuverStep nextStep = nextBackStraightStep(this.maneuverClearance, this.maneuverStepTicks, this.maneuverNoProgressTicks);
                if (nextStep == GroundManeuverStep.SWING_FORWARD) {
                    this.changeManeuverStep(GroundManeuverStep.SWING_FORWARD, "front clearance preferred");
                } else if (nextStep == GroundManeuverStep.BACK_TURN && this.maneuverNoProgressTicks >= MANEUVER_PROBE_INTERVAL) {
                    this.changeManeuverStep(GroundManeuverStep.BACK_TURN, "straight back no progress");
                } else if (nextStep == GroundManeuverStep.BACK_TURN) {
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
                if (this.maneuverClearance.prefersBack()) {
                    this.changeManeuverStep(GroundManeuverStep.BACK_TURN, "back clearance preferred");
                } else if (this.maneuverStepTicks >= 16 && this.maneuverNoProgressTicks >= 10) {
                    this.reverseManeuverTurnDirection("forward no progress");
                } else {
                    boolean pulseExpired = this.maneuverStepTicks >= this.forwardPulseTicks();
                    boolean earlyExit = this.shouldFinishManeuverEarly(request);
                    if (pulseExpired || earlyExit) {
                        this.logManeuverExitDecision(request, pulseExpired, earlyExit);
                        Optional<Vec3> target = this.driveTargetPosition(request);
                        VehicleInput nextInput = target.map(value -> this.postManeuverInput(request, value)).orElse(VehicleInput.NONE);
                        if (target.isPresent() && canExitManeuverWithInput(nextInput, this.maneuverClearance)) {
                            this.finishManeuver("reacquire path");
                        }
                    }
                }
            }
        }
    }

    private void abortManeuverPulse(VehicleInput input) {
        if (input.forward()) {
            this.maneuverTurnDirection = input.left() ? -1 : input.right() ? 1 : this.maneuverClearance.openSideDirection(this.maneuverTurnDirection);
            this.changeManeuverStep(GroundManeuverStep.BACK_TURN, "forward pulse blocked");
        } else if (input.back()) {
            this.maneuverTurnDirection = input.left() ? 1 : input.right() ? -1 : this.maneuverClearance.openSideDirection(this.maneuverTurnDirection);
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
            return GroundSteering.steerRamToward(this.controller, target, this.turnDirection).input();
        }
        if (isTargetDistanceIntent(request.intent()) && this.retreatingFromTarget) {
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

    static boolean suppressStuckForFireWindowHold(int fireWindowHoldTicks) {
        return fireWindowHoldTicks > 0;
    }

    private void beginFireWindowHold(Entity target, GroundClearance clearance) {
        this.fireWindowHoldTicks = FIRE_WINDOW_HOLD_TICKS;
        this.stuckTicks = 0;
        this.lastPosition = this.controller.position();
        VehicleAiLogger.debugEvery(this.mob, 5,
                "Drive fire-window hold target={} ticks={} clearance={} velocity={}",
                target.getId(),
                this.fireWindowHoldTicks,
                clearance,
                this.controller.velocity());
    }

    private void clearFireWindowHold() {
        this.fireWindowHoldTicks = 0;
    }

    private boolean isFireWindowHoldActive() {
        return suppressStuckForFireWindowHold(this.fireWindowHoldTicks);
    }

    private boolean hasCurrentVehicleFireWindow(Entity target) {
        return VehicleAiMemory.getVehicleCurrentWeapons(this.mob, this.controller, target).stream()
                .anyMatch(CurrentWeaponSnapshot::canHoldCurrentFireWindow);
    }

    private boolean hasFireWindowLineOfSight(Entity target) {
        Entity aimEntity = target.getVehicle() == null ? target : target.getVehicle();
        return this.controller.hasLineOfSight(this.mob, target, aimEntity.getBoundingBox().getCenter());
    }

    private String vehicleWeaponWindowSummary(Entity target) {
        return VehicleAiMemory.getVehicleCurrentWeapons(this.mob, this.controller, target).stream()
                .map(GroundDriveStrategy::weaponSummary)
                .toList()
                .toString();
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
        this.maneuverStep = nextStep;
        this.maneuverStepTicks = 0;
        this.maneuverNoProgressTicks = 0;
        this.maneuverLastPosition = this.controller.position();
        this.maneuverLastYaw = this.controller.yaw();
        this.refreshManeuverClearance(true);
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
        this.refreshManeuverClearance(true);
    }

    private void finishManeuver(String reason) {
        VehicleAiLogger.debug(this.mob,
                "Drive maneuver finished reason={} ticks={} clearance={} velocity={} position={}",
                reason,
                this.maneuverTicks,
                this.maneuverClearance,
                this.controller.velocity(),
                this.controller.position());
        this.state = State.PLAN_PATH;
        this.path = null;
        this.replanTicks = 0;
        this.stuckTicks = 0;
        this.lastPosition = this.controller.position();
        this.resetManeuver(this.controller.position());
    }

    private void logManeuverExitDecision(DriveRequest request, boolean pulseExpired, boolean earlyExit) {
        Optional<Vec3> target = this.driveTargetPosition(request);
        VehicleInput nextInput = target.map(value -> this.postManeuverInput(request, value)).orElse(VehicleInput.NONE);
        double yawDiff = target.map(this::yawDiffTo).orElse(0.0D);
        boolean canExit = target.isPresent() && canExitManeuverWithInput(nextInput, this.maneuverClearance);
        VehicleAiLogger.debugEvery(this.mob, 5,
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
        if (!force && this.mob.tickCount - this.lastRadarTick < MANEUVER_PROBE_INTERVAL) {
            return;
        }
        GroundRadarSample sample = GroundObstacleRadar.sample(this.controller);
        this.maneuverClearance = sample.clearance();
        this.lastRadarTick = this.mob.tickCount;
        if (VehicleAiLogger.isDebugEnabled() && (force || this.mob.tickCount % 10 == 0)) {
            VehicleAiLogger.debug(this.mob,
                    "Drive maneuver radar vehicle={} yaw={} origin={} forward={} left={} sideOffset={} clearance={} frontRays=[center={},left={},right={}] backRays=[center={},left={},right={}] bbox={} velocity={}",
                    this.controller.entity().getId(),
                    this.controller.yaw(),
                    sample.origin(),
                    sample.forward(),
                    sample.left(),
                    sample.sideOffset(),
                    sample.clearance(),
                    sample.frontCenter(),
                    sample.frontLeft(),
                    sample.frontRight(),
                    sample.backCenter(),
                    sample.backLeft(),
                    sample.backRight(),
                    this.controller.boundingBox(),
                    this.controller.velocity());
        }
    }

    private void updateManeuverProgress() {
        if (this.maneuverStepTicks == 0 || this.maneuverStepTicks % MANEUVER_PROBE_INTERVAL != 0) {
            return;
        }
        Vec3 position = this.controller.position();
        double movedSqr = this.horizontalDistanceSqr(position, this.maneuverLastPosition);
        double yawDelta = Math.abs(net.minecraft.util.Mth.wrapDegrees(this.controller.yaw() - this.maneuverLastYaw));
        boolean progressByMove = movedSqr >= MANEUVER_PROGRESS_DISTANCE_SQR;
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

    private static String weaponSummary(CurrentWeaponSnapshot weapon) {
        return weapon.weaponName()
                + "{vehicle=" + weapon.vehicleEntityId()
                + ",seat=" + weapon.seatId()
                + ",weapon=" + weapon.weaponId()
                + ",aim=" + weapon.aimMode()
                + ",payload=" + weapon.payloadType()
                + ",selected=" + weapon.selected()
                + ",canFire=" + weapon.canFire()
                + ",fireReady=" + weapon.fireReady()
                + ",hasAmmo=" + weapon.hasAmmo()
                + ",reload=" + weapon.needsReload()
                + ",reloading=" + weapon.reloading()
                + ",cooling=" + weapon.coolingDown()
                + ",progress=" + weapon.fireProgress()
                + ",window=" + weapon.fireWindow().state()
                + ",yawError=" + weapon.fireWindow().yawErrorDegrees()
                + ",pitchError=" + weapon.fireWindow().pitchErrorDegrees()
                + "}";
    }

    private void resetManeuver(Vec3 position) {
        this.maneuverTicks = 0;
        this.maneuverStepTicks = 0;
        this.maneuverNoProgressTicks = 0;
        this.maneuverTurnDirection = 0;
        this.lastRadarTick = 0;
        this.maneuverStep = GroundManeuverStep.BRAKE;
        this.maneuverClearance = GroundClearance.open(GroundObstacleRadar.RANGE);
        this.maneuverLastPosition = position;
        this.maneuverLastYaw = this.controller == null ? 0.0F : this.controller.yaw();
    }

    static GroundManeuverStep selectInitialManeuverStep(GroundClearance clearance, boolean shouldBrake) {
        if (shouldBrake) {
            return GroundManeuverStep.BRAKE;
        }
        if (clearance.prefersBack()) {
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
        if (!clearance.prefersBack()) {
            return GroundManeuverStep.SWING_FORWARD;
        }
        if (stepTicks >= MANEUVER_STEP_MIN_TICKS && noProgressTicks >= MANEUVER_PROBE_INTERVAL) {
            return GroundManeuverStep.BACK_TURN;
        }
        return stepTicks >= 10 ? GroundManeuverStep.BACK_TURN : GroundManeuverStep.BACK_STRAIGHT;
    }

    static GroundManeuverStep nextBackTurnStep(GroundClearance clearance, int stepTicks, int noProgressTicks) {
        if (!clearance.prefersBack()) {
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

    static boolean shouldAbortManeuverPulse(VehicleInput input, GroundClearance clearance,
                                            int stepTicks, int noProgressTicks) {
        if (!input.forward() && !input.back()) {
            return false;
        }
        if (noProgressTicks >= MANEUVER_PROBE_INTERVAL) {
            return true;
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

    private boolean shouldRecoverFromBlockedPath(DriveRequest request) {
        if (!this.canUseManeuver(request)) {
            return false;
        }
        if (request.intent() == DriveIntent.RAM_TARGET && request.targetEntity().isPresent()) {
            Entity target = request.targetEntity().get();
            if (!target.isAlive() || this.shouldUseDirectRam(target)) {
                return false;
            }
        }
        return this.path == null || this.path.isDone() || this.path.getNodeCount() <= 1;
    }

    private boolean shouldRecoverFromExhaustedPath(DriveRequest request) {
        if (!this.canUseManeuver(request)) {
            return false;
        }
        if (request.intent() == DriveIntent.RAM_TARGET && request.targetEntity().isPresent()) {
            Entity target = request.targetEntity().get();
            return target.isAlive() && !this.shouldUseDirectRam(target);
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
                    || !this.dimensions.equals(other.dimensions)
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

    private enum State {
        IDLE,
        PLAN_PATH,
        FOLLOW_PATH,
        MANEUVER,
        RECOVER
    }
}
