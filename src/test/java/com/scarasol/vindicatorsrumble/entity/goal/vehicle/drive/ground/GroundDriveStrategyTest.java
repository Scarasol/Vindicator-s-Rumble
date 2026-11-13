package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground;

import com.scarasol.vindicatorsrumble.api.vehicle.DriveIntent;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundDriveStrategyTest {
    private static final double STOP_DISTANCE = 21.0D;
    private static final double START_DISTANCE = 42.0D;
    private static final VehicleDimensions DIMENSIONS = new VehicleDimensions(3.0D, 2.0D, 5.0D);

    @Test
    void rammingOverridesWeaponDriveMemoryWhenTargetIsValid() {
        assertEquals(
                DriveIntent.RAM_TARGET,
                GroundDriveStrategy.combatDriveIntent(DriveIntent.CHASE_TARGET, true, true));
    }

    @Test
    void supportFireMemoryOverridesRammingWhenTargetIsValid() {
        assertEquals(
                DriveIntent.SUPPORT_FIRE,
                GroundDriveStrategy.combatDriveIntent(DriveIntent.SUPPORT_FIRE, true, true));
    }

    @Test
    void weaponDriveMemoryRemainsWhenRammingIsUnavailable() {
        assertEquals(
                DriveIntent.CHASE_TARGET,
                GroundDriveStrategy.combatDriveIntent(DriveIntent.CHASE_TARGET, true, false));
    }

    @Test
    void supportFireMemoryRemainsWhenRammingIsUnavailable() {
        assertEquals(
                DriveIntent.SUPPORT_FIRE,
                GroundDriveStrategy.combatDriveIntent(DriveIntent.SUPPORT_FIRE, true, false));
    }

    @Test
    void supportFireMemoryIsIgnoredWithoutValidTarget() {
        assertEquals(
                DriveIntent.NONE,
                GroundDriveStrategy.combatDriveIntent(DriveIntent.SUPPORT_FIRE, false, false));
    }

    @Test
    void validTargetFallsBackToChaseWithoutMemoryOrRamming() {
        assertEquals(
                DriveIntent.CHASE_TARGET,
                GroundDriveStrategy.combatDriveIntent(DriveIntent.NONE, true, false));
    }

    @Test
    void noTargetAndNoMemoryStaysIdle() {
        assertEquals(
                DriveIntent.NONE,
                GroundDriveStrategy.combatDriveIntent(DriveIntent.NONE, false, false));
    }

    @Test
    void replanCacheKeepsReadyPathWhileIntervalIsActive() {
        GroundDriveStrategy.PathPlanKey previous = key(DriveIntent.CHASE_TARGET, 7, new Vec3(0.0D, 64.0D, 0.0D), 0);
        GroundDriveStrategy.PathPlanKey next = key(DriveIntent.CHASE_TARGET, 7, new Vec3(20.0D, 64.0D, 0.0D), 0);

        assertFalse(GroundDriveStrategy.shouldReplanPath(previous, next, 10, true));
    }

    @Test
    void replanCacheReusesReadyPathAfterIntervalWhenTargetBarelyMoved() {
        GroundDriveStrategy.PathPlanKey previous = key(DriveIntent.CHASE_TARGET, 7, new Vec3(0.0D, 64.0D, 0.0D), 0);
        GroundDriveStrategy.PathPlanKey next = key(DriveIntent.CHASE_TARGET, 7, new Vec3(3.0D, 64.0D, 0.0D), 0);

        assertFalse(GroundDriveStrategy.shouldReplanPath(previous, next, 0, true));
    }

    @Test
    void replanCacheRefreshesReadyPathAfterIntervalWhenTargetMovedEnough() {
        GroundDriveStrategy.PathPlanKey previous = key(DriveIntent.CHASE_TARGET, 7, new Vec3(0.0D, 64.0D, 0.0D), 0);
        GroundDriveStrategy.PathPlanKey next = key(DriveIntent.CHASE_TARGET, 7, new Vec3(4.0D, 64.0D, 0.0D), 0);

        assertTrue(GroundDriveStrategy.shouldReplanPath(previous, next, 0, true));
    }

    @Test
    void replanCacheRefreshesImmediatelyWhenIntentChanges() {
        GroundDriveStrategy.PathPlanKey previous = key(DriveIntent.CHASE_TARGET, 7, new Vec3(0.0D, 64.0D, 0.0D), 0);
        GroundDriveStrategy.PathPlanKey next = key(DriveIntent.SUPPORT_FIRE, 7, new Vec3(0.0D, 64.0D, 0.0D), 0);

        assertTrue(GroundDriveStrategy.shouldReplanPath(previous, next, 10, true));
    }

    @Test
    void replanCacheRefreshesImmediatelyWhenCapturedPathChanges() {
        GroundDriveStrategy.PathPlanKey previous = key(DriveIntent.MOVE_TO, -1, new Vec3(20.0D, 64.0D, 20.0D), 11);
        GroundDriveStrategy.PathPlanKey next = key(DriveIntent.MOVE_TO, -1, new Vec3(20.0D, 64.0D, 20.0D), 12);

        assertTrue(GroundDriveStrategy.shouldReplanPath(previous, next, 10, true));
    }

    @Test
    void replanCachePlansWhenNoReadyPathExists() {
        GroundDriveStrategy.PathPlanKey previous = key(DriveIntent.CHASE_TARGET, 7, new Vec3(0.0D, 64.0D, 0.0D), 0);
        GroundDriveStrategy.PathPlanKey next = key(DriveIntent.CHASE_TARGET, 7, new Vec3(0.0D, 64.0D, 0.0D), 0);

        assertTrue(GroundDriveStrategy.shouldReplanPath(previous, next, 10, false));
    }

    @Test
    void collisionBoxRoundoffDoesNotInvalidateTheRoute() {
        var previous = new GroundDriveStrategy.PathPlanKey(DriveIntent.MOVE_TO, -1, BlockPos.ZERO,
                Vec3.ZERO, new VehicleDimensions(4.319649677552661D, 2.2602501314165693D, 7.2270238233546D), 0);
        var next = new GroundDriveStrategy.PathPlanKey(DriveIntent.MOVE_TO, -1, BlockPos.ZERO,
                Vec3.ZERO, new VehicleDimensions(4.31964967741942D, 2.26025003930085D, 7.227025280344662D), 0);

        assertFalse(GroundDriveStrategy.shouldReplanPath(previous, next, 19, true));
    }

    @Test
    void widerBodyWithTheSameDiagonalReplansForRoadClearance() {
        var previous = key(DriveIntent.MOVE_TO, -1, Vec3.ZERO, 0);
        var next = new GroundDriveStrategy.PathPlanKey(DriveIntent.MOVE_TO, -1, BlockPos.ZERO,
                Vec3.ZERO, new VehicleDimensions(4.0D, 2.0D, 4.0D), 0);

        assertTrue(GroundDriveStrategy.shouldReplanPath(previous, next, 19, true));
    }

    @Test
    void largerNavigationFootprintImmediatelyReplans() {
        var previous = key(DriveIntent.MOVE_TO, -1, Vec3.ZERO, 0);
        for (VehicleDimensions dimensions : new VehicleDimensions[]{
                new VehicleDimensions(4.0D, 2.0D, 5.0D),
                new VehicleDimensions(3.0D, 2.1D, 5.0D)}) {
            var next = new GroundDriveStrategy.PathPlanKey(DriveIntent.MOVE_TO, -1, BlockPos.ZERO,
                    Vec3.ZERO, dimensions, 0);
            assertTrue(GroundDriveStrategy.shouldReplanPath(previous, next, 19, true));
        }
    }

    @Test
    void chaseClosingStartsOutsideOuterBandAndContinuesUntilInnerBand() {
        boolean closing = false;
        assertFalse(shouldClose(START_DISTANCE, closing));

        assertTrue(shouldClose(42.1D, closing));
        closing = true;

        assertTrue(shouldClose(38.0D, closing));
        assertTrue(shouldClose(30.0D, closing));
        assertTrue(shouldClose(21.1D, closing));
        assertFalse(shouldClose(STOP_DISTANCE, closing));
    }

    @Test
    void chaseClosingDoesNotStartInsideMiddleBand() {
        assertFalse(shouldClose(41.0D, false));
        assertFalse(shouldClose(30.0D, false));
        assertFalse(shouldClose(22.0D, false));
    }

    @Test
    void chaseRetreatStartsInsideInnerBandAndContinuesUntilOuterBand() {
        boolean retreating = false;
        assertFalse(shouldRetreat(STOP_DISTANCE, retreating));

        assertTrue(shouldRetreat(20.9D, retreating));
        retreating = true;

        assertTrue(shouldRetreat(30.0D, retreating));
        assertTrue(shouldRetreat(41.9D, retreating));
        assertFalse(shouldRetreat(START_DISTANCE, retreating));
    }

    @Test
    void chaseRetreatDoesNotStartInsideMiddleBand() {
        assertFalse(shouldRetreat(21.1D, false));
        assertFalse(shouldRetreat(30.0D, false));
        assertFalse(shouldRetreat(41.0D, false));
    }

    @Test
    void middleBandDoesNotRequestEitherDirectionWithoutExistingState() {
        for (double distance : new double[]{21.1D, 28.0D, 35.0D, 41.9D}) {
            assertFalse(shouldClose(distance, false));
            assertFalse(shouldRetreat(distance, false));
        }
    }

    @Test
    void middleBandDriftDoesNotStartBrakeWithoutActiveDistanceIntent() {
        assertFalse(shouldBrake(30.0D, false, false, false, true, 0.4D));
    }

    @Test
    void retreatInputBlockedUsesTheActualRequestedDirection() {
        GroundClearance backBlocked = new GroundClearance(8.0D, 1.0D, 8.0D, 8.0D);
        GroundClearance frontBlocked = new GroundClearance(1.0D, 8.0D, 8.0D, 8.0D);

        assertTrue(GroundDriveStrategy.isRetreatInputBlocked(
                new com.scarasol.vindicatorsrumble.api.vehicle.VehicleInput(false, true, false, false, false, false, false, false, false, false),
                backBlocked,
                2.25D));
        assertFalse(GroundDriveStrategy.isRetreatInputBlocked(
                new com.scarasol.vindicatorsrumble.api.vehicle.VehicleInput(true, false, false, false, false, false, false, false, false, false),
                backBlocked,
                2.25D));
        assertTrue(GroundDriveStrategy.isRetreatInputBlocked(
                new com.scarasol.vindicatorsrumble.api.vehicle.VehicleInput(true, false, false, false, false, false, false, false, false, false),
                frontBlocked,
                2.25D));
    }

    @Test
    void maneuverDoesNotExitIntoBlockedNextInputDirection() {
        GroundClearance blockedBack = new GroundClearance(8.0D, 1.0D, 8.0D, 8.0D);
        GroundClearance openBack = new GroundClearance(8.0D, 8.0D, 8.0D, 8.0D);

        assertFalse(GroundDriveStrategy.canExitManeuverWithInput(
                new com.scarasol.vindicatorsrumble.api.vehicle.VehicleInput(false, true, false, false, false, false, false, false, false, false),
                blockedBack));
        assertTrue(GroundDriveStrategy.canExitManeuverWithInput(
                new com.scarasol.vindicatorsrumble.api.vehicle.VehicleInput(false, true, false, false, false, false, false, false, false, false),
                openBack));
    }

    @Test
    void fireWindowHoldSuppressesNoMovementManeuverOnlyWhileActive() {
        assertTrue(GroundDriveStrategy.suppressStuckForFireWindowHold(1));
        assertTrue(GroundDriveStrategy.suppressStuckForFireWindowHold(20));
        assertFalse(GroundDriveStrategy.suppressStuckForFireWindowHold(0));
        assertFalse(GroundDriveStrategy.suppressStuckForFireWindowHold(-1));
    }

    @Test
    void closingStartsBrakingBeforeInnerBoundary() {
        assertTrue(shouldBrake(30.0D, true, false, false, true, 0.5D));
        assertFalse(shouldBrake(33.0D, true, false, false, true, 0.5D));
    }

    @Test
    void retreatStartsBrakingBeforeOuterBoundary() {
        assertTrue(shouldBrake(34.0D, false, true, false, true, 0.5D));
        assertFalse(shouldBrake(31.0D, false, true, false, true, 0.5D));
    }

    @Test
    void activeBandBrakeContinuesAfterOvershootingOuterBand() {
        assertTrue(shouldBrake(46.0D, false, false, true, true, 0.5D));
    }

    @Test
    void activeBandBrakeEndsWhenStopped() {
        assertFalse(shouldBrake(46.0D, false, false, true, false, 0.5D));
    }

    @Test
    void chaseDistanceControlRequiresRealTwoThirdsOneThirdBand() {
        assertFalse(GroundDriveStrategy.shouldCloseToChase(50.0D * 50.0D, 21.0D, 21.0D, false));
        assertFalse(GroundDriveStrategy.shouldRetreatFromChase(10.0D * 10.0D, 21.0D, 21.0D, false));
    }

    private static boolean shouldClose(double distance, boolean alreadyClosing) {
        return GroundDriveStrategy.shouldCloseToChase(distance * distance, STOP_DISTANCE, START_DISTANCE, alreadyClosing);
    }

    private static boolean shouldRetreat(double distance, boolean alreadyRetreating) {
        return GroundDriveStrategy.shouldRetreatFromChase(distance * distance, STOP_DISTANCE, START_DISTANCE, alreadyRetreating);
    }

    private static boolean shouldBrake(double distance, boolean closing, boolean retreating,
                                       boolean alreadyBraking, boolean moving, double speed) {
        return GroundDriveStrategy.shouldBrakeChaseBand(
                distance * distance,
                STOP_DISTANCE,
                START_DISTANCE,
                closing,
                retreating,
                alreadyBraking,
                moving,
                speed);
    }

    private static GroundDriveStrategy.PathPlanKey key(DriveIntent intent, int targetEntityId, Vec3 position, int pathIdentity) {
        return new GroundDriveStrategy.PathPlanKey(
                intent,
                targetEntityId,
                BlockPos.containing(position),
                position,
                DIMENSIONS,
                pathIdentity);
    }
}
