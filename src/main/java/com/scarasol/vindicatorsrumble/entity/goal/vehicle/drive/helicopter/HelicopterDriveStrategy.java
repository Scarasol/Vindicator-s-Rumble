package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.helicopter;

import com.scarasol.vindicatorsrumble.api.vehicle.CurrentWeaponSnapshot;
import com.scarasol.vindicatorsrumble.api.vehicle.DriveIntent;
import com.scarasol.vindicatorsrumble.api.vehicle.DriveRequest;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleFlightInput;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleMovementType;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleThreats;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponHandle;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.DriveStrategy;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.DriveVehicleGoal;
import com.scarasol.vindicatorsrumble.util.VehicleAiLogger;
import com.scarasol.vindicatorsrumble.util.VehicleAiMemory;
import com.scarasol.vindicatorsrumble.util.VehicleAiTuning;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public final class HelicopterDriveStrategy implements DriveStrategy {
    private static final int HELICOPTER_FIXED_ATTACK_RECOVERY_TICKS = 100;
    private static final int DECOY_RELEASE_INTERVAL_TICKS = 20;

    private final Mob mob;
    private final DriveVehicleGoal.Mode mode;
    private final HelicopterFlightControl flightControl = new HelicopterFlightControl();
    private final HelicopterFlightPlanner flightPlanner = new HelicopterFlightPlanner();
    private VehicleController controller;
    private boolean helicopterFixedAttackActive;
    private int helicopterFixedAttackRecoveryTicks;
    private Vec3 idleHoverAnchor;
    private long nextDecoyTick;

    public HelicopterDriveStrategy(Mob mob, DriveVehicleGoal.Mode mode) {
        this.mob = mob;
        this.mode = mode;
    }

    @Override
    public String name() {
        return "helicopter";
    }

    @Override
    public boolean canUse(VehicleController controller, boolean starting) {
        this.controller = controller;
        return this.isHelicopter(controller)
                && this.shouldUseHelicopterDrive(starting);
    }

    @Override
    public boolean canContinueToUse(VehicleController controller) {
        this.controller = controller;
        return this.isHelicopter(controller)
                && this.shouldUseHelicopterDrive(false);
    }

    @Override
    public void start(VehicleController controller) {
        this.controller = controller;
        this.flightControl.reset();
        this.flightPlanner.reset();
        this.helicopterFixedAttackActive = false;
        this.helicopterFixedAttackRecoveryTicks = 0;
        this.idleHoverAnchor = null;
        this.nextDecoyTick = 0L;
    }

    @Override
    public void stop(VehicleController controller) {
        this.controller = controller;
        this.flightControl.reset();
        this.flightPlanner.reset();
        this.helicopterFixedAttackActive = false;
        this.helicopterFixedAttackRecoveryTicks = 0;
        this.idleHoverAnchor = null;
        this.nextDecoyTick = 0L;
    }

    @Override
    public void tick(VehicleController controller) {
        this.controller = controller;
        if (controller.isWreck()) {
            if (this.helicopterFixedAttackActive) {
                this.finishFixedAttack();
            }
            VehicleAiLogger.debugEvery(this.mob, 40, "Helicopter drive holding recovery controls because vehicle {} is wrecked", controller.entity().getId());
            controller.controlFlight(this.mob, HelicopterFlightControl.neutralInput());
            return;
        }

        DriveRequest request = this.currentDriveRequest();
        Vec3 navigationPoint = request.targetPosition().orElseGet(() ->
                request.targetEntity().filter(Entity::isAlive).map(Entity::position).orElse(null));
        Entity target = this.helicopterTarget(request);
        if (target == null && navigationPoint == null && !this.helicopterNeedsStabilizing()) {
            if (this.helicopterFixedAttackActive) {
                this.finishFixedAttack();
            }
            VehicleAiLogger.debugEvery(this.mob, 40, "Helicopter drive stopping: no target and no flight stabilization needed");
            controller.stopDriving(this.mob);
            return;
        }

        HelicopterFlightPlanner.AircraftState aircraftState = this.helicopterState();
        VehicleThreats.Threat threat = controller.threat().orElse(null);
        Entity pressure = threat != null ? threat.source() : this.approachingAirTarget(target, aircraftState);
        Entity airFirePressure = this.airFirePressure(target, aircraftState);
        Optional<CurrentWeaponSnapshot> fixedWeapon = this.currentFixedWeapon(target);
        boolean fixedWeaponRequested = request.preferStableFire();
        boolean fixedWeaponUsable = fixedWeaponRequested
                && fixedWeapon.map(CurrentWeaponSnapshot::canUseFixedWeaponAttackPosture).orElse(false);
        boolean fixedWeaponReady = fixedWeaponRequested
                && fixedWeapon.map(CurrentWeaponSnapshot::canStartFixedWeaponAttackPosture).orElse(false);
        boolean fixedWeaponAttackRecovering = target != null
                && fixedWeaponUsable
                && this.helicopterFixedAttackRecoveryTicks > 0;
        boolean stableFire = target != null && (this.helicopterFixedAttackActive
                || fixedWeaponUsable && (fixedWeaponReady || fixedWeaponAttackRecovering));
        boolean fixedWeaponAimed = target != null && fixedWeaponUsable
                && this.fixedWeaponAimedAt(target, fixedWeapon.get());
        Vec3 targetPoint = this.targetPointForPlan(target, stableFire);
        double recoveryFloorY = target == null ? Double.NaN
                : HelicopterFlightPlanner.recoveryFloorY(controller, aircraftState, target);
        double hoverHeight = CommonConfig.HELICOPTER_HOVER_HEIGHT.get();
        boolean pullOutBlocksRecovery = targetPoint != null && (stableFire || pressure != null)
                && HelicopterFlightPlanner.needsPassPullOut(aircraftState);
        HelicopterFlightPlanner.Plan plan = this.flightPlanner.resolvePlan(
                controller,
                target,
                aircraftState,
                hoverHeight,
                VehicleAiTuning.followRange(this.mob),
                environment -> pullOutBlocksRecovery
                        || this.altitudeBlocksVisibilityRecovery(
                        aircraftState, recoveryFloorY, environment),
                (environment, recoveryWaypoint) -> this.mode == DriveVehicleGoal.Mode.NAVIGATION
                        && navigationPoint != null
                        ? this.flightPlanner.navigationPlan(controller, aircraftState, navigationPoint, hoverHeight,
                        request.desiredDistance(), environment)
                        : this.helicopterPlan(
                        aircraftState,
                        target,
                        targetPoint,
                        recoveryFloorY,
                        stableFire,
                        fixedWeaponAimed,
                        this.helicopterFixedAttackActive,
                        fixedWeaponAttackRecovering,
                        pressure,
                        airFirePressure,
                        threat != null && threat.stage() == VehicleThreats.Stage.INCOMING,
                        environment,
                        recoveryWaypoint));
        HelicopterEnvironment environment = plan.environment();
        long gameTime = controller.level().getGameTime();
        boolean decoy = threat != null && threat.stage() != VehicleThreats.Stage.ACQUIRING
                && gameTime >= this.nextDecoyTick && controller.canDeployDecoy();
        if (decoy) this.nextDecoyTick = gameTime + DECOY_RELEASE_INTERVAL_TICKS;
        VehicleFlightInput input = this.flightControl.command(aircraftState, plan.flightRequest(), environment, decoy);
        this.updateFixedAttackState(target, aircraftState, plan);
        double targetHorizontalDistance = target == null ? 0.0D
                : horizontalDistance(aircraftState.position(), targetCenter(target));
        double combatDistance = CommonConfig.HELICOPTER_COMBAT_DISTANCE.get();
        boolean retreating = target != null
                && targetHorizontalDistance < combatDistance - HelicopterFlightPlanner.INNER_COMBAT_TOLERANCE;
        boolean closing = target != null
                && targetHorizontalDistance > combatDistance + HelicopterFlightPlanner.OUTER_COMBAT_TOLERANCE;
        VehicleAiLogger.debugEvery(this.mob, retreating || pressure != null || airFirePressure != null
                        || plan.phase() == HelicopterFlightPlanner.Phase.EVASIVE_PASS ? 1 : 10,
                "Helicopter flight tick={} phase={} target={} desired={} aim={} cruise={} stopAtDestination={} retreating={} closing={} stableFire={} fixedWeaponReady={} fixedWeaponAimed={} attackActive={} attackRecover={} targetH={} altitude={} pitch={} roll={} yaw={} hover={} power={} los={} frontBlocked={} elevatedFrontClear={} terrainY={} clearances=[front={},elevated={}] pressure={} airFirePressure={} threat={} input={}",
                this.mob.tickCount,
                plan.phase(),
                target == null ? -1 : target.getId(),
                plan.desiredPosition(),
                plan.aimPoint(),
                plan.cruiseSpeed(),
                plan.stopAtDestination(),
                retreating,
                closing,
                stableFire,
                fixedWeaponReady,
                fixedWeaponAimed,
                this.helicopterFixedAttackActive,
                this.helicopterFixedAttackRecoveryTicks,
                targetHorizontalDistance,
                aircraftState.altitude(),
                aircraftState.pitch(),
                aircraftState.roll(),
                aircraftState.yaw(),
                aircraftState.hoverMode(),
                aircraftState.power(),
                environment.hasLineOfSight(),
                environment.frontBlocked(),
                environment.elevatedFrontClear(),
                environment.terrainPreferredY(),
                environment.frontClearance(),
                environment.elevatedFrontClearance(),
                pressure == null ? -1 : pressure.getId(),
                airFirePressure == null ? -1 : airFirePressure.getId(),
                threat == null ? null : threat.stage(),
                input);
        controller.controlFlight(this.mob, input);
    }

    private Entity approachingAirTarget(Entity target, HelicopterFlightPlanner.AircraftState state) {
        if (target == null) return null;
        Entity opponent = target.getRootVehicle();
        if (opponent.onGround() || !VehicleControllers.create(opponent)
                .map(VehicleController::supportsFlightControl).orElse(false)) return null;
        Vec3 separation = state.position().subtract(opponent.position()).multiply(1.0D, 0.0D, 1.0D);
        double distance = separation.length();
        if (distance < 1.0E-4D) return opponent;
        double closingSpeed = -state.velocity().subtract(opponent.getDeltaMovement()).dot(separation) / distance;
        double innerDistance = CommonConfig.HELICOPTER_COMBAT_DISTANCE.get()
                - HelicopterFlightPlanner.INNER_COMBAT_TOLERANCE;
        return closingSpeed > 0.0D && distance - closingSpeed * 8.0D
                - this.controller.estimatedStabilizedHorizontalStoppingDistance() < innerDistance
                ? opponent : null;
    }

    private Entity airFirePressure(Entity target, HelicopterFlightPlanner.AircraftState state) {
        if (target == null) return null;
        Entity opponent = target.getRootVehicle();
        if (opponent.onGround() || !VehicleControllers.create(opponent)
                .map(VehicleController::supportsFlightControl).orElse(false)) return null;
        Vec3 separation = this.controller.boundingBox().getCenter()
                .subtract(opponent.getBoundingBox().getCenter());
        double distance = separation.horizontalDistance();
        if (distance < 1.0E-4D || opponent.getViewVector(1.0F).dot(separation.normalize()) < 0.95D) return null;
        Vec3 relativeVelocity = state.velocity().subtract(opponent.getDeltaMovement())
                .multiply(1.0D, 0.0D, 1.0D);
        double radialSpeed = relativeVelocity.dot(separation.multiply(1.0D, 0.0D, 1.0D)) / distance;
        double transverseSpeed = Math.sqrt(Math.max(0.0D, relativeVelocity.lengthSqr() - radialSpeed * radialSpeed));
        // Nose pressure is only positional evidence, not a claim that a weapon is firing.
        return transverseSpeed * 8.0D < distance * 0.08D ? opponent : null;
    }

    private boolean shouldUseHelicopterDrive(boolean starting) {
        if (!this.isHelicopter(this.controller)) {
            return false;
        }
        boolean combat = this.validCombatTarget() != null
                || VehicleAiMemory.getDriveRequest(this.mob).targetEntity().filter(Entity::isAlive).isPresent();
        boolean navigation = VehicleAiMemory.getNavigationDriveRequest(this.mob).intent() == DriveIntent.MOVE_TO;
        return this.mode == DriveVehicleGoal.Mode.NAVIGATION
                ? !combat && navigation
                : combat || !navigation && this.helicopterNeedsStabilizing();
    }

    private boolean helicopterNeedsStabilizing() {
        return this.isHelicopter(this.controller)
                && (this.controller.engineStarted()
                || this.controller.power() > 0.01F
                || this.controller.altitudeAboveGround(HelicopterFlightControl.ALTITUDE_SAMPLE) > 3.0D
                || Math.abs(this.controller.velocity().y) > 0.03D);
    }

    private Entity helicopterTarget(DriveRequest request) {
        if (this.mode == DriveVehicleGoal.Mode.NAVIGATION) {
            return null;
        }
        if (request.targetEntity().isPresent() && request.targetEntity().get().isAlive()) {
            return request.targetEntity().get();
        }
        return this.validCombatTarget();
    }

    private Vec3 targetPointForPlan(Entity target, boolean preferStableFire) {
        if (target == null) {
            return null;
        }
        Entity aimEntity = target.getVehicle() == null ? target : target.getVehicle();
        return preferStableFire
                ? this.controller.aimPoint(this.mob, target)
                : aimEntity.getBoundingBox().getCenter();
    }

    private boolean altitudeBlocksVisibilityRecovery(HelicopterFlightPlanner.AircraftState aircraftState,
                                                      double recoveryFloorY,
                                                      HelicopterEnvironment environment) {
        boolean needsRecovery = !environment.hasLineOfSight() || environment.frontBlocked();
        return needsRecovery && HelicopterFlightPlanner.needsAltitudeCorrection(
                aircraftState, recoveryFloorY);
    }

    private HelicopterFlightPlanner.Plan helicopterPlan(HelicopterFlightPlanner.AircraftState aircraftState,
                                                         Entity target, Vec3 targetCenter, double recoveryFloorY,
                                                         boolean preferStableFire, boolean fixedWeaponAimed,
                                                         boolean fixedWeaponAttackActive,
                                                         boolean fixedWeaponAttackRecovering,
                                                         Entity pressure,
                                                         Entity airFirePressure,
                                                         boolean incoming,
                                                         HelicopterEnvironment environment,
                                                         Vec3 recoveryWaypoint) {
        if (targetCenter != null) {
            this.idleHoverAnchor = null;
        }
        double hoverHeight = CommonConfig.HELICOPTER_HOVER_HEIGHT.get();
        double desiredY = this.flightPlanner.desiredY(
                this.controller, aircraftState, target, hoverHeight, environment);
        HelicopterFlightPlanner.Plan plan = this.chooseHelicopterPlan(
                aircraftState,
                target,
                targetCenter,
                desiredY,
                recoveryFloorY,
                hoverHeight,
                CommonConfig.HELICOPTER_COMBAT_DISTANCE.get(),
                preferStableFire,
                fixedWeaponAimed,
                fixedWeaponAttackActive,
                fixedWeaponAttackRecovering,
                pressure,
                airFirePressure,
                incoming,
                environment,
                recoveryWaypoint);
        if (targetCenter == null && this.idleHoverAnchor == null) {
            this.idleHoverAnchor = plan.desiredPosition();
        }
        return targetCenter == null && this.idleHoverAnchor != null
                 ? new HelicopterFlightPlanner.Plan(
                 new Vec3(this.idleHoverAnchor.x, plan.desiredPosition().y, this.idleHoverAnchor.z),
                 plan.aimPoint(),
                 plan.cruiseSpeed(),
                 plan.stopAtDestination(),
                 plan.hasTarget(),
                plan.phase())
                 : plan;
    }

    private HelicopterFlightPlanner.Plan chooseHelicopterPlan(HelicopterFlightPlanner.AircraftState aircraftState,
                                                              Entity target,
                                                              Vec3 targetCenter,
                                                              double desiredY,
                                                              double recoveryFloorY,
                                                              double hoverHeight,
                                                              double combatDistance,
                                                              boolean preferStableFire,
                                                              boolean fixedWeaponAimed,
                                                              boolean fixedWeaponAttackActive,
                                                              boolean fixedWeaponAttackRecovering,
                                                              Entity pressure,
                                                              Entity airFirePressure,
                                                              boolean incoming,
                                                              HelicopterEnvironment environment,
                                                              Vec3 recoveryWaypoint) {
        if (targetCenter != null && (preferStableFire || pressure != null)
                && HelicopterFlightPlanner.needsPassPullOut(aircraftState)) {
            return HelicopterFlightPlanner.pullOutPlan(aircraftState, targetCenter, hoverHeight);
        }
        if (targetCenter == null) {
            if (HelicopterFlightPlanner.needsAltitudeCorrection(aircraftState, desiredY)) {
                return HelicopterFlightPlanner.altitudeCorrectionPlan(aircraftState, null, desiredY);
            }
            return HelicopterFlightPlanner.idlePlan(aircraftState, desiredY);
        }

        boolean needsVisibilityRecovery = !environment.hasLineOfSight() || environment.frontBlocked();
        if (needsVisibilityRecovery) {
            if (this.altitudeBlocksVisibilityRecovery(aircraftState, recoveryFloorY, environment)) {
                return HelicopterFlightPlanner.altitudeCorrectionPlan(
                        aircraftState, targetCenter, recoveryFloorY);
            }
            return HelicopterFlightPlanner.visibilityRecoveryPlan(aircraftState, targetCenter, desiredY, recoveryWaypoint);
        }

        HelicopterFlightPlanner.Plan combatPlan = pressure == null ? this.visibleCombatPlan(
                aircraftState, target, targetCenter, desiredY, hoverHeight, combatDistance,
                preferStableFire, fixedWeaponAimed, fixedWeaponAttackActive, fixedWeaponAttackRecovering,
                environment) : null;
        boolean idleCombat = combatPlan != null && !fixedWeaponAttackActive
                && (combatPlan.phase() == HelicopterFlightPlanner.Phase.COMBAT_HOVER
                || combatPlan.phase() == HelicopterFlightPlanner.Phase.FIRE_HOLD);
        HelicopterFlightPlanner.Plan evasive = this.flightPlanner.evasionPlan(
                this.controller, aircraftState, pressure != null ? pressure : idleCombat ? airFirePressure : null,
                incoming, target, targetCenter, desiredY,
                combatDistance, VehicleAiTuning.followRange(this.mob), environment);
        if (evasive != null) return evasive;
        return combatPlan != null ? combatPlan : this.visibleCombatPlan(
                aircraftState, target, targetCenter, desiredY, hoverHeight, combatDistance,
                preferStableFire, fixedWeaponAimed, fixedWeaponAttackActive, fixedWeaponAttackRecovering,
                environment);
    }

    private HelicopterFlightPlanner.Plan visibleCombatPlan(
            HelicopterFlightPlanner.AircraftState aircraftState, Entity target, Vec3 targetCenter,
            double desiredY, double hoverHeight, double combatDistance, boolean preferStableFire,
            boolean fixedWeaponAimed, boolean fixedWeaponAttackActive, boolean fixedWeaponAttackRecovering,
            HelicopterEnvironment environment) {
        int targetId = target.getId();
        Entity targetVehicle = target.getRootVehicle();
        if (horizontalDistance(aircraftState.position(), targetVehicle.position())
                > combatDistance + HelicopterFlightPlanner.OUTER_COMBAT_TOLERANCE
                && !targetVehicle.onGround() && VehicleControllers.create(targetVehicle)
                .map(VehicleController::supportsFlightControl).orElse(false)) {
            return this.combatPositionPlan(
                    aircraftState, targetId, targetCenter, desiredY, hoverHeight, combatDistance, environment);
        }

        if (fixedWeaponAttackActive) {
            return HelicopterFlightPlanner.attackRunPlan(targetCenter, desiredY);
        }

        if (preferStableFire) {
            if (fixedWeaponAttackRecovering) {
                return this.combatPositionPlan(
                        aircraftState, targetId, targetCenter, desiredY, hoverHeight, combatDistance, environment);
            }
            if (fixedWeaponAimed) {
                return HelicopterFlightPlanner.fireHoldPlan(aircraftState, targetCenter, desiredY);
            }
            if (!HelicopterFlightPlanner.fixedWeaponAttackYawAligned(aircraftState, targetCenter)) {
                return this.combatPositionPlan(
                        aircraftState, targetId, targetCenter, desiredY, hoverHeight, combatDistance, environment);
            }
            return HelicopterFlightPlanner.attackRunPlan(targetCenter, desiredY);
        }

        return this.combatPositionPlan(
                aircraftState, targetId, targetCenter, desiredY, hoverHeight, combatDistance, environment);
    }

    private HelicopterFlightPlanner.Plan combatPositionPlan(
            HelicopterFlightPlanner.AircraftState state, int targetId, Vec3 targetCenter,
            double desiredY, double hoverHeight, double combatDistance,
            HelicopterEnvironment environment) {
        return this.flightPlanner.combatPositionPlan(
                this.controller, targetId, state, targetCenter, desiredY, hoverHeight,
                combatDistance, VehicleAiTuning.followRange(this.mob), environment);
    }

    private void updateFixedAttackState(Entity target, HelicopterFlightPlanner.AircraftState state,
                                        HelicopterFlightPlanner.Plan plan) {
        if (this.helicopterFixedAttackActive) {
            if (target == null
                    || plan.phase() != HelicopterFlightPlanner.Phase.ATTACK_RUN
                    || HelicopterFlightPlanner.reachedOrPassed(state, targetCenter(target), false)) {
                this.finishFixedAttack();
                return;
            }
        } else if (plan.phase() == HelicopterFlightPlanner.Phase.ATTACK_RUN) {
            this.helicopterFixedAttackActive = true;
        }
        this.tickFixedAttackRecovery();
    }

    private void finishFixedAttack() {
        this.helicopterFixedAttackActive = false;
        this.helicopterFixedAttackRecoveryTicks = HELICOPTER_FIXED_ATTACK_RECOVERY_TICKS;
    }

    private void tickFixedAttackRecovery() {
        if (this.helicopterFixedAttackRecoveryTicks > 0) {
            this.helicopterFixedAttackRecoveryTicks--;
        }
    }

    private Optional<CurrentWeaponSnapshot> currentFixedWeapon(Entity target) {
        return VehicleAiMemory.getCurrentWeapon(this.mob, this.controller, target)
                .filter(CurrentWeaponSnapshot::requiresVehicleAim);
    }

    private boolean fixedWeaponAimedAt(Entity target, CurrentWeaponSnapshot snapshot) {
        if (this.controller == null || target == null) {
            return false;
        }
        List<WeaponHandle> weapons = this.controller.weapons(this.mob);
        for (WeaponHandle weapon : weapons) {
            if (weapon.id() != snapshot.weaponId()) {
                continue;
            }
            if (!weapon.requiresVehicleAim()) {
                VehicleAiLogger.debugEvery(this.mob, 20,
                        "Helicopter fixed weapon aim gate target={} weapon={} ignored because current weapon no longer requires vehicle aim",
                        target.getId(),
                        weapon.name());
                return false;
            }
            Vec3 aimPoint = this.controller.aimPoint(this.mob, target, weapon);
            boolean aimed = this.controller.isAimedAt(this.mob, target, aimPoint, weapon);
            VehicleAiLogger.debugEvery(this.mob, 10,
                    "Helicopter fixed weapon aim gate target={} weapon={} weaponId={} aimPoint={} aimed={} hasAmmo={} reloading={} coolingDown={}",
                    target.getId(),
                    weapon.name(),
                    weapon.id(),
                    aimPoint,
                    aimed,
                    snapshot.hasAmmo(),
                    snapshot.reloading(),
                    snapshot.coolingDown());
            return aimed;
        }
        VehicleAiLogger.debugEvery(this.mob, 40,
                "Helicopter fixed weapon aim gate target={} missing current weapon id={}",
                target.getId(),
                snapshot.weaponId());
        return false;
    }

    private HelicopterFlightPlanner.AircraftState helicopterState() {
        return new HelicopterFlightPlanner.AircraftState(
                this.controller.position(),
                this.controller.velocity(),
                this.controller.yaw(),
                this.controller.pitch(),
                this.controller.roll(),
                this.controller.hoverMode(),
                this.controller.engineStarted(),
                this.controller.power(),
                this.controller.altitudeAboveGround(HelicopterFlightControl.ALTITUDE_SAMPLE));
    }

    private boolean isHelicopter(VehicleController controller) {
        return controller != null
                && controller.movementType() == VehicleMovementType.HELICOPTER
                && controller.supportsFlightControl();
    }

    private DriveRequest currentDriveRequest() {
        if (this.mode == DriveVehicleGoal.Mode.NAVIGATION) {
            return VehicleAiMemory.getNavigationDriveRequest(this.mob);
        }
        DriveRequest request = VehicleAiMemory.getDriveRequest(this.mob);
        if (request.intent() != DriveIntent.NONE) {
            return request;
        }
        LivingEntity target = this.validCombatTarget();
        if (target != null) {
            return DriveRequest.chase(target, VehicleAiTuning.chaseDistance(this.mob));
        }
        VehicleAiLogger.debugEvery(this.mob, 40, "Drive has no target and no memory request");
        return DriveRequest.NONE;
    }

    private LivingEntity validCombatTarget() {
        LivingEntity target = this.mob.getTarget();
        return VehicleAiTuning.isValidVehicleDriveTarget(this.mob, target) ? target : null;
    }

    private static Vec3 targetCenter(Entity target) {
        Entity aimEntity = target.getVehicle() == null ? target : target.getVehicle();
        return aimEntity.getBoundingBox().getCenter();
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
