package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.helicopter;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleFlightInput;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleInput;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelicopterFlightControlCharacterizationTest {
    private static final double EPSILON = 1.0E-7D;

    @Test
    void preservesKnownFlightControlOutputs() {
        for (Case sample : cases()) {
            VehicleFlightInput output = new HelicopterFlightControl()
                    .command(sample.state, sample.plan.flightRequest(), sample.environment);
            assertEquals(sample.expectedInput, bits(output.input()), sample.name + " input");
            assertEquals(sample.expectedMouseX, output.mouseX(), EPSILON, sample.name + " mouseX");
            assertEquals(sample.expectedMouseY, output.mouseY(), EPSILON, sample.name + " mouseY");
        }
    }

    @Test
    void countermeasureRequestChangesOnlyTheDecoyBit() {
        for (Case sample : cases()) {
            VehicleFlightInput ordinary = new HelicopterFlightControl()
                    .command(sample.state, sample.plan.flightRequest(), sample.environment);
            VehicleFlightInput defensive = new HelicopterFlightControl()
                    .command(sample.state, sample.plan.flightRequest(), sample.environment, true);
            VehicleInput input = ordinary.input();
            assertEquals(new VehicleFlightInput(new VehicleInput(
                    input.forward(), input.back(), input.left(), input.right(), input.brake(),
                    input.sprint(), input.up(), input.down(), input.fire(), true),
                    ordinary.mouseX(), ordinary.mouseY()), defensive, sample.name);
        }
    }

    @Test
    void idleHeightKeepsAReasonableHeightInsteadOfTrackingThePreferredPoint() {
        HelicopterFlightPlanner planner = new HelicopterFlightPlanner();
        for (double height : new double[]{18.0D, 22.0D, 27.0D}) {
            HelicopterFlightPlanner.AircraftState aircraft = state(
                    new Vec3(0.0D, height, 0.0D), Vec3.ZERO, 0.0F, 0.0F, 0.0F,
                    true, true, 0.09F, height);
            assertEquals(height, planner.desiredY(null, aircraft, null, 18.0D, HelicopterEnvironment.NONE), EPSILON);
        }
    }

    @Test
    void heightCorrectionEntersTheBandAndStillHonorsTerrainSafety() {
        HelicopterFlightPlanner planner = new HelicopterFlightPlanner();
        HelicopterFlightPlanner.AircraftState low = state(
                new Vec3(0.0D, 10.0D, 0.0D), Vec3.ZERO, 0.0F, 0.0F, 0.0F,
                true, true, 0.09F, 10.0D);
        double corrected = planner.desiredY(null, low, null, 18.0D, HelicopterEnvironment.NONE);
        assertTrue(corrected > 16.0D && corrected < 28.0D);
        HelicopterEnvironment terrain = new HelicopterEnvironment(true, false, true, 0, 35.0D, 24.0D, 24.0D);
        assertTrue(planner.desiredY(null, low, null, 18.0D, terrain) > 35.0D);
    }

    @Test
    void divePassExitTracksTheTargetWithoutChangingItsRelativeDirection() {
        Vec3 direction = new Vec3(0.6D, 0.0D, 0.8D);
        Vec3 firstTarget = new Vec3(10.0D, 4.0D, -6.0D);
        Vec3 movedTarget = new Vec3(17.0D, 8.0D, -9.0D);
        Vec3 firstExit = HelicopterFlightPlanner.divePassExitPoint(firstTarget, direction, 30.0D, 42.0D);
        Vec3 movedExit = HelicopterFlightPlanner.divePassExitPoint(movedTarget, direction, 34.0D, 42.0D);

        assertEquals(42.0D, horizontalDistance(firstTarget, firstExit), EPSILON);
        assertEquals(42.0D, horizontalDistance(movedTarget, movedExit), EPSILON);
        assertEquals(direction.x, (movedExit.x - movedTarget.x) / 42.0D, EPSILON);
        assertEquals(direction.z, (movedExit.z - movedTarget.z) / 42.0D, EPSILON);
        assertEquals(34.0D, movedExit.y, EPSILON);
    }

    @Test
    void divePassReusesTheAttackRunFlightProfile() {
        Vec3 destination = new Vec3(30.0D, 28.0D, 0.0D);
        Vec3 aimPoint = new Vec3(30.0D, 2.0D, 0.0D);
        HelicopterFlightPlanner.Plan attackRun = plan(
                destination, aimPoint, 1.0D, false, true, HelicopterFlightPlanner.Phase.ATTACK_RUN);
        HelicopterFlightPlanner.Plan divePass = plan(
                destination, aimPoint, 1.0D, false, true, HelicopterFlightPlanner.Phase.DIVE_PASS);

        assertEquals(attackRun.flightRequest(), divePass.flightRequest());
    }

    @Test
    void unboostedPassTracksTheActualElevationWithoutForcingASteepDive() {
        Vec3 position = new Vec3(0.0D, 40.0D, 0.0D);
        for (HelicopterFlightPlanner.Phase phase : new HelicopterFlightPlanner.Phase[]{
                HelicopterFlightPlanner.Phase.ATTACK_RUN, HelicopterFlightPlanner.Phase.DIVE_PASS}) {
            for (double heightDifference : new double[]{-10.0D, -1.0D, -0.01D, 0.0D, 0.01D, 1.0D, 10.0D}) {
                Vec3 aimPoint = position.add(40.0D, heightDifference, 0.0D);
                HelicopterFlightPlanner.Plan pass = plan(
                        position.add(80.0D, 6.0D, 0.0D), aimPoint, 1.0D, false, true, phase)
                        .withEnvironment(HelicopterEnvironment.NONE, 0.0D);
                VehicleFlightInput output = new HelicopterFlightControl().command(
                        state(position, Vec3.ZERO, -90.0F, 0.0F, 0.0F,
                                false, true, 0.07F, 40.0D),
                        pass.flightRequest(), HelicopterEnvironment.NONE);
                double elevation = -Math.toDegrees(Math.atan2(heightDifference, 40.0D));

                assertEquals(elevation / 1.5D, output.mouseY(), EPSILON,
                        phase + " heightDifference=" + heightDifference);
                assertTrue(output.input().forward());
                assertTrue(output.input().sprint());
                assertFalse(output.input().up());
            }
        }
    }

    @Test
    void unboostedPassUsesAltitudeTrimInsteadOfContinuouslyIncreasingPower() {
        Vec3 position = new Vec3(0.0D, 40.0D, 0.0D);
        for (HelicopterFlightPlanner.Phase phase : new HelicopterFlightPlanner.Phase[]{
                HelicopterFlightPlanner.Phase.ATTACK_RUN, HelicopterFlightPlanner.Phase.DIVE_PASS}) {
            HelicopterFlightPlanner.Plan pass = plan(
                    new Vec3(80.0D, 32.0D, 0.0D), new Vec3(40.0D, 38.0D, 0.0D), 1.0D, false, true, phase)
                    .withEnvironment(HelicopterEnvironment.NONE, 0.0D);
            VehicleFlightInput output = new HelicopterFlightControl().command(
                    state(position, new Vec3(0.4D, 0.15D, 0.0D), -90.0F, 0.0F, 0.0F,
                            false, true, 0.12F, 40.0D),
                    pass.flightRequest(), HelicopterEnvironment.NONE);
            assertFalse(output.input().forward(), phase.name());
            assertTrue(output.input().down());
            assertTrue(output.input().sprint());
            assertFalse(output.input().up());
        }
    }

    @Test
    void cruiseDoesNotReapplyLiftAboveDesiredHeightEvenBelowTrimPower() {
        Vec3 position = new Vec3(0.0D, 40.0D, 0.0D);
        for (HelicopterFlightPlanner.Phase phase : new HelicopterFlightPlanner.Phase[]{
                HelicopterFlightPlanner.Phase.APPROACH, HelicopterFlightPlanner.Phase.EVASIVE_PASS,
                HelicopterFlightPlanner.Phase.ATTACK_RUN, HelicopterFlightPlanner.Phase.DIVE_PASS}) {
            HelicopterFlightPlanner.Plan cruise = plan(
                    new Vec3(80.0D, 32.0D, 0.0D), new Vec3(40.0D, 38.0D, 0.0D),
                    1.0D, false, true, phase).withEnvironment(HelicopterEnvironment.NONE, 0.0D);
            for (float power : new float[]{0.04F, 0.07F}) {
                VehicleFlightInput output = new HelicopterFlightControl().command(
                        state(position, new Vec3(0.4D, 0.15D, 0.0D), -90.0F, 0.0F, 0.0F,
                                false, true, power, 40.0D), cruise.flightRequest(), HelicopterEnvironment.NONE);
                assertFalse(output.input().forward(), phase + " power=" + power);
                VehicleFlightInput emergency = new HelicopterFlightControl().command(
                        state(position, new Vec3(0.4D, 0.15D, 0.0D), -90.0F, 0.0F, 0.0F,
                                false, true, power, 6.0D), cruise.flightRequest(), HelicopterEnvironment.NONE);
                assertTrue(emergency.input().forward(), phase.name());
            }
            if (phase == HelicopterFlightPlanner.Phase.ATTACK_RUN
                    || phase == HelicopterFlightPlanner.Phase.DIVE_PASS) {
                VehicleFlightInput groundDive = new HelicopterFlightControl().command(
                        state(position, new Vec3(0.4D, 0.15D, 0.0D), -90.0F, 0.0F, 0.0F,
                                false, true, 0.07F, 40.0D),
                        cruise.withEnvironment(HelicopterEnvironment.NONE, 38.0D).flightRequest(),
                        HelicopterEnvironment.NONE);
                assertTrue(groundDive.input().forward(), phase.name());
            }
        }
    }

    @Test
    void groundPassRetainsTheExistingSteepDiveProfile() {
        Vec3 position = new Vec3(0.0D, 40.0D, 0.0D);
        Vec3 aimPoint = new Vec3(40.0D, 0.0D, 0.0D);
        for (HelicopterFlightPlanner.Phase phase : new HelicopterFlightPlanner.Phase[]{
                HelicopterFlightPlanner.Phase.ATTACK_RUN, HelicopterFlightPlanner.Phase.DIVE_PASS}) {
            HelicopterFlightPlanner.Plan pass = plan(
                    new Vec3(80.0D, 18.0D, 0.0D), aimPoint, 1.0D, false, true, phase);
            VehicleFlightInput output = new HelicopterFlightControl().command(
                    state(position, Vec3.ZERO, -90.0F, 50.0F, 0.0F,
                            false, true, 0.12F, 40.0D),
                    pass.flightRequest(), HelicopterEnvironment.NONE);
            assertEquals(28.0D / 1.5D, output.mouseY(), EPSILON, phase.name());
        }
    }

    @Test
    void passPullOutUsesPredictedGroundClearanceInsteadOfDiveAttitude() {
        assertFalse(HelicopterFlightPlanner.needsPassPullOut(
                state(new Vec3(0.0D, 21.0D, 0.0D), new Vec3(0.6D, 0.05D, 0.0D),
                        -90.0F, 66.0F, 0.0F, false, true, 0.12F, 21.0D)));
        assertFalse(HelicopterFlightPlanner.needsPassPullOut(
                state(new Vec3(0.0D, 16.0D, 0.0D), new Vec3(0.6D, -0.12D, 0.0D),
                        -90.0F, 45.0F, 0.0F, false, true, 0.12F, 15.9D)));
        assertTrue(HelicopterFlightPlanner.needsPassPullOut(
                state(new Vec3(0.0D, 13.0D, 0.0D), new Vec3(0.6D, -0.2D, 0.0D),
                        -90.0F, 45.0F, 0.0F, false, true, 0.12F, 13.0D)));
    }

    @Test
    void directMovementCompletesOnlyWhenItReachesOrPassesTheDestination() {
        Vec3 destination = new Vec3(0.0D, 30.0D, 0.0D);

        assertFalse(HelicopterFlightPlanner.reachedOrPassed(
                state(new Vec3(-16.0D, 30.0D, 0.0D), new Vec3(0.8D, 0.0D, 0.0D),
                        -90.0F, 0.0F, 0.0F, false, true, 0.12F, 24.0D),
                destination, false));
        assertTrue(HelicopterFlightPlanner.reachedOrPassed(
                state(new Vec3(-2.0D, 30.0D, 0.0D), new Vec3(0.8D, 0.0D, 0.0D),
                        -90.0F, 0.0F, 0.0F, false, true, 0.12F, 24.0D),
                destination, false));
        assertTrue(HelicopterFlightPlanner.reachedOrPassed(
                state(new Vec3(6.0D, 30.0D, 0.0D), new Vec3(0.8D, 0.0D, 0.0D),
                        -90.0F, 0.0F, 0.0F, false, true, 0.12F, 24.0D),
                destination, false));
        assertFalse(HelicopterFlightPlanner.reachedOrPassed(
                state(new Vec3(12.0D, 30.0D, 0.0D), new Vec3(0.8D, 0.0D, 0.0D),
                        -90.0F, 0.0F, 0.0F, false, true, 0.12F, 24.0D),
                destination, false));
    }

    @Test
    void movementCompletionCanRetainThreeDimensionalClimbSemantics() {
        HelicopterFlightPlanner.AircraftState belowDestination = state(
                new Vec3(-2.0D, 15.0D, 0.0D), new Vec3(0.4D, 0.0D, 0.0D),
                -90.0F, 0.0F, 0.0F, false, true, 0.12F, 9.0D);
        Vec3 destination = new Vec3(0.0D, 30.0D, 0.0D);

        assertTrue(HelicopterFlightPlanner.reachedOrPassed(belowDestination, destination, false));
        assertFalse(HelicopterFlightPlanner.reachedOrPassed(belowDestination, destination, true));
    }

    @Test
    void interruptedDivePassContinuesAsTargetFacingRadialRetreat() {
        Vec3 targetCenter = new Vec3(0.0D, 2.0D, 0.0D);
        HelicopterFlightPlanner.AircraftState firstState = state(
                new Vec3(10.0D, 28.0D, 0.0D), Vec3.ZERO,
                90.0F, 0.0F, 0.0F, true, true, 0.04F, 22.0D);
        HelicopterFlightPlanner.Plan continuation =
                HelicopterFlightPlanner.retreatContinuationPlan(firstState, targetCenter, 28.0D, 42.0D);

        assertEquals(new Vec3(42.0D, 28.0D, 0.0D), continuation.desiredPosition());
        assertEquals(targetCenter, continuation.aimPoint());
        assertEquals(HelicopterFlightPlanner.Phase.APPROACH, continuation.phase());
        assertTrue(continuation.stopAtDestination());
        assertFalse(continuation.flightRequest().divePitch());
        VehicleFlightInput reverse = new HelicopterFlightControl()
                .command(firstState, continuation.flightRequest(), HelicopterEnvironment.NONE);
        assertEquals(0.0D, reverse.mouseX(), EPSILON);
        assertTrue(reverse.mouseY() < 0.0D);

        HelicopterFlightPlanner.Plan rotated = HelicopterFlightPlanner.retreatContinuationPlan(
                state(new Vec3(0.0D, 28.0D, 10.0D), Vec3.ZERO,
                        180.0F, 0.0F, 0.0F, true, true, 0.04F, 22.0D),
                targetCenter, 28.0D, 42.0D);
        assertEquals(new Vec3(0.0D, 28.0D, 42.0D), rotated.desiredPosition());
        assertEquals(targetCenter, rotated.aimPoint());

        HelicopterFlightPlanner.Plan inBand = HelicopterFlightPlanner.retreatContinuationPlan(
                state(new Vec3(35.0D, 28.0D, 0.0D), Vec3.ZERO,
                        90.0F, 0.0F, 0.0F, true, true, 0.04F, 22.0D),
                targetCenter, 28.0D, 42.0D);
        assertEquals(HelicopterFlightPlanner.Phase.COMBAT_HOVER, inBand.phase());
        assertEquals(new Vec3(35.0D, 28.0D, 0.0D), inBand.desiredPosition());
        assertEquals(targetCenter, inBand.aimPoint());
    }

    @Test
    void stableHoverIsReassertedEvenDuringToggleCooldown() {
        HelicopterFlightControl control = new HelicopterFlightControl();
        HelicopterFlightPlanner.Plan hover = plan(
                new Vec3(0.0D, 28.0D, 0.0D), new Vec3(24.0D, 1.0D, 16.0D),
                0.0D, true, true, HelicopterFlightPlanner.Phase.COMBAT_HOVER);

        VehicleFlightInput enter = control.command(
                state(new Vec3(0.0D, 28.0D, 0.0D), new Vec3(0.4D, 0.0D, 0.0D),
                        0.0F, 0.0F, 0.0F, false, true, 0.12F, 22.0D),
                hover.flightRequest(), HelicopterEnvironment.NONE);
        VehicleFlightInput maintain = control.command(
                state(new Vec3(0.0D, 28.0D, 0.0D), new Vec3(0.3D, 0.0D, 0.0D),
                        0.0F, 0.0F, 0.0F, true, true, 0.12F, 22.0D),
                hover.flightRequest(), HelicopterEnvironment.NONE);
        VehicleFlightInput reenter = control.command(
                state(new Vec3(0.0D, 28.0D, 0.0D), new Vec3(0.25D, 0.0D, 0.0D),
                        0.0F, 0.0F, 0.0F, false, true, 0.12F, 22.0D),
                hover.flightRequest(), HelicopterEnvironment.NONE);

        assertTrue(enter.input().up());
        assertFalse(maintain.input().up());
        assertTrue(reenter.input().up());
    }

    @Test
    void hoverAltitudeCorrectionDoesNotRequireACombatTarget() {
        for (boolean hasTarget : new boolean[]{false, true}) {
            HelicopterFlightPlanner.Plan hover = plan(
                    new Vec3(0.0D, 18.0D, 0.0D), new Vec3(16.0D, 18.0D, 0.0D),
                    0.0D, true, hasTarget, HelicopterFlightPlanner.Phase.COMBAT_HOVER);
            VehicleFlightInput climb = new HelicopterFlightControl().command(
                    state(new Vec3(0.0D, 14.0D, 0.0D), Vec3.ZERO,
                            -90.0F, 0.0F, 0.0F, true, true, 0.09F, 14.0D),
                    hover.flightRequest(), HelicopterEnvironment.NONE);
            VehicleFlightInput settled = new HelicopterFlightControl().command(
                    state(new Vec3(0.0D, 18.0D, 0.0D), Vec3.ZERO,
                            -90.0F, 0.0F, 0.0F, true, true, 0.09F, 18.0D),
                    hover.flightRequest(), HelicopterEnvironment.NONE);
            VehicleFlightInput rising = new HelicopterFlightControl().command(
                    state(new Vec3(0.0D, 14.0D, 0.0D), new Vec3(0.0D, 0.75D, 0.0D),
                            -90.0F, 0.0F, 0.0F, true, true, 0.09F, 14.0D),
                    hover.flightRequest(), HelicopterEnvironment.NONE);

            assertTrue(climb.input().forward(), "hasTarget=" + hasTarget);
            assertFalse(climb.input().down());
            assertFalse(settled.input().forward());
            assertFalse(settled.input().down());
            assertFalse(rising.input().forward());
            assertTrue(rising.input().down());
        }
    }

    @Test
    void ordinaryCruiseWaitsForHoverReleaseCooldown() {
        HelicopterFlightControl control = new HelicopterFlightControl();
        Vec3 destination = new Vec3(40.0D, 28.0D, 0.0D);
        HelicopterFlightPlanner.Plan hover = plan(
                new Vec3(0.0D, 28.0D, 0.0D), destination,
                0.0D, true, true, HelicopterFlightPlanner.Phase.COMBAT_HOVER);
        HelicopterFlightPlanner.Plan cruise = plan(
                destination, destination, 0.68D, false, true, HelicopterFlightPlanner.Phase.APPROACH);
        assertTrue(control.command(
                state(new Vec3(0.0D, 28.0D, 0.0D), Vec3.ZERO,
                        -90.0F, 0.0F, 0.0F, false, true, 0.09F, 28.0D),
                hover.flightRequest(), HelicopterEnvironment.NONE).input().up());

        HelicopterFlightPlanner.AircraftState hovering = state(
                new Vec3(0.0D, 28.0D, 0.0D), new Vec3(0.1D, 0.0D, 0.0D),
                -90.0F, 0.0F, 0.0F, true, true, 0.09F, 28.0D);
        for (int elapsed = 1; elapsed < 20; elapsed++) {
            assertFalse(control.command(hovering, cruise.flightRequest(), HelicopterEnvironment.NONE)
                    .input().up(), "elapsed=" + elapsed);
        }
        assertTrue(control.command(hovering, cruise.flightRequest(), HelicopterEnvironment.NONE).input().up());
        assertFalse(control.command(
                state(hovering.position(), hovering.velocity(), -90.0F, 0.0F, 0.0F,
                        false, true, 0.09F, 28.0D),
                cruise.flightRequest(), HelicopterEnvironment.NONE).input().up());
    }

    @Test
    void softArrivalBrakingDoesNotReleaseHoverDuringCooldown() {
        HelicopterFlightControl control = new HelicopterFlightControl();
        Vec3 destination = new Vec3(40.0D, 28.0D, 0.0D);
        HelicopterFlightPlanner.Plan hover = plan(
                new Vec3(34.0D, 28.0D, 0.0D), destination,
                0.0D, true, true, HelicopterFlightPlanner.Phase.COMBAT_HOVER);
        assertTrue(control.command(
                state(new Vec3(34.0D, 28.0D, 0.0D), Vec3.ZERO,
                        -90.0F, 0.0F, 0.0F, false, true, 0.09F, 28.0D),
                hover.flightRequest(), HelicopterEnvironment.NONE).input().up());
        HelicopterFlightPlanner.Plan arrival = plan(
                destination, destination, 0.68D, true, true, HelicopterFlightPlanner.Phase.APPROACH);
        VehicleFlightInput braking = control.command(
                state(new Vec3(34.0D, 28.0D, 0.0D), new Vec3(0.7D, 0.0D, 0.0D),
                        -90.0F, 0.0F, 0.0F, true, true, 0.09F, 28.0D),
                arrival.flightRequest(), HelicopterEnvironment.NONE);

        assertFalse(braking.input().up());
        assertFalse(braking.input().forward());
        assertTrue(braking.mouseY() < 0.0D);
    }

    @Test
    void committedDivesLeaveHoverWithoutCooldownDelay() {
        for (HelicopterFlightPlanner.Phase phase : new HelicopterFlightPlanner.Phase[]{
                HelicopterFlightPlanner.Phase.ATTACK_RUN, HelicopterFlightPlanner.Phase.DIVE_PASS}) {
            HelicopterFlightControl control = new HelicopterFlightControl();
            HelicopterFlightPlanner.Plan hover = plan(
                    new Vec3(0.0D, 28.0D, 0.0D), new Vec3(40.0D, 2.0D, 0.0D),
                    0.0D, true, true, HelicopterFlightPlanner.Phase.COMBAT_HOVER);
            assertTrue(control.command(
                    state(new Vec3(0.0D, 28.0D, 0.0D), Vec3.ZERO,
                            -90.0F, 0.0F, 0.0F, false, true, 0.09F, 28.0D),
                    hover.flightRequest(), HelicopterEnvironment.NONE).input().up());
            HelicopterFlightPlanner.Plan dive = plan(
                    new Vec3(80.0D, 28.0D, 0.0D), new Vec3(40.0D, 2.0D, 0.0D),
                    1.0D, false, true, phase);
            VehicleFlightInput committed = control.command(
                    state(new Vec3(0.0D, 28.0D, 0.0D), Vec3.ZERO,
                            -90.0F, 0.0F, 0.0F, true, true, 0.09F, 28.0D),
                    dive.flightRequest(), HelicopterEnvironment.NONE);

            assertTrue(committed.input().up(), phase.name());
            assertTrue(committed.input().forward());
            assertTrue(committed.mouseY() > 0.0D);
        }
    }

    @Test
    void bothDiveExitsReusePullOutControlsRegardlessOfTheNextIntent() {
        Vec3 position = new Vec3(0.0D, 28.0D, 0.0D);
        Vec3 aimPoint = new Vec3(30.0D, 2.0D, 0.0D);
        HelicopterFlightPlanner.AircraftState tilted = state(
                position, new Vec3(0.85D, -0.16D, 0.0D),
                -90.0F, 60.8F, -0.3F, false, true, 0.12F, 28.0D);
        HelicopterFlightPlanner.Plan pullOut = plan(
                position, aimPoint, 0.0D, true, true, HelicopterFlightPlanner.Phase.PULL_OUT);
        VehicleFlightInput expected = new HelicopterFlightControl().command(
                tilted, pullOut.flightRequest(), HelicopterEnvironment.NONE);
        assertTrue(expected.input().up());
        assertTrue(expected.input().forward());
        assertTrue(expected.mouseY() < -2.25D);

        for (HelicopterFlightPlanner.Phase dive : new HelicopterFlightPlanner.Phase[]{
                HelicopterFlightPlanner.Phase.ATTACK_RUN, HelicopterFlightPlanner.Phase.DIVE_PASS}) {
            for (HelicopterFlightPlanner.Phase next : new HelicopterFlightPlanner.Phase[]{
                    HelicopterFlightPlanner.Phase.COMBAT_HOVER, HelicopterFlightPlanner.Phase.FIRE_HOLD,
                    HelicopterFlightPlanner.Phase.APPROACH, HelicopterFlightPlanner.Phase.IDLE}) {
                HelicopterFlightControl control = new HelicopterFlightControl();
                control.command(tilted, plan(
                        new Vec3(70.0D, 28.0D, 0.0D), aimPoint, 1.0D, false, true, dive)
                        .flightRequest(), HelicopterEnvironment.NONE);
                VehicleFlightInput actual = control.command(tilted, plan(
                        position, aimPoint, 0.0D, true, next != HelicopterFlightPlanner.Phase.IDLE, next)
                        .flightRequest(), HelicopterEnvironment.NONE);
                assertEquals(expected, actual, dive + " -> " + next);
            }
        }
    }

    @Test
    void postDiveControlsWaitForBothAttitudeAndHorizontalSpeedToSettle() {
        Vec3 position = new Vec3(0.0D, 28.0D, 0.0D);
        Vec3 aimPoint = new Vec3(30.0D, 2.0D, 0.0D);
        HelicopterFlightPlanner.Plan hover = plan(
                position, aimPoint, 0.0D, true, true, HelicopterFlightPlanner.Phase.COMBAT_HOVER);
        HelicopterFlightPlanner.Plan pullOut = plan(
                position, aimPoint, 0.0D, true, true, HelicopterFlightPlanner.Phase.PULL_OUT);
        HelicopterFlightControl control = new HelicopterFlightControl();
        control.command(state(position, Vec3.ZERO,
                        -90.0F, 0.0F, 0.0F, false, true, 0.12F, 28.0D),
                plan(aimPoint, aimPoint, 1.0D, false, true, HelicopterFlightPlanner.Phase.DIVE_PASS)
                        .flightRequest(), HelicopterEnvironment.NONE);

        HelicopterFlightPlanner.AircraftState[] unsettled = {
                state(position, new Vec3(0.8D, 0.0D, 0.0D),
                        -90.0F, 0.0F, 0.0F, true, true, 0.12F, 28.0D),
                state(position, new Vec3(0.01D, 0.0D, 0.0D),
                        -90.0F, 18.0F, 0.0F, true, true, 0.12F, 28.0D)
        };
        for (HelicopterFlightPlanner.AircraftState aircraft : unsettled) {
            assertEquals(new HelicopterFlightControl().command(
                            aircraft, pullOut.flightRequest(), HelicopterEnvironment.NONE),
                    control.command(aircraft, hover.flightRequest(), HelicopterEnvironment.NONE));
        }
        HelicopterFlightPlanner.AircraftState settled = state(
                position, Vec3.ZERO, -90.0F, 0.0F, 0.0F, true, true, 0.12F, 28.0D);
        assertEquals(new HelicopterFlightControl().command(
                        settled, hover.flightRequest(), HelicopterEnvironment.NONE),
                control.command(settled, hover.flightRequest(), HelicopterEnvironment.NONE));
        assertEquals(new HelicopterFlightControl().command(
                        unsettled[1], hover.flightRequest(), HelicopterEnvironment.NONE),
                control.command(unsettled[1], hover.flightRequest(), HelicopterEnvironment.NONE));
    }

    @Test
    void lowAltitudeSafetyKeepsSharedPostDiveLeveling() {
        Vec3 position = new Vec3(0.0D, 8.0D, 0.0D);
        Vec3 destination = new Vec3(0.0D, 24.0D, 0.0D);
        Vec3 aimPoint = new Vec3(30.0D, 2.0D, 0.0D);
        HelicopterFlightPlanner.AircraftState aircraft = state(
                position, new Vec3(0.6D, -0.1D, 0.0D),
                -90.0F, 18.0F, 0.0F, false, true, 0.12F, 8.0D);
        VehicleFlightInput expected = new HelicopterFlightControl().command(
                aircraft, plan(destination, aimPoint, 0.0D, true, true,
                        HelicopterFlightPlanner.Phase.PULL_OUT).flightRequest(),
                HelicopterEnvironment.NONE);
        assertTrue(expected.input().forward());
        assertTrue(expected.input().up());
        assertFalse(expected.input().down());
        assertTrue(expected.mouseY() < -2.25D);

        for (HelicopterFlightPlanner.Phase dive : new HelicopterFlightPlanner.Phase[]{
                HelicopterFlightPlanner.Phase.ATTACK_RUN, HelicopterFlightPlanner.Phase.DIVE_PASS}) {
            HelicopterFlightControl control = new HelicopterFlightControl();
            control.command(aircraft, plan(aimPoint, aimPoint, 1.0D, false, true, dive)
                    .flightRequest(), HelicopterEnvironment.NONE);
            assertEquals(expected, control.command(
                    aircraft, plan(destination, aimPoint, 0.0D, true, true,
                            HelicopterFlightPlanner.Phase.TAKEOFF).flightRequest(),
                    HelicopterEnvironment.NONE), dive.name());
        }
    }

    @Test
    void switchingBetweenDiveProfilesDoesNotStartPullOutControls() {
        Vec3 position = new Vec3(0.0D, 28.0D, 0.0D);
        Vec3 aimPoint = new Vec3(30.0D, 2.0D, 0.0D);
        HelicopterFlightPlanner.AircraftState aircraft = state(
                position, new Vec3(0.85D, -0.16D, 0.0D),
                -90.0F, 60.8F, -0.3F, true, true, 0.12F, 28.0D);
        HelicopterFlightControl control = new HelicopterFlightControl();
        for (HelicopterFlightPlanner.Phase phase : new HelicopterFlightPlanner.Phase[]{
                HelicopterFlightPlanner.Phase.DIVE_PASS, HelicopterFlightPlanner.Phase.ATTACK_RUN,
                HelicopterFlightPlanner.Phase.DIVE_PASS}) {
            HelicopterFlightPlanner.Plan dive = plan(
                    new Vec3(70.0D, 28.0D, 0.0D), aimPoint, 1.0D, false, true, phase);
            VehicleFlightInput output = control.command(aircraft, dive.flightRequest(), HelicopterEnvironment.NONE);
            assertEquals(new HelicopterFlightControl().command(
                    aircraft, dive.flightRequest(), HelicopterEnvironment.NONE), output);
            assertTrue(output.input().forward());
            assertTrue(output.input().sprint());
            assertTrue(output.mouseY() > 0.0D);
        }
    }

    @Test
    void resettingFlightControlClearsPendingDiveStabilization() {
        Vec3 position = new Vec3(0.0D, 28.0D, 0.0D);
        Vec3 aimPoint = new Vec3(30.0D, 2.0D, 0.0D);
        HelicopterFlightPlanner.AircraftState aircraft = state(
                position, new Vec3(0.8D, -0.1D, 0.0D),
                -90.0F, 58.0F, 0.0F, true, true, 0.12F, 28.0D);
        HelicopterFlightControl control = new HelicopterFlightControl();
        control.command(aircraft, plan(
                aimPoint, aimPoint, 1.0D, false, true, HelicopterFlightPlanner.Phase.ATTACK_RUN)
                .flightRequest(), HelicopterEnvironment.NONE);
        control.reset();
        HelicopterFlightPlanner.Plan hover = plan(
                position, aimPoint, 0.0D, true, true, HelicopterFlightPlanner.Phase.COMBAT_HOVER);
        assertEquals(new HelicopterFlightControl().command(
                        aircraft, hover.flightRequest(), HelicopterEnvironment.NONE),
                control.command(aircraft, hover.flightRequest(), HelicopterEnvironment.NONE));
    }

    @Test
    void safetyHoverCanReenterDuringCooldown() {
        HelicopterFlightPlanner.AircraftState[] hazards = {
                state(new Vec3(0.0D, 28.0D, 0.0D), new Vec3(0.7D, 0.0D, 0.0D),
                        -90.0F, 0.0F, 0.0F, false, true, 0.09F, 28.0D),
                state(new Vec3(0.0D, 28.0D, 0.0D), new Vec3(-0.3D, 0.0D, 0.0D),
                        -90.0F, 0.0F, 0.0F, false, true, 0.09F, 28.0D),
                state(new Vec3(0.0D, 28.0D, 0.0D), new Vec3(0.0D, 0.0D, 0.8D),
                        -90.0F, 0.0F, 0.0F, false, true, 0.09F, 28.0D),
                state(new Vec3(0.0D, 8.0D, 0.0D), Vec3.ZERO,
                        -90.0F, 0.0F, 0.0F, false, true, 0.09F, 8.0D)
        };
        HelicopterFlightPlanner.Plan hover = plan(
                new Vec3(0.0D, 28.0D, 0.0D), new Vec3(40.0D, 28.0D, 0.0D),
                0.0D, true, true, HelicopterFlightPlanner.Phase.COMBAT_HOVER);
        HelicopterFlightPlanner.Plan cruise = plan(
                new Vec3(40.0D, 28.0D, 0.0D), new Vec3(40.0D, 28.0D, 0.0D),
                0.68D, false, true, HelicopterFlightPlanner.Phase.APPROACH);
        for (int i = 0; i < hazards.length; i++) {
            HelicopterFlightControl control = new HelicopterFlightControl();
            assertTrue(control.command(
                    state(new Vec3(0.0D, 28.0D, 0.0D), Vec3.ZERO,
                            -90.0F, 0.0F, 0.0F, false, true, 0.09F, 28.0D),
                    hover.flightRequest(), HelicopterEnvironment.NONE).input().up());
            HelicopterEnvironment environment = i == 0
                    ? new HelicopterEnvironment(true, true, false, 0, Double.NaN, 2.0D, 2.0D)
                    : HelicopterEnvironment.NONE;
            assertTrue(control.command(hazards[i], cruise.flightRequest(), environment).input().up(),
                    "hazard=" + i);
        }
    }

    @Test
    void lateralHoverRetainsItsEntryAndExitHysteresis() {
        HelicopterFlightPlanner.Plan cruise = plan(
                new Vec3(40.0D, 28.0D, 0.0D), new Vec3(40.0D, 28.0D, 0.0D),
                0.68D, false, true, HelicopterFlightPlanner.Phase.APPROACH);
        double[] lateralSpeeds = {0.8D, 0.3D, 0.3D, 0.1D};
        boolean[] hovering = {false, true, false, true};
        boolean[] shouldToggle = {true, false, false, true};
        for (int i = 0; i < lateralSpeeds.length; i++) {
            VehicleFlightInput output = new HelicopterFlightControl().command(
                    state(new Vec3(0.0D, 28.0D, 0.0D), new Vec3(0.0D, 0.0D, lateralSpeeds[i]),
                            -90.0F, 0.0F, 0.0F, hovering[i], true, 0.09F, 28.0D),
                    cruise.flightRequest(), HelicopterEnvironment.NONE);
            assertEquals(shouldToggle[i], output.input().up(), "sample=" + i);
        }
    }

    private static Case[] cases() {
        return new Case[]{
                new Case(
                        "takeoff-start",
                        state(new Vec3(0.0D, 8.0D, 0.0D), Vec3.ZERO, 0.0F, 0.0F, 0.0F,
                                false, false, 0.0F, 4.0D),
                        plan(new Vec3(0.0D, 24.0D, 0.0D), new Vec3(0.0D, 24.0D, 16.0D),
                                0.0D, true, false, HelicopterFlightPlanner.Phase.TAKEOFF),
                        HelicopterEnvironment.NONE,
                        "true,false,false,false,false,false,false,false,false,false",
                        0.0D,
                        0.0D),
                new Case(
                        "combat-hover",
                        state(new Vec3(0.0D, 28.0D, 0.0D), new Vec3(0.05D, 0.0D, 0.1D),
                                10.0F, 2.0F, 4.0F, false, true, 0.12F, 22.0D),
                        plan(new Vec3(0.0D, 28.0D, 0.0D), new Vec3(24.0D, 1.0D, 16.0D),
                                0.0D, true, true, HelicopterFlightPlanner.Phase.COMBAT_HOVER),
                        HelicopterEnvironment.NONE,
                        "false,false,true,false,false,false,true,false,false,false",
                        -34.52326854D,
                        -0.36363636D),
                new Case(
                        "approach-forward",
                        state(new Vec3(0.0D, 30.0D, 0.0D), new Vec3(0.1D, 0.0D, 0.05D),
                                -35.0F, 0.0F, 3.0F, false, true, 0.04F, 24.0D),
                        plan(new Vec3(30.0D, 30.0D, 20.0D), new Vec3(30.0D, 2.0D, 20.0D),
                                0.68D, true, true, HelicopterFlightPlanner.Phase.APPROACH),
                        HelicopterEnvironment.NONE,
                        "true,false,true,false,false,false,false,false,false,false",
                        -11.12348452D,
                        5.71774794D),
                new Case(
                        "approach-trim-down",
                        state(new Vec3(0.0D, 36.0D, 0.0D), new Vec3(0.3D, 0.12D, 0.0D),
                                -70.0F, 4.0F, 2.0F, false, true, 0.13F, 30.0D),
                        plan(new Vec3(30.0D, 28.0D, 0.0D), new Vec3(30.0D, 2.0D, 0.0D),
                                0.68D, true, true, HelicopterFlightPlanner.Phase.APPROACH),
                        HelicopterEnvironment.NONE,
                        "false,false,true,false,false,false,false,true,false,false",
                        -10.75396825D,
                        4.50706436D),
                new Case(
                        "arrival-brake",
                        state(new Vec3(27.0D, 30.0D, 0.0D), new Vec3(0.42D, 0.0D, 0.0D),
                                -90.0F, 4.0F, 0.0F, false, true, 0.15F, 24.0D),
                        plan(new Vec3(30.0D, 30.0D, 0.0D), new Vec3(30.0D, 2.0D, 0.0D),
                                0.68D, true, true, HelicopterFlightPlanner.Phase.APPROACH),
                        HelicopterEnvironment.NONE,
                        "false,false,false,false,false,false,true,false,false,false",
                        0.0D,
                        -2.26461538D),
                new Case(
                        "obstacle-brake",
                        state(new Vec3(0.0D, 30.0D, 0.0D), new Vec3(0.7D, 0.0D, 0.0D),
                                -90.0F, 8.0F, 0.0F, false, true, 0.16F, 24.0D),
                        plan(new Vec3(30.0D, 30.0D, 0.0D), new Vec3(30.0D, 2.0D, 0.0D),
                                0.78D, false, true, HelicopterFlightPlanner.Phase.APPROACH),
                        new HelicopterEnvironment(false, true, false, 0, Double.NaN, 2.0D, 2.0D),
                        "false,false,false,false,false,false,true,false,false,false",
                        0.0D,
                        -4.0D),
                new Case(
                        "lateral-stabilize",
                        state(new Vec3(0.0D, 30.0D, 0.0D), new Vec3(0.0D, 0.0D, 0.8D),
                                -90.0F, 6.0F, 12.0F, false, true, 0.16F, 24.0D),
                        plan(new Vec3(30.0D, 30.0D, 0.0D), new Vec3(30.0D, 2.0D, 0.0D),
                                0.78D, false, true, HelicopterFlightPlanner.Phase.APPROACH),
                        HelicopterEnvironment.NONE,
                        "false,false,false,false,false,false,true,false,false,false",
                        1.5D,
                        -1.84615385D),
                new Case(
                        "attack-run-stable-fire",
                        state(new Vec3(0.0D, 42.0D, 0.0D), new Vec3(0.55D, -0.05D, 0.0D),
                                -90.0F, 10.0F, 5.0F, false, true, 0.2F, 36.0D),
                        plan(new Vec3(30.0D, 28.0D, 0.0D), new Vec3(30.0D, 2.0D, 0.0D),
                                1.0D, false, true, HelicopterFlightPlanner.Phase.ATTACK_RUN),
                        HelicopterEnvironment.NONE,
                        "true,false,false,false,false,true,false,false,false,false",
                        0.625D,
                        26.0D),
                new Case(
                        "pull-out",
                        state(new Vec3(0.0D, 18.0D, 0.0D), new Vec3(0.4D, -0.6D, 0.0D),
                                -90.0F, 45.0F, 8.0F, false, true, 0.1F, 12.0D),
                        plan(new Vec3(0.0D, 28.0D, 0.0D), new Vec3(30.0D, 2.0D, 0.0D),
                                0.0D, true, true, HelicopterFlightPlanner.Phase.PULL_OUT),
                        HelicopterEnvironment.NONE,
                        "true,false,false,false,false,false,true,false,false,false",
                        1.0D,
                        -8.0D),
                new Case(
                        "fire-hold",
                        state(new Vec3(0.0D, 30.0D, 0.0D), new Vec3(0.05D, 0.0D, 0.0D),
                                -90.0F, 7.0F, 0.0F, false, true, 0.12F, 24.0D),
                        plan(new Vec3(0.0D, 30.0D, 0.0D), new Vec3(30.0D, 2.0D, 0.0D),
                                0.0D, true, true, HelicopterFlightPlanner.Phase.FIRE_HOLD),
                        HelicopterEnvironment.NONE,
                        "false,false,false,false,false,false,true,false,false,false",
                        0.0D,
                        0.0D)
        };
    }

    private static HelicopterFlightPlanner.AircraftState state(Vec3 position, Vec3 velocity, float yaw,
                                                              float pitch, float roll, boolean hoverMode,
                                                              boolean engineStarted, float power, double altitude) {
        return new HelicopterFlightPlanner.AircraftState(position, velocity, yaw, pitch, roll,
                hoverMode, engineStarted, power, altitude);
    }

    private static HelicopterFlightPlanner.Plan plan(Vec3 desiredPosition, Vec3 aimPoint, double cruiseSpeed,
                                                     boolean stopAtDestination, boolean hasTarget,
                                                     HelicopterFlightPlanner.Phase phase) {
        return new HelicopterFlightPlanner.Plan(desiredPosition, aimPoint, cruiseSpeed,
                stopAtDestination, hasTarget, phase);
    }

    private static String bits(VehicleInput input) {
        return String.format(Locale.ROOT,
                "%b,%b,%b,%b,%b,%b,%b,%b,%b,%b",
                input.forward(),
                input.back(),
                input.left(),
                input.right(),
                input.brake(),
                input.sprint(),
                input.up(),
                input.down(),
                input.fire(),
                input.decoy());
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        return Math.hypot(first.x - second.x, first.z - second.z);
    }

    private record Case(String name, HelicopterFlightPlanner.AircraftState state,
                        HelicopterFlightPlanner.Plan plan, HelicopterEnvironment environment, String expectedInput,
                        double expectedMouseX, double expectedMouseY) {
    }
}
