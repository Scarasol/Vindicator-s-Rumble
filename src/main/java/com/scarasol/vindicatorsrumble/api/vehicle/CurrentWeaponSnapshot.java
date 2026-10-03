package com.scarasol.vindicatorsrumble.api.vehicle;

import java.util.UUID;

public record CurrentWeaponSnapshot(
        UUID vehicleUuid,
        int vehicleEntityId,
        int seatId,
        UUID targetUuid,
        int targetEntityId,
        int weaponId,
        String weaponName,
        WeaponAimMode aimMode,
        WeaponPayloadType payloadType,
        WeaponFireMode fireMode,
        boolean selected,
        boolean canFire,
        boolean needsReload,
        boolean reloading,
        boolean coolingDown,
        boolean hasAmmo,
        int heat,
        boolean fireReady,
        double fireProgress,
        WeaponFireWindow fireWindow
) {
    public static CurrentWeaponSnapshot of(UUID vehicleUuid, int vehicleEntityId, int seatId,
                                           UUID targetUuid, int targetEntityId,
                                           WeaponHandle weapon, WeaponStatus status) {
        return of(vehicleUuid, vehicleEntityId, seatId, targetUuid, targetEntityId,
                weapon, status, status.canFire() ? 1.0D : 0.0D);
    }

    public static CurrentWeaponSnapshot of(UUID vehicleUuid, int vehicleEntityId, int seatId,
                                           UUID targetUuid, int targetEntityId,
                                           WeaponHandle weapon, WeaponStatus status,
                                           double fireProgress) {
        double clampedFireProgress = Math.max(0.0D, Math.min(1.0D, fireProgress));
        return new CurrentWeaponSnapshot(
                vehicleUuid,
                vehicleEntityId,
                seatId,
                targetUuid,
                targetEntityId,
                weapon.id(),
                weapon.name(),
                weapon.aimMode(),
                weapon.payloadType(),
                weapon.fireMode(),
                status.selected(),
                status.canFire(),
                status.needsReload(),
                status.reloading(),
                status.coolingDown(),
                status.hasAmmo(),
                status.heat(),
                status.canFire() && clampedFireProgress >= 1.0D,
                clampedFireProgress,
                WeaponFireWindow.unknown());
    }

    public static CurrentWeaponSnapshot of(UUID vehicleUuid, int vehicleEntityId, int seatId,
                                           UUID targetUuid, int targetEntityId,
                                           WeaponHandle weapon, WeaponStatus status,
                                           double fireProgress, WeaponFireWindow fireWindow) {
        double clampedFireProgress = Math.max(0.0D, Math.min(1.0D, fireProgress));
        return new CurrentWeaponSnapshot(
                vehicleUuid,
                vehicleEntityId,
                seatId,
                targetUuid,
                targetEntityId,
                weapon.id(),
                weapon.name(),
                weapon.aimMode(),
                weapon.payloadType(),
                weapon.fireMode(),
                status.selected(),
                status.canFire(),
                status.needsReload(),
                status.reloading(),
                status.coolingDown(),
                status.hasAmmo(),
                status.heat(),
                status.canFire() && clampedFireProgress >= 1.0D,
                clampedFireProgress,
                fireWindow == null ? WeaponFireWindow.unknown() : fireWindow);
    }

    public boolean matchesContext(UUID vehicleUuid, int seatId, UUID targetUuid) {
        return this.vehicleUuid.equals(vehicleUuid)
                && this.seatId == seatId
                && this.targetUuid.equals(targetUuid);
    }

    public boolean matchesVehicleTarget(UUID vehicleUuid, UUID targetUuid) {
        return this.vehicleUuid.equals(vehicleUuid)
                && this.targetUuid.equals(targetUuid);
    }

    public boolean requiresVehicleAim() {
        return this.aimMode == WeaponAimMode.VEHICLE_FIXED;
    }

    public boolean canUseFixedWeaponAttackPosture() {
        return this.selected
                && this.requiresVehicleAim()
                && this.canFire
                && this.hasAmmo
                && !this.needsReload
                && !this.reloading
                && !this.coolingDown;
    }

    public boolean canStartFixedWeaponAttackPosture() {
        return this.canUseFixedWeaponAttackPosture() && this.fireReady;
    }

    public boolean hasCurrentFireWindow() {
        return this.fireWindow.hasCurrentWindow();
    }

    public boolean hasPotentialFireWindow() {
        return this.fireWindow.hasPotentialWindow();
    }

    public boolean canHoldCurrentFireWindow() {
        return this.selected
                && this.hasAmmo
                && !this.needsReload
                && !this.reloading
                && !this.coolingDown
                && this.hasCurrentFireWindow();
    }
}
