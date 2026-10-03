package com.scarasol.vindicatorsrumble.entity.goal.vehicle.weapon;

import com.scarasol.vindicatorsrumble.api.vehicle.DriveRequest;
import com.scarasol.vindicatorsrumble.api.vehicle.CurrentWeaponSnapshot;
import com.scarasol.vindicatorsrumble.api.vehicle.SeatHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleMovementType;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponAimMode;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponFireWindow;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponStatus;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.common.VehicleAiGoal;
import com.scarasol.vindicatorsrumble.util.AllegianceUtil;
import com.scarasol.vindicatorsrumble.util.VehicleAiLogger;
import com.scarasol.vindicatorsrumble.util.VehicleAiMemory;
import com.scarasol.vindicatorsrumble.util.VehicleAiTuning;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public class VehicleWeaponGoal extends Goal implements VehicleAiGoal {
    private static final double GROUND_CADENCE_DISTANCE_SCALE = 0.25D;
    private static final double FLYING_TURRET_CADENCE_DISTANCE_SCALE = 0.125D;
    private static final double FLYING_FIXED_CADENCE_DISTANCE_SCALE = 0.0D;

    private final Mob mob;
    private VehicleController controller;
    private WeaponHandle selectedWeapon;
    private int cooldown;
    private int aimTargetId = -1;
    private int aimWeaponId = -1;
    private int alignedTicks;
    private double fireProgress = 1.0D;
    private boolean stopRequested;

    public VehicleWeaponGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (!CommonConfig.ENABLE_VEHICLE_AI.get()) {
            VehicleAiLogger.debugEvery(this.mob, 40, "Weapon canUse=false: vehicle AI disabled");
            return false;
        }
        if (!this.mob.isPassenger()) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            VehicleAiLogger.debugEvery(this.mob, 40, "Weapon canUse=false: no target");
            return false;
        }
        if (!VehicleAiTuning.isValidVehicleCombatTarget(this.mob, target)) {
            VehicleAiLogger.debugEvery(this.mob, 40,
                    "Weapon canUse=false: target {} invalid for combat effectiveFollowRange={} distanceSqr={} alive={} sameSide={}",
                    target.getId(),
                    VehicleAiTuning.followRange(this.mob),
                    VehicleAiTuning.targetDistanceToSqr(this.mob, target),
                    target.isAlive(),
                    AllegianceUtil.isSameSide(this.mob, target));
            return false;
        }
        Optional<VehicleController> current = VehicleControllers.forMob(this.mob);
        if (current.isEmpty()) {
            VehicleAiLogger.debugEvery(this.mob, 40, "Weapon canUse=false: passenger vehicle has no compatible controller");
            return false;
        }
        List<WeaponHandle> weapons = current.get().weapons(this.mob);
        if (weapons.isEmpty()) {
            VehicleAiLogger.debugEvery(this.mob, 40, "Weapon canUse=false: no weapons for current seat on vehicle {}", current.get().entity().getId());
            return false;
        }
        this.controller = current.get();
        this.selectedWeapon = this.chooseWeapon(weapons, target).orElse(null);
        if (this.selectedWeapon == null) {
            VehicleAiLogger.debugEvery(this.mob, 40, "Weapon canUse=false: no weapon with ammo on vehicle {}", this.controller.entity().getId());
            this.controller = null;
            return false;
        }
        this.stopRequested = false;
        VehicleAiLogger.debug(this.mob, "Weapon goal selected {} payload={} on vehicle {}", this.selectedWeapon.name(), this.selectedWeapon.payloadType(), this.controller.entity().getId());
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        return shouldContinueLifecycle(
                this.stopRequested,
                this.controller != null && this.controller.isValid(),
                this.mob.isPassenger(),
                VehicleAiTuning.isValidVehicleCombatTarget(this.mob, target),
                this.controller != null && !this.controller.weapons(this.mob).isEmpty());
    }

    @Override
    public void stop() {
        if (this.controller != null) {
            this.controller.stopWeaponControl(this.mob);
        }
        this.controller = null;
        this.selectedWeapon = null;
        this.resetAimSettling();
        this.fireProgress = 1.0D;
        this.stopRequested = false;
        VehicleAiMemory.clearDriveRequest(this.mob);
        VehicleAiMemory.clearCurrentWeapon(this.mob);
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (this.controller == null || this.selectedWeapon == null || target == null) {
            VehicleAiLogger.debugEvery(this.mob, 20,
                    "Weapon requesting stop: missing state controller={} selectedWeapon={} target={}",
                    this.controller != null,
                    this.selectedWeapon == null ? "null" : this.selectedWeapon.name(),
                    target == null ? -1 : target.getId());
            this.requestStop();
            return;
        }
        if (!VehicleAiTuning.isValidVehicleCombatTarget(this.mob, target)) {
            VehicleAiLogger.debugEvery(this.mob, 20,
                    "Weapon stopping: target {} invalid for combat effectiveFollowRange={} distanceSqr={}",
                    target.getId(),
                    VehicleAiTuning.followRange(this.mob),
                    VehicleAiTuning.targetDistanceToSqr(this.mob, target));
            this.requestStop();
            return;
        }

        List<WeaponHandle> weapons = this.controller.weapons(this.mob);
        Optional<WeaponHandle> preferredWeapon = this.chooseWeapon(weapons, target);
        if (preferredWeapon.isEmpty()) {
            VehicleAiLogger.debugEvery(this.mob, 20, "Weapon stopping: no weapon with ammo on vehicle {}", this.controller.entity().getId());
            this.requestStop();
            return;
        }
        if (!preferredWeapon.get().equals(this.selectedWeapon)) {
            VehicleAiLogger.debug(this.mob,
                    "Weapon switching {}({}) -> {}({}) target={} heavyTarget={}",
                    this.selectedWeapon.name(),
                    this.selectedWeapon.payloadType(),
                    preferredWeapon.get().name(),
                    preferredWeapon.get().payloadType(),
                    target.getId(),
                    prefersHeavyWeapon(target));
            this.selectedWeapon = preferredWeapon.get();
        }
        this.trackAimSettling(target, this.selectedWeapon);

        boolean selected = this.controller.selectWeapon(this.mob, this.selectedWeapon);
        WeaponStatus status = this.controller.weaponStatus(this.mob, this.selectedWeapon);
        double actualDistance = this.mob.distanceTo(target);
        double cadenceDistance = cadenceDistance(actualDistance, this.controller.movementType(), this.selectedWeapon);
        double fireStep = 0.0D;
        if (shouldAdvanceFireCadence(status) && this.fireProgress < 1.0D) {
            fireStep = this.selectedWeapon.fireStep(cadenceDistance, false);
            this.fireProgress = advanceFireProgress(this.fireProgress, fireStep);
        }
        Vec3 aimPoint = this.controller.aimPoint(this.mob, target, this.selectedWeapon);
        WeaponFireWindow fireWindow = this.controller.fireWindow(this.mob, target, aimPoint, this.selectedWeapon);
        int seatId = this.controller.seatOf(this.mob).map(SeatHandle::id).orElse(-1);
        CurrentWeaponSnapshot currentWeapon = CurrentWeaponSnapshot.of(
                this.controller.entity().getUUID(),
                this.controller.entity().getId(),
                seatId,
                target.getUUID(),
                target.getId(),
                this.selectedWeapon,
                status,
                this.fireProgress,
                fireWindow);
        VehicleAiMemory.setCurrentWeapon(this.mob, currentWeapon, 10);
        VehicleAiLogger.debugEvery(this.mob, 20,
                "Weapon status selectedResult={} selected={} canFire={} needsReload={} reloading={} coolingDown={} hasAmmo={} heat={} fireMode={} rpm={} actionCycleTicks={} fireProgress={} fireReady={} fireWindow={} yawError={} pitchError={} estimatedMiss={}",
                selected,
                status.selected(),
                status.canFire(),
                status.needsReload(),
                status.reloading(),
                status.coolingDown(),
                status.hasAmmo(),
                status.heat(),
                this.selectedWeapon.fireMode(),
                this.selectedWeapon.rpm(),
                this.selectedWeapon.actionCycleTicks(),
                this.fireProgress,
                currentWeapon.fireReady(),
                fireWindow.state(),
                fireWindow.yawErrorDegrees(),
                fireWindow.pitchErrorDegrees(),
                fireWindow.estimatedMiss());

        boolean fixedAim = this.selectedWeapon.requiresVehicleAim();
        boolean stableFire = shouldRequestStableFire(currentWeapon);
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        boolean aimed = this.controller.aimAt(this.mob, target, aimPoint);
        VehicleAiLogger.debugEvery(this.mob, 20,
                "Weapon aiming at target {} aimPoint={} result={} fixedAim={} stableFire={} hasAmmo={}",
                target.getId(),
                aimPoint,
                aimed,
                fixedAim,
                stableFire,
                status.hasAmmo());
        if (!aimed) {
            this.alignedTicks = 0;
            VehicleAiLogger.debugEvery(this.mob, 10, "Weapon waiting for aim control before firing {}", this.selectedWeapon.name());
            return;
        }
        DriveRequest driveRequest = stableFire
                ? DriveRequest.supportFire(target, VehicleAiTuning.chaseDistance(this.mob))
                : DriveRequest.chase(target, VehicleAiTuning.chaseDistance(this.mob));
        VehicleAiMemory.setDriveRequest(this.mob, driveRequest, 10);
        if (stableFire) {
            VehicleAiMemory.setVehicleCurrentWeapon(this.controller.entity(), currentWeapon, 10);
            VehicleAiMemory.setVehicleDriveRequest(this.controller.entity(), driveRequest, 10);
        }

        if (this.cooldown > 0) {
            this.cooldown--;
            return;
        }

        if (status.needsReload()) {
            VehicleAiLogger.debug(this.mob, "Reloading vehicle weapon {}", this.selectedWeapon.name());
            this.controller.reload(this.mob);
            this.cooldown = 20;
            this.fireProgress = 0.0D;
            return;
        }
        if (shouldPrepareWeapon(status)) {
            if (!this.controller.hasLineOfSight(this.mob, target, aimPoint)) {
                VehicleAiLogger.debugEvery(this.mob, 10, "Weapon waiting for line of sight before firing {}", this.selectedWeapon.name());
                return;
            }
            if (!this.controller.isAimedAt(this.mob, target, aimPoint, this.selectedWeapon)) {
                this.alignedTicks = 0;
                VehicleAiLogger.debugEvery(this.mob, 10, "Weapon waiting for aim alignment before firing {}", this.selectedWeapon.name());
                return;
            }
            this.alignedTicks++;
            int requiredAlignedTicks = requiredAimSettleTicks(this.selectedWeapon);
            if (this.alignedTicks < requiredAlignedTicks) {
                VehicleAiLogger.debugEvery(this.mob, 5,
                        "Weapon waiting for aim visual settle before firing {} alignedTicks={}/{}",
                        this.selectedWeapon.name(),
                        this.alignedTicks,
                        requiredAlignedTicks);
                return;
            }
            if (this.fireProgress < 1.0D) {
                VehicleAiLogger.debugEvery(this.mob, 5,
                        "Weapon waiting for fire cadence before firing {} progress={} step={} mode={} rpm={} distance={} cadenceDistance={}",
                        this.selectedWeapon.name(),
                        this.fireProgress,
                        fireStep,
                        this.selectedWeapon.fireMode(),
                        this.selectedWeapon.rpm(),
                        actualDistance,
                        cadenceDistance);
                return;
            }
            if (!shouldFireWeapon(status)) {
                VehicleAiLogger.debugEvery(this.mob, 5,
                        "Weapon cadence ready but controller cannot fire {} canFire={} reloading={} coolingDown={} hasAmmo={}",
                        this.selectedWeapon.name(),
                        status.canFire(),
                        status.reloading(),
                        status.coolingDown(),
                        status.hasAmmo());
                return;
            }
            VehicleAiLogger.debug(this.mob, "Firing vehicle weapon {} at {}", this.selectedWeapon.name(), target.getId());
            boolean fired = this.controller.fire(this.mob, target, aimPoint, this.selectedWeapon);
            if (fired) {
                this.fireProgress = 0.0D;
            }
            VehicleAiLogger.debug(this.mob, "Fire call result={} for weapon {}", fired, this.selectedWeapon.name());
        } else {
            VehicleAiLogger.debugEvery(this.mob, 20, "Weapon cannot fire this tick");
        }
    }

    static boolean shouldRequestStableFire(CurrentWeaponSnapshot currentWeapon) {
        return currentWeapon != null && currentWeapon.canUseFixedWeaponAttackPosture();
    }

    static boolean shouldContinueLifecycle(boolean stopRequested, boolean controllerReady, boolean passenger, boolean validTarget, boolean hasWeapons) {
        return !stopRequested && controllerReady && passenger && validTarget && hasWeapons;
    }

    static boolean shouldPrepareWeapon(WeaponStatus status) {
        return status.selected() && status.hasAmmo() && !status.reloading() && !status.coolingDown();
    }

    static boolean shouldFireWeapon(WeaponStatus status) {
        return status.canFire() && status.hasAmmo() && !status.coolingDown();
    }

    static boolean shouldAdvanceFireCadence(WeaponStatus status) {
        return status.selected() && status.hasAmmo() && !status.reloading() && !status.coolingDown();
    }

    static double advanceFireProgress(double currentProgress, double fireStep) {
        return Math.min(1.0D, Math.max(0.0D, currentProgress) + Math.max(0.0D, fireStep));
    }

    static int requiredAimSettleTicks(WeaponHandle weapon) {
        return weapon != null && weapon.aimMode() == WeaponAimMode.INDEPENDENT ? 3 : 0;
    }

    static double cadenceDistance(double actualDistance, VehicleMovementType movementType, WeaponHandle weapon) {
        double distance = Math.max(0.0D, actualDistance);
        if (movementType == VehicleMovementType.HELICOPTER || movementType == VehicleMovementType.AIRCRAFT) {
            double scale = weapon != null && weapon.requiresVehicleAim()
                    ? FLYING_FIXED_CADENCE_DISTANCE_SCALE
                    : FLYING_TURRET_CADENCE_DISTANCE_SCALE;
            return distance * scale;
        }
        if (movementType == VehicleMovementType.WHEEL
                || movementType == VehicleMovementType.TRACK
                || movementType == VehicleMovementType.SHIP) {
            return distance * GROUND_CADENCE_DISTANCE_SCALE;
        }
        return distance;
    }

    private void trackAimSettling(LivingEntity target, WeaponHandle weapon) {
        int targetId = target == null ? -1 : target.getId();
        int weaponId = weapon == null ? -1 : weapon.id();
        if (targetId != this.aimTargetId || weaponId != this.aimWeaponId) {
            boolean weaponChanged = weaponId != this.aimWeaponId;
            this.aimTargetId = targetId;
            this.aimWeaponId = weaponId;
            this.alignedTicks = 0;
            if (weaponChanged) {
                this.fireProgress = 1.0D;
            }
        }
    }

    private void resetAimSettling() {
        this.aimTargetId = -1;
        this.aimWeaponId = -1;
        this.alignedTicks = 0;
        this.fireProgress = 1.0D;
    }

    private void requestStop() {
        this.stopRequested = true;
        VehicleAiMemory.clearDriveRequest(this.mob);
        VehicleAiMemory.clearCurrentWeapon(this.mob);
    }

    private Optional<WeaponHandle> chooseWeapon(List<WeaponHandle> weapons, LivingEntity target) {
        List<WeaponStatus> statuses = new ArrayList<>(weapons.size());
        List<WeaponFireWindow> fireWindows = new ArrayList<>(weapons.size());
        for (WeaponHandle weapon : weapons) {
            statuses.add(this.controller.weaponStatus(this.mob, weapon));
            Vec3 aimPoint = this.controller.aimPoint(this.mob, target, weapon);
            fireWindows.add(this.controller.fireWindow(this.mob, target, aimPoint, weapon));
        }
        return chooseWeapon(weapons, statuses, fireWindows, prefersHeavyWeapon(target), this.selectedWeapon);
    }

    static Optional<WeaponHandle> chooseWeapon(List<WeaponHandle> weapons, List<WeaponStatus> statuses, boolean preferHeavy, WeaponHandle currentWeapon) {
        return chooseWeapon(weapons, statuses, unknownFireWindows(Math.min(weapons.size(), statuses.size())), preferHeavy, currentWeapon);
    }

    static Optional<WeaponHandle> chooseWeapon(List<WeaponHandle> weapons, List<WeaponStatus> statuses,
                                               List<WeaponFireWindow> fireWindows,
                                               boolean preferHeavy, WeaponHandle currentWeapon) {
        WeaponHandle best = null;
        int bestScore = Integer.MAX_VALUE;
        int count = Math.min(Math.min(weapons.size(), statuses.size()), fireWindows.size());
        for (int i = 0; i < count; i++) {
            WeaponHandle weapon = weapons.get(i);
            WeaponStatus status = statuses.get(i);
            WeaponFireWindow fireWindow = fireWindows.get(i);
            if (!isSelectableWeaponStatus(status)) {
                continue;
            }
            if (!isSelectableFireWindow(fireWindow)) {
                continue;
            }
            int score = payloadScore(weapon, preferHeavy) * 10_000
                    + fireReadinessScore(status) * 1_000
                    + fireWindowScore(fireWindow) * 250
                    + sameWeaponScore(weapon, currentWeapon) * 100
                    + weapon.priority();
            if (score < bestScore) {
                best = weapon;
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }

    private static List<WeaponFireWindow> unknownFireWindows(int count) {
        List<WeaponFireWindow> windows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            windows.add(WeaponFireWindow.unknown());
        }
        return windows;
    }

    static boolean isSelectableWeaponStatus(WeaponStatus status) {
        return status.hasAmmo() && !status.coolingDown();
    }

    static boolean isSelectableFireWindow(WeaponFireWindow fireWindow) {
        return fireWindow == null || !fireWindow.isImpossible();
    }

    static boolean prefersHeavyWeapon(LivingEntity target) {
        return target != null && (target.getVehicle() != null || target.getHealth() > 50.0F);
    }

    private static int payloadScore(WeaponHandle weapon, boolean preferHeavy) {
        if (weapon.payloadType().isHeavyTargetPreferred()) {
            return preferHeavy ? 0 : 2;
        }
        if (weapon.payloadType().isSoftTargetPreferred()) {
            return preferHeavy ? 2 : 0;
        }
        return switch (weapon.payloadType()) {
            case UNKNOWN -> 1;
            case UTILITY -> 3;
            default -> 2;
        };
    }

    private static int sameWeaponScore(WeaponHandle weapon, WeaponHandle currentWeapon) {
        return currentWeapon != null && weapon.id() == currentWeapon.id() ? 0 : 1;
    }

    private static int fireReadinessScore(WeaponStatus status) {
        return status.canFire() ? 0 : 1;
    }

    private static int fireWindowScore(WeaponFireWindow fireWindow) {
        if (fireWindow == null) {
            return 1;
        }
        return switch (fireWindow.state()) {
            case READY_NOW -> 0;
            case IN_WINDOW, UNKNOWN -> 1;
            case NEEDS_POSTURE -> 2;
            case IMPOSSIBLE -> 100;
        };
    }
}
