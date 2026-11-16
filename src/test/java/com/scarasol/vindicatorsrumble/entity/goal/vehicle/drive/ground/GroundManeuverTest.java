package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundManeuverTest {
    @Test
    void brakingVehicleStartsManeuverByBraking() {
        GroundClearance clearance = new GroundClearance(8.0D, 8.0D, 8.0D, 8.0D);

        assertEquals(GroundManeuverStep.BRAKE, GroundDriveControl.selectInitialManeuverStep(clearance, true));
    }

    @Test
    void frontBlockedStartsByBackingOut() {
        GroundClearance clearance = new GroundClearance(0.8D, 5.0D, 3.0D, 3.0D);

        assertEquals(GroundManeuverStep.BACK_STRAIGHT, GroundDriveControl.selectInitialManeuverStep(clearance, false));
    }

    @Test
    void backBlockedStartsBySwingingForward() {
        GroundClearance clearance = new GroundClearance(5.0D, 0.8D, 3.0D, 3.0D);

        assertEquals(GroundManeuverStep.SWING_FORWARD, GroundDriveControl.selectInitialManeuverStep(clearance, false));
    }

    @Test
    void narrowFrontPrefersBackingEvenWhenFrontIsNotImmediatelyBlocked() {
        GroundClearance clearance = new GroundClearance(1.8D, 4.0D, 3.0D, 3.0D);

        assertEquals(GroundManeuverStep.BACK_STRAIGHT, GroundDriveControl.selectInitialManeuverStep(clearance, false));
    }

    @Test
    void clearFrontDoesNotReverseJustBecauseRearHasMoreSpace() {
        GroundClearance clearance = new GroundClearance(5.0D, 8.0D, 3.0D, 3.0D);

        assertEquals(GroundManeuverStep.SWING_FORWARD, GroundDriveControl.selectInitialManeuverStep(clearance, false));
    }

    @Test
    void tightFrontAndBackStillChoosesShortMovement() {
        GroundClearance clearance = new GroundClearance(0.9D, 0.8D, 3.0D, 3.0D);

        assertEquals(GroundManeuverStep.SWING_FORWARD, GroundDriveControl.selectInitialManeuverStep(clearance, false));
    }

    @Test
    void openSideOverridesTargetFallbackDirection() {
        GroundClearance clearance = new GroundClearance(2.0D, 2.0D, 6.0D, 2.0D);

        assertEquals(-1, GroundDriveControl.selectManeuverTurnDirection(clearance, 45.0D, 0));
    }

    @Test
    void similarSideSpaceUsesTargetDirection() {
        GroundClearance clearance = new GroundClearance(2.0D, 2.0D, 4.0D, 4.2D);

        assertEquals(1, GroundDriveControl.selectManeuverTurnDirection(clearance, 45.0D, 0));
        assertEquals(-1, GroundDriveControl.selectManeuverTurnDirection(clearance, -45.0D, 0));
    }

    @Test
    void forwardSwingStopsTurningWhenAlreadyAimed() {
        GroundClearance clearance = new GroundClearance(5.0D, 5.0D, 4.0D, 4.0D);

        assertEquals(0, GroundDriveControl.selectManeuverForwardTurn(-1, 4.0D, clearance));
    }

    @Test
    void forwardSwingUsesTargetDirectionForLargeYawError() {
        GroundClearance clearance = new GroundClearance(5.0D, 5.0D, 4.0D, 4.0D);

        assertEquals(1, GroundDriveControl.selectManeuverForwardTurn(-1, 70.0D, clearance));
        assertEquals(-1, GroundDriveControl.selectManeuverForwardTurn(1, -70.0D, clearance));
    }

    @Test
    void forwardSwingKeepsEscapeTurnWhileObstacleRemainsAhead() {
        GroundClearance clearance = new GroundClearance(4.0D, 8.0D, 8.0D, 8.0D);

        assertEquals(1, GroundDriveControl.selectManeuverForwardTurn(1, -28.0D, clearance));
        assertEquals(-1, GroundDriveControl.selectManeuverForwardTurn(-1, 28.0D, clearance));
    }

    @Test
    void forwardSwingUsesOpenSideWhenVehicleIsSideSqueezed() {
        GroundClearance clearance = new GroundClearance(5.0D, 5.0D, 0.9D, 5.0D);

        assertEquals(1, GroundDriveControl.selectManeuverForwardTurn(-1, -70.0D, clearance));
    }

    @Test
    void maneuverPulseAbortsForwardWhenFrontIsBlockedAfterShortProbe() {
        GroundClearance clearance = new GroundClearance(1.0D, 8.0D, 8.0D, 8.0D);

        assertTrue(GroundDriveControl.shouldAbortManeuverPulse(
                new VehicleInput(true, false, false, false, false, false, false, false, false, false),
                clearance,
                2));
    }

    @Test
    void maneuverPulseAbortsBackWhenRearIsBlockedAfterShortProbe() {
        GroundClearance clearance = new GroundClearance(8.0D, 1.0D, 8.0D, 8.0D);

        assertTrue(GroundDriveControl.shouldAbortManeuverPulse(
                new VehicleInput(false, true, false, false, false, false, false, false, false, false),
                clearance,
                2));
    }

    @Test
    void maneuverPulseKeepsInitialProbeAndIgnoresSideOnlySqueeze() {
        GroundClearance sideSqueezed = new GroundClearance(8.0D, 8.0D, 8.0D, 1.3D);

        assertFalse(GroundDriveControl.shouldAbortManeuverPulse(
                new VehicleInput(true, false, true, false, false, false, false, false, false, false),
                sideSqueezed,
                1));
        assertFalse(GroundDriveControl.shouldAbortManeuverPulse(
                new VehicleInput(true, false, true, false, false, false, false, false, false, false),
                sideSqueezed,
                8));
    }

    @Test
    void slowThrottleReversalIsNotTreatedAsACollisionInOpenSpace() {
        GroundClearance clearance = new GroundClearance(8.0D, 8.0D, 8.0D, 8.0D);

        assertFalse(GroundDriveControl.shouldAbortManeuverPulse(
                new VehicleInput(true, false, false, false, false, false, false, false, false, false),
                clearance,
                8));
        assertFalse(GroundDriveControl.shouldAbortManeuverPulse(
                new VehicleInput(false, true, false, false, false, false, false, false, false, false),
                clearance,
                8));
        assertFalse(GroundDriveControl.shouldAbortManeuverPulse(
                VehicleInput.BRAKE,
                clearance,
                8));
    }

    @Test
    void backTurnDoesNotSwingForwardWhenRearClearanceDominates() {
        GroundClearance clearance = new GroundClearance(1.36D, 8.0D, 6.3D, 1.3D);

        assertEquals(GroundManeuverStep.BACK_TURN, GroundDriveControl.preferredMovementStep(clearance, true));
        assertEquals(GroundManeuverStep.BACK_TURN, GroundDriveControl.nextBackTurnStep(clearance, 14, 0));
    }

    @Test
    void backTurnSwingsForwardWhenFrontClearanceIsGreater() {
        GroundClearance clearance = new GroundClearance(3.0D, 0.8D, 3.0D, 3.0D);

        assertEquals(GroundManeuverStep.SWING_FORWARD, GroundDriveControl.preferredMovementStep(clearance, true));
        assertEquals(GroundManeuverStep.SWING_FORWARD, GroundDriveControl.nextBackTurnStep(clearance, 4, 0));
    }

    @Test
    void straightBackStillBacksWhenRearIsOnlySlightlyGreater() {
        GroundClearance clearance = new GroundClearance(4.7D, 4.8D, 3.0D, 3.0D);

        assertEquals(GroundManeuverStep.BACK_STRAIGHT, GroundDriveControl.preferredMovementStep(clearance, false));
        assertEquals(GroundManeuverStep.BACK_STRAIGHT, GroundDriveControl.nextBackStraightStep(clearance, 4, 0));
    }

    @Test
    void backTurnReturnsToStraightBackWhenRearStillPreferredAndProgressing() {
        GroundClearance clearance = new GroundClearance(2.0D, 8.0D, 6.0D, 2.0D);

        assertEquals(GroundManeuverStep.BACK_STRAIGHT, GroundDriveControl.nextBackTurnStep(clearance, 18, 0));
    }

    @Test
    void backingPulseDoesNotFlipForwardOnEqualOrNoisyClearance() {
        assertEquals(GroundManeuverStep.BACK_TURN, GroundDriveControl.nextBackTurnStep(
                new GroundClearance(0.008D, 0.008D, 1.46D, 8.0D), 4, 0));
        assertEquals(GroundManeuverStep.BACK_STRAIGHT, GroundDriveControl.nextBackStraightStep(
                new GroundClearance(5.0D, 4.8D, 8.0D, 8.0D), 4, 0));
    }

    @Test
    void openRearDoesNotCancelBackingOnLargerFrontReading() {
        GroundClearance clearance = new GroundClearance(8.0D, 6.1D, 8.0D, 1.4D);

        assertEquals(GroundManeuverStep.BACK_STRAIGHT, GroundDriveControl.nextBackStraightStep(clearance, 4, 0));
        assertEquals(GroundManeuverStep.BACK_TURN, GroundDriveControl.nextBackStraightStep(clearance, 5, 5));
        assertEquals(GroundManeuverStep.BACK_TURN, GroundDriveControl.nextBackTurnStep(clearance, 4, 0));
    }

    @Test
    void straightBackUsesBackTurnWhenRearStillPreferredButNoProgress() {
        GroundClearance clearance = new GroundClearance(2.0D, 8.0D, 6.0D, 2.0D);

        assertEquals(GroundManeuverStep.BACK_TURN, GroundDriveControl.nextBackStraightStep(clearance, 5, 5));
    }

    @Test
    void maneuverDoesNotExitIntoNarrowFrontClearance() {
        GroundClearance clearance = new GroundClearance(1.6D, 8.0D, 4.0D, 4.0D);

        assertFalse(GroundDriveControl.canExitManeuverWithInput(
                new VehicleInput(true, false, false, false, false, false, false, false, false, false),
                clearance));
    }

    @Test
    void maneuverCanExitWhenNextThrottleDirectionHasRoom() {
        GroundClearance clearance = new GroundClearance(1.6D, 8.0D, 4.0D, 4.0D);

        assertTrue(GroundDriveControl.canExitManeuverWithInput(
                new VehicleInput(false, true, false, false, false, false, false, false, false, false),
                clearance));
    }
}
