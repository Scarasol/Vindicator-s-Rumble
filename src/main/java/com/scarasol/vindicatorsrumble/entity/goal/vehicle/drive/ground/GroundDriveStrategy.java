package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground;

import com.scarasol.vindicatorsrumble.api.vehicle.CurrentWeaponSnapshot;
import com.scarasol.vindicatorsrumble.api.vehicle.DriveIntent;
import com.scarasol.vindicatorsrumble.api.vehicle.DriveRequest;
import com.scarasol.vindicatorsrumble.api.vehicle.SeatHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleMovementType;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponHandle;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.DriveStrategy;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.DriveVehicleGoal;
import com.scarasol.vindicatorsrumble.util.VehicleAiLogger;
import com.scarasol.vindicatorsrumble.util.VehicleAiMemory;
import com.scarasol.vindicatorsrumble.util.VehicleAiTuning;
import com.scarasol.vindicatorsrumble.util.VehicleSmokeDefense;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public final class GroundDriveStrategy implements DriveStrategy {
    private final Mob mob;
    private final DriveVehicleGoal.Mode mode;
    private final GroundDriveControl control;
    private boolean closing;
    private boolean retreating;
    private int positioningTarget = -1;
    private Activation activation = Activation.DRIVE;

    public GroundDriveStrategy(Mob mob, DriveVehicleGoal.Mode mode) {
        this.mob = mob;
        this.mode = mode;
        this.control = new GroundDriveControl(mob);
    }

    @Override
    public String name() {
        return "ground";
    }

    @Override
    public boolean canUse(VehicleController controller, boolean starting) {
        if (!controller.usesGroundNavigation()) return false;
        if (this.mode == DriveVehicleGoal.Mode.COMBAT) {
            return this.combatTarget(this.combatRequest(controller)) != null;
        }
        if (this.combatTarget(DriveRequest.NONE) != null) return false;
        DriveRequest request = VehicleAiMemory.getNavigationDriveRequest(this.mob);
        if (request.intent() == DriveIntent.NONE || targetPosition(request).isEmpty()) return false;
        if (!starting) return true;
        double minimum = CommonConfig.VEHICLE_NAVIGATION_MIN_DISTANCE.get();
        if (request.path().isEmpty()) {
            minimum = Math.min(minimum,
                    VehicleAiTuning.navigationArrivalDistance(controller, request.desiredDistance()));
        }
        return horizontalDistanceSqr(controller.position(), targetPosition(request).orElseThrow())
                > minimum * minimum;
    }

    @Override
    public boolean canContinueToUse(VehicleController controller) {
        return this.canUse(controller, false);
    }

    @Override
    public void start(VehicleController controller, Activation activation) {
        this.activation = activation;
        this.resetPositioning();
        this.control.start(controller);
    }

    @Override
    public void stop(VehicleController controller) {
        this.resetPositioning();
        this.control.stop(controller);
    }

    @Override
    public void tick(VehicleController controller) {
        GroundDrivePlan plan = this.plan(controller);
        boolean smoke = VehicleSmokeDefense.requestDeployment(this.mob, controller);
        GroundDriveControl.Result result = this.control.execute(controller, plan, smoke);
        if (result == GroundDriveControl.Result.COMPLETE) {
            this.closing = false;
            this.retreating = false;
            if (plan.navigation()) VehicleAiMemory.clearNavigationDriveRequest(this.mob);
        }
        if (VehicleAiLogger.shouldLog(this.mob, 20)) {
            VehicleAiLogger.debug(this.mob,
                    "Ground decision action={} intent={} target={} closing={} retreating={} execution={}",
                    plan.action(), plan.request().intent(),
                    plan.request().targetEntity().map(Entity::getId).orElse(-1),
                    this.closing, this.retreating, result);
        }
    }

    private GroundDrivePlan plan(VehicleController controller) {
        if (controller.isWreck() || this.activation == Activation.DEFENSE_ONLY) {
            return this.hold(DriveRequest.NONE);
        }
        if (this.mode == DriveVehicleGoal.Mode.NAVIGATION) {
            DriveRequest request = VehicleAiMemory.getNavigationDriveRequest(this.mob);
            return targetPosition(request).isEmpty()
                    ? this.hold(request)
                    : new GroundDrivePlan(GroundDrivePlan.Action.FOLLOW, request, 0.0D);
        }
        DriveRequest request = this.combatRequest(controller);
        Entity target = this.combatTarget(request);
        if (target == null) return this.hold(DriveRequest.NONE);
        if (target.getId() != this.positioningTarget) {
            this.resetPositioning();
            this.positioningTarget = target.getId();
        }

        double inner = VehicleAiTuning.chaseStopDistance(this.mob);
        double outer = VehicleAiTuning.chaseStartDistance(this.mob);
        double distanceSqr = horizontalDistanceSqr(controller.position(), target.position());
        List<CurrentWeaponSnapshot> weapons = VehicleAiMemory.getVehicleCurrentWeapons(this.mob, controller, target);
        boolean currentFireWindow = weapons.stream().anyMatch(CurrentWeaponSnapshot::canHoldCurrentFireWindow);
        boolean fixedPosture = request.preferStableFire()
                && weapons.stream().anyMatch(CurrentWeaponSnapshot::canUseFixedWeaponAttackPosture);
        Entity aimEntity = target.getVehicle() == null ? target : target.getVehicle();
        boolean lineOfSight = controller.hasLineOfSight(this.mob, target, aimEntity.getBoundingBox().getCenter());
        DriveRequest chase = DriveRequest.chase(target, inner);
        boolean groundRam = controller.movementType() == VehicleMovementType.WHEEL
                || controller.movementType() == VehicleMovementType.TRACK;

        // Resolve infantry combat before any distance-band behavior is selected.
        boolean closeInfantry = target.getVehicle() == null && distanceSqr <= inner * inner;
        if (closeInfantry && currentFireWindow && lineOfSight) return this.hold(chase);
        if (groundRam && closeInfantry) {
            if (fixedPosture && lineOfSight) return this.aim(request, outer);
            return this.ram(target);
        }
        if (fixedPosture && lineOfSight) return this.aim(request, outer);
        boolean fallbackRam = groundRam && !(target.getVehicle() == null && currentFireWindow && lineOfSight)
                && this.shouldUseRamming(controller);
        if (fallbackRam) return this.ram(target);
        this.retreating = (!groundRam || target.getVehicle() != null)
                && shouldRetreatFromChase(distanceSqr, inner, outer, this.retreating);
        if (this.retreating) {
            this.closing = false;
            return new GroundDrivePlan(GroundDrivePlan.Action.RETREAT, chase, outer);
        }
        if (!lineOfSight) {
            this.closing = true;
            return new GroundDrivePlan(GroundDrivePlan.Action.FOLLOW, chase, outer);
        }
        this.closing = shouldCloseToChase(distanceSqr, inner, outer, this.closing);
        return this.closing
                ? new GroundDrivePlan(GroundDrivePlan.Action.APPROACH, chase, outer)
                : this.hold(chase);
    }

    private GroundDrivePlan hold(DriveRequest request) {
        this.closing = false;
        this.retreating = false;
        return new GroundDrivePlan(GroundDrivePlan.Action.HOLD, request, 0.0D);
    }

    private GroundDrivePlan aim(DriveRequest request, double outer) {
        this.closing = false;
        this.retreating = false;
        return new GroundDrivePlan(GroundDrivePlan.Action.AIM, request, outer);
    }

    private GroundDrivePlan ram(Entity target) {
        this.closing = false;
        this.retreating = false;
        return new GroundDrivePlan(GroundDrivePlan.Action.RAM, DriveRequest.ram(target), 0.0D);
    }

    private DriveRequest combatRequest(VehicleController controller) {
        DriveRequest own = VehicleAiMemory.getDriveRequest(this.mob);
        DriveRequest shared = VehicleAiMemory.getVehicleDriveRequest(this.mob, controller);
        return shared.intent() == DriveIntent.SUPPORT_FIRE && shared.preferStableFire()
                && this.combatTarget(shared) != null && own.intent() != DriveIntent.SUPPORT_FIRE
                ? shared : own;
    }

    private Entity combatTarget(DriveRequest request) {
        return VehicleAiTuning.vehicleDriveTarget(this.mob, request);
    }

    private boolean shouldUseRamming(VehicleController controller) {
        Optional<SeatHandle> seat = controller.seatOf(this.mob);
        if (seat.isEmpty() || !seat.get().canDrive()) return false;
        boolean armedDriver = seat.get().hasWeapon();
        boolean occupiedWeaponSeat = armedDriver || (controller.passengers().size() > 1
                && controller.seats().stream().anyMatch(value -> value.hasWeapon() && value.occupied()));
        boolean needsAmmo = CommonConfig.VEHICLE_AI_NEEDS_AMMO.get();
        boolean driverHasAmmo = armedDriver && !needsAmmo;
        if (armedDriver && needsAmmo) {
            for (WeaponHandle weapon : controller.weapons(this.mob)) {
                if (controller.weaponStatus(this.mob, weapon).hasAmmo()) {
                    driverHasAmmo = true;
                    break;
                }
            }
        }
        boolean vehicleHasAmmo = !needsAmmo || armedDriver || !occupiedWeaponSeat
                || controller.hasAnyWeaponAmmo(this.mob);
        return shouldUseRamming(controller.movementType(), true, armedDriver,
                driverHasAmmo, occupiedWeaponSeat, needsAmmo, vehicleHasAmmo);
    }

    public static boolean shouldUseRamming(VehicleMovementType type, Optional<SeatHandle> seat,
                                           List<SeatHandle> seats) {
        return shouldUseRamming(type, seat, seats, false, true);
    }

    public static boolean shouldUseRamming(VehicleMovementType type, Optional<SeatHandle> seat,
                                           List<SeatHandle> seats, boolean needsAmmo, boolean hasAmmo) {
        return seat.isPresent() && shouldUseRamming(type, seat.get().canDrive(), seat.get().hasWeapon(),
                seats.stream().anyMatch(value -> value.hasWeapon() && value.occupied()), needsAmmo, hasAmmo);
    }

    public static boolean shouldUseRamming(VehicleMovementType type, boolean canDrive,
                                           boolean armedDriver, boolean occupiedWeaponSeat) {
        return shouldUseRamming(type, canDrive, armedDriver, occupiedWeaponSeat, false, true);
    }

    public static boolean shouldUseRamming(VehicleMovementType type, boolean canDrive,
                                           boolean armedDriver, boolean occupiedWeaponSeat,
                                           boolean needsAmmo, boolean hasAmmo) {
        return shouldUseRamming(type, canDrive, armedDriver, armedDriver && (!needsAmmo || hasAmmo),
                occupiedWeaponSeat, needsAmmo, hasAmmo);
    }

    public static boolean shouldUseRamming(VehicleMovementType type, boolean canDrive,
                                           boolean armedDriver, boolean driverHasAmmo,
                                           boolean occupiedWeaponSeat, boolean needsAmmo, boolean vehicleHasAmmo) {
        return (type == VehicleMovementType.WHEEL || type == VehicleMovementType.TRACK) && canDrive
                && ((!armedDriver && (!occupiedWeaponSeat || needsAmmo && !vehicleHasAmmo))
                || needsAmmo && armedDriver && !driverHasAmmo);
    }

    static boolean shouldRetreatFromChase(double distanceSqr, double inner, double outer, boolean retreating) {
        double threshold = retreating ? outer : inner;
        return inner > 0.0D && outer > inner && distanceSqr < threshold * threshold;
    }

    static boolean shouldCloseToChase(double distanceSqr, double inner, double outer, boolean closing) {
        double threshold = closing ? inner : outer;
        return inner > 0.0D && outer > inner && distanceSqr > threshold * threshold;
    }

    private void resetPositioning() {
        this.closing = false;
        this.retreating = false;
        this.positioningTarget = -1;
    }

    private static Optional<Vec3> targetPosition(DriveRequest request) {
        return request.targetPosition().isPresent() ? request.targetPosition()
                : request.targetEntity().filter(Entity::isAlive).map(Entity::position);
    }

    private static double horizontalDistanceSqr(Vec3 from, Vec3 to) {
        return from.subtract(to).horizontalDistanceSqr();
    }
}
