package com.scarasol.vindicatorsrumble.entity.goal.vehicle.weapon;

import com.scarasol.vindicatorsrumble.api.vehicle.CurrentWeaponSnapshot;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleMovementType;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponAimMode;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponFireWindow;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponFireMode;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponPayloadType;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleWeaponGoalTest {
    @Test
    void fixedWeaponRequestsStableFireOnlyWhenCurrentFixedWeaponIsReady() {
        assertTrue(VehicleWeaponGoal.shouldRequestStableFire(
                snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET, WeaponStatus.READY)));
        assertTrue(VehicleWeaponGoal.shouldRequestStableFire(
                snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.SHELL, WeaponStatus.READY)));
        assertTrue(VehicleWeaponGoal.shouldRequestStableFire(
                snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.MISSILE, WeaponStatus.READY)));
        assertFalse(VehicleWeaponGoal.shouldRequestStableFire(
                snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET,
                        new WeaponStatus(true, true, false, false, false, false, 0))));
        assertFalse(VehicleWeaponGoal.shouldRequestStableFire(
                snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET,
                        new WeaponStatus(false, true, false, false, false, true, 0))));
        assertFalse(VehicleWeaponGoal.shouldRequestStableFire(
                snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET,
                        new WeaponStatus(true, true, true, false, false, true, 0))));
        assertFalse(VehicleWeaponGoal.shouldRequestStableFire(
                snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET,
                        new WeaponStatus(true, true, false, true, false, true, 0))));
        assertFalse(VehicleWeaponGoal.shouldRequestStableFire(
                snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET,
                        new WeaponStatus(true, true, false, false, true, true, 0))));
        assertFalse(VehicleWeaponGoal.shouldRequestStableFire(
                snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET,
                        new WeaponStatus(true, false, false, false, false, true, 0))));
        assertTrue(VehicleWeaponGoal.shouldRequestStableFire(
                snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET, WeaponStatus.READY, 0.5D)));
    }

    @Test
    void nonFixedWeaponNeverRequestsStableFire() {
        assertFalse(VehicleWeaponGoal.shouldRequestStableFire(
                snapshot(WeaponAimMode.INDEPENDENT, WeaponPayloadType.ROCKET, WeaponStatus.READY)));
    }

    @Test
    void lifecycleStopRequestPreventsFakeRunningGoal() {
        assertTrue(VehicleWeaponGoal.shouldContinueLifecycle(false, true, true, true, true));
        assertFalse(VehicleWeaponGoal.shouldContinueLifecycle(true, true, true, true, true));
        assertFalse(VehicleWeaponGoal.shouldContinueLifecycle(false, false, true, true, true));
        assertFalse(VehicleWeaponGoal.shouldContinueLifecycle(false, true, false, true, true));
        assertFalse(VehicleWeaponGoal.shouldContinueLifecycle(false, true, true, false, true));
        assertFalse(VehicleWeaponGoal.shouldContinueLifecycle(false, true, true, true, false));
    }

    @Test
    void firingRequiresBothShootPermissionAndAmmo() {
        assertTrue(VehicleWeaponGoal.shouldFireWeapon(WeaponStatus.READY));
        assertFalse(VehicleWeaponGoal.shouldFireWeapon(
                new WeaponStatus(true, true, false, false, false, false, 0)));
        assertFalse(VehicleWeaponGoal.shouldFireWeapon(
                new WeaponStatus(true, false, false, false, false, true, 0)));
        assertFalse(VehicleWeaponGoal.shouldFireWeapon(
                new WeaponStatus(true, true, false, false, true, true, 12)));
    }

    @Test
    void cadencePreparationDoesNotRequireImmediateShootPermission() {
        assertTrue(VehicleWeaponGoal.shouldPrepareWeapon(
                new WeaponStatus(true, false, false, false, false, true, 0)));
        assertFalse(VehicleWeaponGoal.shouldPrepareWeapon(
                new WeaponStatus(false, false, false, false, false, true, 0)));
        assertFalse(VehicleWeaponGoal.shouldPrepareWeapon(
                new WeaponStatus(true, false, false, true, false, true, 0)));
        assertFalse(VehicleWeaponGoal.shouldPrepareWeapon(
                new WeaponStatus(true, false, false, false, true, true, 12)));
        assertFalse(VehicleWeaponGoal.shouldPrepareWeapon(
                new WeaponStatus(true, false, false, false, false, false, 0)));
    }

    @Test
    void independentWeaponsNeedShortAimSettleForVisualTurretSync() {
        assertEquals(3, VehicleWeaponGoal.requiredAimSettleTicks(
                new WeaponHandle(0, "turret", 0.0D, 96.0D, 0, WeaponAimMode.INDEPENDENT, WeaponPayloadType.BULLET)));
        assertEquals(0, VehicleWeaponGoal.requiredAimSettleTicks(
                new WeaponHandle(1, "fixed", 0.0D, 96.0D, 1, WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET)));
    }

    @Test
    void automaticWeaponCadenceUsesRpmOverTwelveHundredTicksPerMinute() {
        WeaponHandle weapon = new WeaponHandle(0, "auto", 0.0D, 96.0D, 0,
                WeaponAimMode.INDEPENDENT, WeaponPayloadType.BULLET, WeaponFireMode.AUTOMATIC, 600.0D, 0.0D);

        assertEquals(0.5D, weapon.fireStep(8.0D, false), 1.0E-6D);
        assertEquals(0.5D, weapon.fireStep(32.0D, false), 1.0E-6D);
    }

    @Test
    void semiAutomaticWeaponCadenceUsesPillagersGunDistanceScaling() {
        WeaponHandle weapon = new WeaponHandle(0, "semi", 0.0D, 96.0D, 0,
                WeaponAimMode.INDEPENDENT, WeaponPayloadType.SHELL, WeaponFireMode.SEMI_AUTOMATIC, 600.0D, 0.0D);

        assertEquals(0.25D, weapon.fireStep(8.0D, false), 1.0E-6D);
        assertEquals(0.125D, weapon.fireStep(32.0D, false), 1.0E-6D);
        assertEquals(0.25D, weapon.fireStep(32.0D, true), 1.0E-6D);
    }

    @Test
    void actionCycleLimitsWeaponCadence() {
        WeaponHandle weapon = new WeaponHandle(0, "bolt", 0.0D, 96.0D, 0,
                WeaponAimMode.INDEPENDENT, WeaponPayloadType.SHELL, WeaponFireMode.AUTOMATIC, 1200.0D, 5.0D);

        assertEquals(0.2D, weapon.fireStep(8.0D, false), 1.0E-6D);
    }

    @Test
    void currentWeaponSnapshotAllowsFixedWeaponAttackPostureOnlyWhenUsable() {
        assertTrue(snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET, WeaponStatus.READY).canUseFixedWeaponAttackPosture());
        assertTrue(snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.SHELL, WeaponStatus.READY).canUseFixedWeaponAttackPosture());
        assertTrue(snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.MISSILE, WeaponStatus.READY).canUseFixedWeaponAttackPosture());
        assertFalse(snapshot(WeaponAimMode.INDEPENDENT, WeaponPayloadType.ROCKET, WeaponStatus.READY).canUseFixedWeaponAttackPosture());
        assertFalse(snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET,
                new WeaponStatus(true, false, false, false, false, true, 0)).canUseFixedWeaponAttackPosture());
        assertTrue(snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET,
                WeaponStatus.READY, 0.5D).canUseFixedWeaponAttackPosture());
        assertFalse(snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET,
                new WeaponStatus(false, true, false, false, false, true, 0)).canUseFixedWeaponAttackPosture());
        assertFalse(snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET,
                new WeaponStatus(true, true, true, false, false, true, 0)).canUseFixedWeaponAttackPosture());
        assertFalse(snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET,
                new WeaponStatus(true, true, false, true, false, true, 0)).canUseFixedWeaponAttackPosture());
        assertFalse(snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET,
                new WeaponStatus(true, true, false, false, true, true, 0)).canUseFixedWeaponAttackPosture());
        assertFalse(snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET,
                new WeaponStatus(true, true, false, false, false, false, 0)).canUseFixedWeaponAttackPosture());
    }

    @Test
    void currentWeaponSnapshotStartsFixedAttackOnlyWhenFireReady() {
        assertTrue(snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.MISSILE,
                WeaponStatus.READY, 1.0D).canStartFixedWeaponAttackPosture());
        assertFalse(snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.MISSILE,
                WeaponStatus.READY, 0.5D).canStartFixedWeaponAttackPosture());
        assertFalse(snapshot(WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.MISSILE,
                new WeaponStatus(true, false, false, false, false, true, 0), 1.0D).canStartFixedWeaponAttackPosture());
    }

    @Test
    void currentWeaponSnapshotTracksFireWindowSeparatelyFromFixedAim() {
        CurrentWeaponSnapshot readyWindow = CurrentWeaponSnapshot.of(UUID.randomUUID(), 4, 0, UUID.randomUUID(), 9,
                new WeaponHandle(0, "weapon", 0.0D, 96.0D, 0, WeaponAimMode.INDEPENDENT, WeaponPayloadType.BULLET),
                WeaponStatus.READY,
                1.0D,
                WeaponFireWindow.inWindow(0.0D, 0.0D, 0.0D));
        CurrentWeaponSnapshot postureWindow = CurrentWeaponSnapshot.of(UUID.randomUUID(), 4, 0, UUID.randomUUID(), 9,
                new WeaponHandle(1, "weapon", 0.0D, 96.0D, 0, WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET),
                WeaponStatus.READY,
                1.0D,
                WeaponFireWindow.needsPosture(20.0D, 0.0D, 2.0D));

        assertTrue(readyWindow.hasCurrentFireWindow());
        assertTrue(readyWindow.canHoldCurrentFireWindow());
        assertFalse(postureWindow.hasCurrentFireWindow());
        assertTrue(postureWindow.hasPotentialFireWindow());
        assertTrue(postureWindow.canStartFixedWeaponAttackPosture());
    }

    @Test
    void currentWeaponSnapshotMatchesVehicleSeatAndTargetContext() {
        UUID vehicle = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        CurrentWeaponSnapshot snapshot = CurrentWeaponSnapshot.of(vehicle, 4, 1, target, 9,
                new WeaponHandle(2, "fixed", 0.0D, 96.0D, 0, WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET),
                WeaponStatus.READY);

        assertTrue(snapshot.matchesContext(vehicle, 1, target));
        assertFalse(snapshot.matchesContext(UUID.randomUUID(), 1, target));
        assertFalse(snapshot.matchesContext(vehicle, 2, target));
        assertFalse(snapshot.matchesContext(vehicle, 1, UUID.randomUUID()));
    }

    @Test
    void currentWeaponSnapshotCanMatchVehicleTargetAcrossSeats() {
        UUID vehicle = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        CurrentWeaponSnapshot snapshot = CurrentWeaponSnapshot.of(vehicle, 4, 2, target, 9,
                new WeaponHandle(2, "fixed", 0.0D, 96.0D, 0, WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.ROCKET),
                WeaponStatus.READY);

        assertTrue(snapshot.matchesVehicleTarget(vehicle, target));
        assertFalse(snapshot.matchesVehicleTarget(UUID.randomUUID(), target));
        assertFalse(snapshot.matchesVehicleTarget(vehicle, UUID.randomUUID()));
    }

    @Test
    void groundVehicleCadenceDistanceWeakensSemiAutoPenalty() {
        WeaponHandle weapon = new WeaponHandle(0, "semi", 0.0D, 96.0D, 0,
                WeaponAimMode.INDEPENDENT, WeaponPayloadType.SHELL, WeaponFireMode.SEMI_AUTOMATIC, 600.0D, 0.0D);
        double cadenceDistance = VehicleWeaponGoal.cadenceDistance(96.0D, VehicleMovementType.WHEEL, weapon);

        assertEquals(24.0D, cadenceDistance, 1.0E-6D);
        assertEquals(1.0D / 6.0D, weapon.fireStep(cadenceDistance, false), 1.0E-6D);
    }

    @Test
    void helicopterFixedWeaponCadenceDistanceRemovesDistancePenalty() {
        WeaponHandle weapon = new WeaponHandle(0, "fixed", 0.0D, 96.0D, 0,
                WeaponAimMode.VEHICLE_FIXED, WeaponPayloadType.MISSILE, WeaponFireMode.SEMI_AUTOMATIC, 600.0D, 0.0D);
        double cadenceDistance = VehicleWeaponGoal.cadenceDistance(96.0D, VehicleMovementType.HELICOPTER, weapon);

        assertEquals(0.0D, cadenceDistance, 1.0E-6D);
        assertEquals(0.25D, weapon.fireStep(cadenceDistance, false), 1.0E-6D);
    }

    @Test
    void helicopterTurretCadenceDistanceKeepsOnlyLightPenalty() {
        WeaponHandle weapon = new WeaponHandle(0, "turret", 0.0D, 96.0D, 0,
                WeaponAimMode.INDEPENDENT, WeaponPayloadType.SHELL, WeaponFireMode.SEMI_AUTOMATIC, 600.0D, 0.0D);
        double cadenceDistance = VehicleWeaponGoal.cadenceDistance(96.0D, VehicleMovementType.HELICOPTER, weapon);

        assertEquals(12.0D, cadenceDistance, 1.0E-6D);
        assertEquals(0.25D, weapon.fireStep(cadenceDistance, false), 1.0E-6D);
    }

    @Test
    void fireCadenceProgressAdvancesAndClampsByElapsedTicks() {
        assertTrue(VehicleWeaponGoal.shouldAdvanceFireCadence(WeaponStatus.READY));
        assertFalse(VehicleWeaponGoal.shouldAdvanceFireCadence(
                new WeaponStatus(true, false, false, true, false, true, 0)));
        assertFalse(VehicleWeaponGoal.shouldAdvanceFireCadence(
                new WeaponStatus(false, true, false, false, false, true, 0)));
        assertFalse(VehicleWeaponGoal.shouldAdvanceFireCadence(
                new WeaponStatus(true, true, false, false, true, true, 0)));
        assertFalse(VehicleWeaponGoal.shouldAdvanceFireCadence(
                new WeaponStatus(true, true, false, false, false, false, 0)));

        assertEquals(0.25D, VehicleWeaponGoal.advanceFireProgress(0.0D, 0.25D), 1.0E-6D);
        assertEquals(1.0D, VehicleWeaponGoal.advanceFireProgress(0.9D, 0.25D), 1.0E-6D);
        assertEquals(0.0D, VehicleWeaponGoal.advanceFireProgress(-0.5D, -1.0D), 1.0E-6D);
    }

    @Test
    void unknownVehicleCadenceDistanceKeepsActualDistance() {
        WeaponHandle weapon = weapon(0, WeaponPayloadType.SHELL);

        assertEquals(32.0D, VehicleWeaponGoal.cadenceDistance(32.0D, VehicleMovementType.UNKNOWN, weapon), 1.0E-6D);
    }

    @Test
    void weaponSelectionFiltersWeaponsWithoutAmmo() {
        WeaponHandle emptyBullet = weapon(0, WeaponPayloadType.BULLET);
        WeaponHandle loadedShell = weapon(1, WeaponPayloadType.SHELL);

        Optional<WeaponHandle> selected = VehicleWeaponGoal.chooseWeapon(
                List.of(emptyBullet, loadedShell),
                List.of(ammo(false), ammo(true)),
                false,
                null);

        assertTrue(selected.isPresent());
        assertEquals(loadedShell, selected.get());
    }

    @Test
    void weaponSelectionFiltersWeaponsInCooldown() {
        WeaponHandle coolingBullet = weapon(0, WeaponPayloadType.BULLET);
        WeaponHandle readyShell = weapon(1, WeaponPayloadType.SHELL);

        Optional<WeaponHandle> selected = VehicleWeaponGoal.chooseWeapon(
                List.of(coolingBullet, readyShell),
                List.of(cooldown(true), ammo(true)),
                false,
                null);

        assertTrue(selected.isPresent());
        assertEquals(readyShell, selected.get());
    }

    @Test
    void weaponSelectionDoesNotRequireImmediateFireReadiness() {
        WeaponHandle bullet = weapon(0, WeaponPayloadType.BULLET);
        WeaponHandle shell = weapon(1, WeaponPayloadType.SHELL);

        Optional<WeaponHandle> selected = VehicleWeaponGoal.chooseWeapon(
                List.of(bullet, shell),
                List.of(new WeaponStatus(false, false, false, false, false, true, 88), ammo(true)),
                false,
                null);

        assertTrue(selected.isPresent());
        assertEquals(bullet, selected.get());
    }

    @Test
    void weaponSelectionPrefersReadyWeaponWithinSamePayloadBand() {
        WeaponHandle currentMissile = weapon(0, WeaponPayloadType.MISSILE);
        WeaponHandle readyMissile = weapon(1, WeaponPayloadType.ROCKET);

        Optional<WeaponHandle> selected = VehicleWeaponGoal.chooseWeapon(
                List.of(currentMissile, readyMissile),
                List.of(new WeaponStatus(false, false, false, false, false, true, 0), WeaponStatus.READY),
                true,
                currentMissile);

        assertTrue(selected.isPresent());
        assertEquals(readyMissile, selected.get());
    }

    @Test
    void weaponSelectionReturnsEmptyWhenAllAmmoCapableWeaponsAreCoolingDown() {
        WeaponHandle bullet = weapon(0, WeaponPayloadType.BULLET);
        WeaponHandle shell = weapon(1, WeaponPayloadType.SHELL);

        Optional<WeaponHandle> selected = VehicleWeaponGoal.chooseWeapon(
                List.of(bullet, shell),
                List.of(cooldown(true), cooldown(true)),
                false,
                null);

        assertTrue(selected.isEmpty());
    }

    @Test
    void softTargetsPreferBulletOrHitscanWeapons() {
        WeaponHandle shell = weapon(0, WeaponPayloadType.SHELL);
        WeaponHandle bullet = weapon(1, WeaponPayloadType.BULLET);

        Optional<WeaponHandle> selected = VehicleWeaponGoal.chooseWeapon(
                List.of(shell, bullet),
                List.of(ammo(true), ammo(true)),
                false,
                null);

        assertTrue(selected.isPresent());
        assertEquals(bullet, selected.get());
    }

    @Test
    void heavyTargetsPreferShellRocketOrMissileWeapons() {
        WeaponHandle bullet = weapon(0, WeaponPayloadType.BULLET);
        WeaponHandle rocket = weapon(1, WeaponPayloadType.ROCKET);

        Optional<WeaponHandle> selected = VehicleWeaponGoal.chooseWeapon(
                List.of(bullet, rocket),
                List.of(ammo(true), ammo(true)),
                true,
                null);

        assertTrue(selected.isPresent());
        assertEquals(rocket, selected.get());
    }

    @Test
    void preferredWeaponWithNoAmmoFallsBackToAvailableWeapon() {
        WeaponHandle bullet = weapon(0, WeaponPayloadType.BULLET);
        WeaponHandle missile = weapon(1, WeaponPayloadType.MISSILE);

        Optional<WeaponHandle> selected = VehicleWeaponGoal.chooseWeapon(
                List.of(bullet, missile),
                List.of(ammo(true), ammo(false)),
                true,
                null);

        assertTrue(selected.isPresent());
        assertEquals(bullet, selected.get());
    }

    @Test
    void currentWeaponWinsOnlyWithinSamePayloadPreferenceBand() {
        WeaponHandle currentBullet = weapon(0, WeaponPayloadType.BULLET);
        WeaponHandle otherBullet = weapon(1, WeaponPayloadType.HITSCAN);

        Optional<WeaponHandle> selected = VehicleWeaponGoal.chooseWeapon(
                List.of(otherBullet, currentBullet),
                List.of(ammo(true), ammo(true)),
                false,
                currentBullet);

        assertTrue(selected.isPresent());
        assertEquals(currentBullet, selected.get());
    }

    @Test
    void weaponSelectionFiltersImpossibleFireWindows() {
        WeaponHandle impossibleBullet = weapon(0, WeaponPayloadType.BULLET);
        WeaponHandle possibleShell = weapon(1, WeaponPayloadType.SHELL);

        Optional<WeaponHandle> selected = VehicleWeaponGoal.chooseWeapon(
                List.of(impossibleBullet, possibleShell),
                List.of(WeaponStatus.READY, WeaponStatus.READY),
                List.of(WeaponFireWindow.impossible(0.0D, 12.0D, 4.0D), WeaponFireWindow.needsPosture(40.0D, 0.0D, 6.0D)),
                false,
                null);

        assertTrue(selected.isPresent());
        assertEquals(possibleShell, selected.get());
    }

    @Test
    void weaponSelectionPrefersCurrentFireWindowWithinSamePayloadAndReadiness() {
        WeaponHandle needsPosture = weapon(0, WeaponPayloadType.BULLET);
        WeaponHandle inWindow = weapon(1, WeaponPayloadType.HITSCAN);

        Optional<WeaponHandle> selected = VehicleWeaponGoal.chooseWeapon(
                List.of(needsPosture, inWindow),
                List.of(WeaponStatus.READY, WeaponStatus.READY),
                List.of(WeaponFireWindow.needsPosture(40.0D, 0.0D, 6.0D), WeaponFireWindow.inWindow(0.0D, 0.0D, 1.0D)),
                false,
                needsPosture);

        assertTrue(selected.isPresent());
        assertEquals(inWindow, selected.get());
    }

    private static WeaponHandle weapon(int id, WeaponPayloadType payloadType) {
        return new WeaponHandle(id, "weapon_" + id, 0.0D, 96.0D, id, WeaponAimMode.INDEPENDENT, payloadType);
    }

    private static CurrentWeaponSnapshot snapshot(WeaponAimMode aimMode, WeaponPayloadType payloadType, WeaponStatus status) {
        return CurrentWeaponSnapshot.of(UUID.randomUUID(), 4, 0, UUID.randomUUID(), 9,
                new WeaponHandle(0, "weapon", 0.0D, 96.0D, 0, aimMode, payloadType),
                status);
    }

    private static CurrentWeaponSnapshot snapshot(WeaponAimMode aimMode, WeaponPayloadType payloadType,
                                                  WeaponStatus status, double fireProgress) {
        return CurrentWeaponSnapshot.of(UUID.randomUUID(), 4, 0, UUID.randomUUID(), 9,
                new WeaponHandle(0, "weapon", 0.0D, 96.0D, 0, aimMode, payloadType),
                status,
                fireProgress);
    }

    private static WeaponStatus ammo(boolean hasAmmo) {
        return new WeaponStatus(false, false, false, false, false, hasAmmo, 0);
    }

    private static WeaponStatus cooldown(boolean hasAmmo) {
        return new WeaponStatus(false, false, false, false, true, hasAmmo, 8);
    }
}
