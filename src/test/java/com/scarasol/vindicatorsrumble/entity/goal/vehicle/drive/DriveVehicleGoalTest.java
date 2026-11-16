package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleMovementType;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground.GroundDriveStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriveVehicleGoalTest {
    @Test
    void rammingEnabledWhenDriverHasNoWeaponAndWeaponSeatsAreEmpty() {
        assertTrue(GroundDriveStrategy.shouldUseRamming(
                VehicleMovementType.WHEEL,
                true,
                false,
                false));
    }

    @Test
    void rammingDisabledWhenDriverSeatHasWeapon() {
        assertFalse(GroundDriveStrategy.shouldUseRamming(
                VehicleMovementType.WHEEL,
                true,
                true,
                false));
    }

    @Test
    void rammingDisabledWhenWeaponSeatIsOccupied() {
        assertFalse(GroundDriveStrategy.shouldUseRamming(
                VehicleMovementType.TRACK,
                true,
                false,
                true));
    }

    @Test
    void rammingEnabledWhenAmmoConsumptionIsOnAndAllVehicleWeaponsAreEmpty() {
        assertTrue(GroundDriveStrategy.shouldUseRamming(
                VehicleMovementType.WHEEL,
                true,
                true,
                true,
                true,
                false));
    }

    @Test
    void rammingDisabledWhenAmmoConsumptionIsOffEvenIfAmmoProbeIsEmpty() {
        assertFalse(GroundDriveStrategy.shouldUseRamming(
                VehicleMovementType.WHEEL,
                true,
                true,
                true,
                false,
                false));
    }

    @Test
    void rammingDisabledWhenAnyVehicleWeaponStillHasAmmo() {
        assertFalse(GroundDriveStrategy.shouldUseRamming(
                VehicleMovementType.TRACK,
                true,
                true,
                true,
                true,
                true));
    }

    @Test
    void rammingEnabledWhenDriverSeatWeaponIsOutOfAmmoEvenIfOtherSeatHasAmmo() {
        assertTrue(GroundDriveStrategy.shouldUseRamming(
                VehicleMovementType.TRACK,
                true,
                true,
                false,
                true,
                true,
                true));
    }

    @Test
    void unarmedDriverReliesOnOccupiedGunnerAmmoOnlyWhenAmmoIsFinite() {
        assertTrue(GroundDriveStrategy.shouldUseRamming(
                VehicleMovementType.WHEEL, true, false, false, true, true, false));
        assertFalse(GroundDriveStrategy.shouldUseRamming(
                VehicleMovementType.WHEEL, true, false, false, true, true, true));
        assertFalse(GroundDriveStrategy.shouldUseRamming(
                VehicleMovementType.WHEEL, true, false, false, true, false, false));
    }

    @Test
    void armedDriverWithAmmoDoesNotRamWhenOtherWeaponsAreEmpty() {
        assertFalse(GroundDriveStrategy.shouldUseRamming(
                VehicleMovementType.TRACK, true, true, true, true, true, false));
    }

    @Test
    void unarmedDriverWithoutGunnerDoesNotNeedVehicleAmmo() {
        assertTrue(GroundDriveStrategy.shouldUseRamming(
                VehicleMovementType.WHEEL, true, false, false, false, true, true));
        assertTrue(GroundDriveStrategy.shouldUseRamming(
                VehicleMovementType.WHEEL, true, false, false, false, true, false));
    }

    @Test
    void rammingDisabledForNonGroundMovementTypes() {
        assertFalse(GroundDriveStrategy.shouldUseRamming(
                VehicleMovementType.HELICOPTER,
                true,
                false,
                false));
    }

}
