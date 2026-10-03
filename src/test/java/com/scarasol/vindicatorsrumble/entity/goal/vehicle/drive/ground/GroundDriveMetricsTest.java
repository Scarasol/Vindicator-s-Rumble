package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground;

import com.scarasol.vindicatorsrumble.api.vehicle.SeatHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleDimensions;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleInput;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleMovementType;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundDriveMetricsTest {
    private static final String REPORT_FILE = "ground-drive-metrics.txt";
    private static final int MANEUVER_CASES = 1_600;
    private static final int TURN_CASES = 1_200;
    private static final int RAM_CASES = 1_200;
    private static final int CLOSED_LOOP_CASES = 720;

    @BeforeAll
    static void resetReport() throws IOException {
        Files.deleteIfExists(Path.of("build", "test-metrics", REPORT_FILE));
    }

    @Test
    void randomizedManeuverContractsPreferLargerForeAftClearance() throws IOException {
        Random random = new Random(0x6A0DCAFE);
        StringBuilder failures = new StringBuilder();
        StringBuilder report = new StringBuilder("ground-maneuver-contracts\n");
        int rearDominant = 0;
        int frontDominant = 0;
        int braking = 0;
        int backStraightTransitions = 0;
        int backTurnTransitions = 0;

        for (int i = 0; i < MANEUVER_CASES; i++) {
            double front = randomRange(random, 0.2D, 5.4D);
            double back = front + randomRange(random, 0.05D, 4.5D);
            GroundClearance clearance = new GroundClearance(front, back, randomRange(random, 0.4D, 7.0D), randomRange(random, 0.4D, 7.0D));
            GroundManeuverStep initial = GroundDriveStrategy.selectInitialManeuverStep(clearance, false);
            GroundManeuverStep preferredStraight = GroundDriveStrategy.preferredMovementStep(clearance, false);
            GroundManeuverStep preferredTurn = GroundDriveStrategy.preferredMovementStep(clearance, true);
            if (initial == GroundManeuverStep.BACK_STRAIGHT
                    && preferredStraight == GroundManeuverStep.BACK_STRAIGHT
                    && preferredTurn == GroundManeuverStep.BACK_TURN) {
                rearDominant++;
            } else {
                appendManeuverFailure(failures, "rear-dominant", i, clearance, initial, preferredStraight, preferredTurn);
            }
        }

        for (int i = 0; i < MANEUVER_CASES; i++) {
            double back = randomRange(random, 0.2D, 5.4D);
            double front = back + randomRange(random, 0.05D, 4.5D);
            GroundClearance clearance = new GroundClearance(front, back, randomRange(random, 0.4D, 7.0D), randomRange(random, 0.4D, 7.0D));
            GroundManeuverStep initial = GroundDriveStrategy.selectInitialManeuverStep(clearance, false);
            GroundManeuverStep preferredStraight = GroundDriveStrategy.preferredMovementStep(clearance, false);
            GroundManeuverStep preferredTurn = GroundDriveStrategy.preferredMovementStep(clearance, true);
            if (initial == GroundManeuverStep.SWING_FORWARD
                    && preferredStraight == GroundManeuverStep.SWING_FORWARD
                    && preferredTurn == GroundManeuverStep.SWING_FORWARD) {
                frontDominant++;
            } else {
                appendManeuverFailure(failures, "front-dominant", i, clearance, initial, preferredStraight, preferredTurn);
            }
        }

        for (int i = 0; i < MANEUVER_CASES; i++) {
            GroundClearance clearance = randomClearance(random);
            GroundManeuverStep initial = GroundDriveStrategy.selectInitialManeuverStep(clearance, true);
            if (initial == GroundManeuverStep.BRAKE) {
                braking++;
            } else {
                appendManeuverFailure(failures, "braking", i, clearance, initial, null, null);
            }
        }

        for (int i = 0; i < MANEUVER_CASES; i++) {
            double front = randomRange(random, 0.2D, 4.0D);
            GroundClearance clearance = new GroundClearance(front, front + randomRange(random, 0.1D, 5.0D), randomRange(random, 0.6D, 7.0D), randomRange(random, 0.6D, 7.0D));
            int ticks = random.nextInt(10);
            GroundManeuverStep next = GroundDriveStrategy.nextBackStraightStep(clearance, ticks, 0);
            if (next == GroundManeuverStep.BACK_STRAIGHT) {
                backStraightTransitions++;
            } else {
                appendTransitionFailure(failures, "short-back-straight", i, clearance, ticks, 0, next);
            }
        }

        for (int i = 0; i < MANEUVER_CASES; i++) {
            double front = randomRange(random, 0.2D, 4.0D);
            GroundClearance clearance = new GroundClearance(front, front + randomRange(random, 0.1D, 5.0D), randomRange(random, 0.6D, 7.0D), randomRange(random, 0.6D, 7.0D));
            int ticks = randomRangeInt(random, 10, 30);
            int noProgressTicks = random.nextBoolean() ? 0 : 5;
            GroundManeuverStep next = GroundDriveStrategy.nextBackStraightStep(clearance, ticks, noProgressTicks);
            if (next == GroundManeuverStep.BACK_TURN) {
                backTurnTransitions++;
            } else {
                appendTransitionFailure(failures, "long-back-turn", i, clearance, ticks, noProgressTicks, next);
            }
        }

        report.append("rearDominant=").append(rearDominant).append('/').append(MANEUVER_CASES).append('\n');
        report.append("frontDominant=").append(frontDominant).append('/').append(MANEUVER_CASES).append('\n');
        report.append("braking=").append(braking).append('/').append(MANEUVER_CASES).append('\n');
        report.append("backStraightTransitions=").append(backStraightTransitions).append('/').append(MANEUVER_CASES).append('\n');
        report.append("backTurnTransitions=").append(backTurnTransitions).append('/').append(MANEUVER_CASES).append('\n');
        appendReport(REPORT_FILE, report);
        assertTrue(failures.length() == 0, report + "\nfailures:\n" + failures);
    }

    @Test
    void randomizedTurnAndInputContractsKeepBackAndForwardSteeringDistinct() throws IOException {
        Random random = new Random(0x517E5A11);
        StringBuilder failures = new StringBuilder();
        StringBuilder report = new StringBuilder("ground-turn-input-contracts\n");
        int leftOpen = 0;
        int rightOpen = 0;
        int leftSqueezed = 0;
        int rightSqueezed = 0;
        int reverseInputs = 0;
        int forwardInputs = 0;

        for (int i = 0; i < TURN_CASES; i++) {
            GroundClearance clearance = new GroundClearance(randomRange(random, 0.4D, 5.0D), randomRange(random, 0.4D, 5.0D), randomRange(random, 3.0D, 8.0D), randomRange(random, 0.4D, 1.2D));
            int direction = GroundDriveStrategy.selectManeuverTurnDirection(clearance, randomRange(random, -90.0D, 90.0D), 0);
            if (direction == -1) {
                leftOpen++;
            } else {
                appendTurnFailure(failures, "left-open", i, clearance, direction);
            }
        }

        for (int i = 0; i < TURN_CASES; i++) {
            GroundClearance clearance = new GroundClearance(randomRange(random, 0.4D, 5.0D), randomRange(random, 0.4D, 5.0D), randomRange(random, 0.4D, 1.2D), randomRange(random, 3.0D, 8.0D));
            int direction = GroundDriveStrategy.selectManeuverTurnDirection(clearance, randomRange(random, -90.0D, 90.0D), 0);
            if (direction == 1) {
                rightOpen++;
            } else {
                appendTurnFailure(failures, "right-open", i, clearance, direction);
            }
        }

        for (int i = 0; i < TURN_CASES; i++) {
            GroundClearance clearance = new GroundClearance(randomRange(random, 0.4D, 5.0D), randomRange(random, 0.4D, 5.0D), randomRange(random, 0.1D, 1.0D), randomRange(random, 2.0D, 8.0D));
            int direction = GroundDriveStrategy.selectManeuverTurnDirection(clearance, randomRange(random, -90.0D, 90.0D), -1);
            if (direction == 1) {
                leftSqueezed++;
            } else {
                appendTurnFailure(failures, "left-squeezed", i, clearance, direction);
            }
        }

        for (int i = 0; i < TURN_CASES; i++) {
            GroundClearance clearance = new GroundClearance(randomRange(random, 0.4D, 5.0D), randomRange(random, 0.4D, 5.0D), randomRange(random, 2.0D, 8.0D), randomRange(random, 0.1D, 1.0D));
            int direction = GroundDriveStrategy.selectManeuverTurnDirection(clearance, randomRange(random, -90.0D, 90.0D), 1);
            if (direction == -1) {
                rightSqueezed++;
            } else {
                appendTurnFailure(failures, "right-squeezed", i, clearance, direction);
            }
        }

        for (int i = 0; i < TURN_CASES; i++) {
            int direction = random.nextBoolean() ? -1 : 1;
            VehicleInput input = GroundSteering.maneuverBack(direction);
            boolean expectedLeft = direction > 0;
            boolean expectedRight = direction < 0;
            if (input.back() && !input.forward() && input.left() == expectedLeft && input.right() == expectedRight) {
                reverseInputs++;
            } else {
                appendInputFailure(failures, "reverse-input", i, direction, input);
            }
        }

        for (int i = 0; i < TURN_CASES; i++) {
            int direction = random.nextBoolean() ? -1 : 1;
            VehicleInput input = GroundSteering.maneuverForward(direction, random.nextBoolean());
            boolean expectedLeft = direction < 0;
            boolean expectedRight = direction > 0;
            if (input.forward() && !input.back() && input.left() == expectedLeft && input.right() == expectedRight) {
                forwardInputs++;
            } else {
                appendInputFailure(failures, "forward-input", i, direction, input);
            }
        }

        report.append("leftOpen=").append(leftOpen).append('/').append(TURN_CASES).append('\n');
        report.append("rightOpen=").append(rightOpen).append('/').append(TURN_CASES).append('\n');
        report.append("leftSqueezed=").append(leftSqueezed).append('/').append(TURN_CASES).append('\n');
        report.append("rightSqueezed=").append(rightSqueezed).append('/').append(TURN_CASES).append('\n');
        report.append("reverseInputs=").append(reverseInputs).append('/').append(TURN_CASES).append('\n');
        report.append("forwardInputs=").append(forwardInputs).append('/').append(TURN_CASES).append('\n');
        appendReport(REPORT_FILE, report);
        assertTrue(failures.length() == 0, report + "\nfailures:\n" + failures);
    }

    @Test
    void randomizedRamContractsSeparateWindupFromDirectCharge() throws IOException {
        Random random = new Random(0xCA4C0DE);
        StringBuilder failures = new StringBuilder();
        StringBuilder report = new StringBuilder("ground-ram-contracts\n");
        int startWindup = 0;
        int frontArcNoWindup = 0;
        int continueWindup = 0;
        int releaseWindup = 0;
        int chargeWithRunup = 0;
        int closeMisalignedBrake = 0;
        int windupAway = 0;

        for (int i = 0; i < RAM_CASES; i++) {
            double distanceSqr = randomRange(random, 0.25D, 35.8D);
            double yawDiff = randomSign(random) * randomRange(random, 12.25D, 178.0D);
            if (GroundDriveStrategy.shouldStartRamWindup(distanceSqr, yawDiff)) {
                startWindup++;
            } else {
                appendRamFailure(failures, "start-windup", i, distanceSqr, yawDiff, null);
            }
        }

        for (int i = 0; i < RAM_CASES; i++) {
            double distanceSqr = randomRange(random, 0.25D, 35.8D);
            double yawDiff = randomSign(random) * randomRange(random, 0.0D, 12.0D);
            if (!GroundDriveStrategy.shouldStartRamWindup(distanceSqr, yawDiff)) {
                frontArcNoWindup++;
            } else {
                appendRamFailure(failures, "front-arc-no-windup", i, distanceSqr, yawDiff, null);
            }
        }

        for (int i = 0; i < RAM_CASES; i++) {
            double distanceSqr = randomRange(random, 36.5D, 120.5D);
            double yawDiff = randomSign(random) * randomRange(random, 12.25D, 178.0D);
            if (GroundDriveStrategy.shouldContinueRamWindup(distanceSqr, yawDiff)) {
                continueWindup++;
            } else {
                appendRamFailure(failures, "continue-windup", i, distanceSqr, yawDiff, null);
            }
        }

        for (int i = 0; i < RAM_CASES; i++) {
            double distanceSqr = randomRange(random, 121.0D, 196.0D);
            double yawDiff = randomSign(random) * randomRange(random, 12.25D, 178.0D);
            if (!GroundDriveStrategy.shouldContinueRamWindup(distanceSqr, yawDiff)) {
                releaseWindup++;
            } else {
                appendRamFailure(failures, "release-windup", i, distanceSqr, yawDiff, null);
            }
        }

        for (int i = 0; i < RAM_CASES; i++) {
            TestController controller = new TestController(Vec3.ZERO, 0.0F, Vec3.ZERO, VehicleMovementType.WHEEL);
            Vec3 target = targetAt(randomRange(random, 6.2D, 24.0D), randomRange(random, -45.0D, 45.0D));
            VehicleInput input = GroundSteering.steerRamToward(controller, target, 0).input();
            if (input.forward() && !input.back() && !input.brake()) {
                chargeWithRunup++;
            } else {
                appendRamFailure(failures, "charge-runup", i, target.lengthSqr(), yawDiffTo(controller.yaw(), target), input);
            }
        }

        for (int i = 0; i < RAM_CASES; i++) {
            TestController controller = new TestController(Vec3.ZERO, 0.0F, new Vec3(0.28D, 0.0D, 0.0D), VehicleMovementType.WHEEL);
            Vec3 target = targetAt(randomRange(random, 1.2D, 5.9D), randomSign(random) * randomRange(random, 20.0D, 150.0D));
            VehicleInput input = GroundSteering.steerRamToward(controller, target, 0).input();
            if (!input.forward() && !input.back() && input.brake()) {
                closeMisalignedBrake++;
            } else {
                appendRamFailure(failures, "close-misaligned-brake", i, target.lengthSqr(), yawDiffTo(controller.yaw(), target), input);
            }
        }

        for (int i = 0; i < RAM_CASES; i++) {
            TestController controller = new TestController(Vec3.ZERO, 0.0F, Vec3.ZERO, VehicleMovementType.WHEEL);
            Vec3 target = targetAt(randomRange(random, 1.2D, 5.8D), randomRange(random, -120.0D, 120.0D));
            VehicleInput input = GroundSteering.ramWindupAway(controller, target, 0).input();
            double yawDiff = Math.abs(yawDiffTo(controller.yaw(), target));
            boolean expectedBack = yawDiff < 130.0D;
            if (input.back() == expectedBack && input.forward() != expectedBack && !input.left() && !input.right()) {
                windupAway++;
            } else {
                appendRamFailure(failures, "windup-away", i, target.lengthSqr(), yawDiff, input);
            }
        }

        report.append("startWindup=").append(startWindup).append('/').append(RAM_CASES).append('\n');
        report.append("frontArcNoWindup=").append(frontArcNoWindup).append('/').append(RAM_CASES).append('\n');
        report.append("continueWindup=").append(continueWindup).append('/').append(RAM_CASES).append('\n');
        report.append("releaseWindup=").append(releaseWindup).append('/').append(RAM_CASES).append('\n');
        report.append("chargeWithRunup=").append(chargeWithRunup).append('/').append(RAM_CASES).append('\n');
        report.append("closeMisalignedBrake=").append(closeMisalignedBrake).append('/').append(RAM_CASES).append('\n');
        report.append("windupAway=").append(windupAway).append('/').append(RAM_CASES).append('\n');
        appendReport(REPORT_FILE, report);
        assertTrue(failures.length() == 0, report + "\nfailures:\n" + failures);
    }

    @Test
    void randomizedSteeringClosedLoopMovesTowardWaypoint() throws IOException {
        Random random = new Random(0xD12EC7ED);
        StringBuilder failures = new StringBuilder();
        StringBuilder report = new StringBuilder("ground-steering-closed-loop\n");
        int accepted = 0;
        int improvedDistance = 0;
        int improvedYaw = 0;
        int reached = 0;
        int forwardCases = 0;
        int brakeCases = 0;
        double totalInitialDistance = 0.0D;
        double totalFinalDistance = 0.0D;

        for (int i = 0; i < CLOSED_LOOP_CASES; i++) {
            VehicleMovementType type = random.nextBoolean() ? VehicleMovementType.WHEEL : VehicleMovementType.TRACK;
            double distance = randomRange(random, 16.0D, 72.0D);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            Vec3 start = new Vec3(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
            float yaw = (float) (yawFacing(start, Vec3.ZERO) + randomRange(random, -115.0D, 115.0D));
            SimController controller = new SimController(start, yaw, Vec3.ZERO, type);
            double initialDistance = horizontalDistance(controller.position(), Vec3.ZERO);
            double initialYawError = Math.abs(yawDiffTo(controller.yaw(), Vec3.ZERO.subtract(controller.position())));
            ClosedLoopResult result = simulateSteerToward(controller, Vec3.ZERO, false, 120);
            double finalDistance = horizontalDistance(result.position(), Vec3.ZERO);
            double finalYawError = Math.abs(yawDiffTo(result.yaw(), Vec3.ZERO.subtract(result.position())));
            totalInitialDistance += initialDistance;
            totalFinalDistance += finalDistance;
            if (finalDistance < initialDistance) {
                improvedDistance++;
            }
            if (finalYawError < initialYawError || finalYawError <= 18.0D) {
                improvedYaw++;
            }
            if (finalDistance <= 4.0D) {
                reached++;
            }
            if (result.forwardTicks() > 0) {
                forwardCases++;
            }
            if (result.brakeTicks() > 0) {
                brakeCases++;
            }
            boolean distanceAccepted = finalDistance <= 4.0D || finalDistance <= initialDistance * 0.62D;
            boolean yawAccepted = finalDistance <= 4.0D
                    || finalYawError <= Math.max(22.0D, initialYawError * 0.72D);
            if (distanceAccepted && yawAccepted && result.forwardTicks() > 0) {
                accepted++;
            } else {
                appendClosedLoopFailure(failures, "steer-waypoint", i, type,
                        initialDistance, finalDistance, initialYawError, finalYawError, result);
            }
        }

        report.append("accepted=").append(accepted).append('/').append(CLOSED_LOOP_CASES).append('\n');
        report.append("improvedDistance=").append(improvedDistance).append('/').append(CLOSED_LOOP_CASES).append('\n');
        report.append("improvedYaw=").append(improvedYaw).append('/').append(CLOSED_LOOP_CASES).append('\n');
        report.append("reached=").append(reached).append('/').append(CLOSED_LOOP_CASES).append('\n');
        report.append("forwardCases=").append(forwardCases).append('/').append(CLOSED_LOOP_CASES).append('\n');
        report.append("brakeCases=").append(brakeCases).append('/').append(CLOSED_LOOP_CASES).append('\n');
        report.append(String.format(Locale.ROOT, "avgInitialDistance=%.3f%n", totalInitialDistance / CLOSED_LOOP_CASES));
        report.append(String.format(Locale.ROOT, "avgFinalDistance=%.3f%n", totalFinalDistance / CLOSED_LOOP_CASES));
        appendReport(REPORT_FILE, report);
        assertTrue(failures.length() == 0, report + "\nfailures:\n" + failures);
    }

    @Test
    void randomizedRamClosedLoopBuildsRunupThenCharges() throws IOException {
        Random random = new Random(0x4A6C0A11);
        StringBuilder failures = new StringBuilder();
        StringBuilder report = new StringBuilder("ground-ram-closed-loop\n");
        int accepted = 0;
        int createdRunup = 0;
        int strongRunup = 0;
        int chargedBack = 0;
        int sprinted = 0;
        double totalInitialDistance = 0.0D;
        double totalPeakDistance = 0.0D;
        double totalFinalDistance = 0.0D;

        for (int i = 0; i < CLOSED_LOOP_CASES; i++) {
            double distance = randomRange(random, 1.6D, 5.8D);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            Vec3 start = new Vec3(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
            float yaw = (float) (yawFacing(start, Vec3.ZERO) + randomSign(random) * randomRange(random, 18.0D, 105.0D));
            SimController controller = new SimController(start, yaw, Vec3.ZERO, VehicleMovementType.WHEEL);
            double initialDistance = horizontalDistance(controller.position(), Vec3.ZERO);
            ClosedLoopResult result = simulateRam(controller, Vec3.ZERO, 150);
            double peakDistance = result.maxDistance();
            double finalDistance = horizontalDistance(result.position(), Vec3.ZERO);
            totalInitialDistance += initialDistance;
            totalPeakDistance += peakDistance;
            totalFinalDistance += finalDistance;
            if (peakDistance >= initialDistance + 1.5D) {
                createdRunup++;
            }
            if (peakDistance >= initialDistance + 2.5D) {
                strongRunup++;
            }
            if (finalDistance <= peakDistance - 3.0D) {
                chargedBack++;
            }
            if (result.sprintTicks() > 0) {
                sprinted++;
            }
            boolean acceptedRunup = peakDistance >= initialDistance + 1.5D;
            boolean acceptedCharge = finalDistance <= peakDistance - 3.0D || finalDistance <= 4.0D;
            boolean noReverseChargeLock = result.forwardTicks() > 0 && result.backTicks() > 0;
            if (acceptedRunup && acceptedCharge && noReverseChargeLock) {
                accepted++;
            } else {
                appendClosedLoopFailure(failures, "ram-runup-charge", i, VehicleMovementType.WHEEL,
                        initialDistance, finalDistance, peakDistance, Math.abs(yawDiffTo(result.yaw(), Vec3.ZERO.subtract(result.position()))), result);
            }
        }

        report.append("accepted=").append(accepted).append('/').append(CLOSED_LOOP_CASES).append('\n');
        report.append("createdRunup=").append(createdRunup).append('/').append(CLOSED_LOOP_CASES).append('\n');
        report.append("strongRunup=").append(strongRunup).append('/').append(CLOSED_LOOP_CASES).append('\n');
        report.append("chargedBack=").append(chargedBack).append('/').append(CLOSED_LOOP_CASES).append('\n');
        report.append("sprinted=").append(sprinted).append('/').append(CLOSED_LOOP_CASES).append('\n');
        report.append(String.format(Locale.ROOT, "avgInitialDistance=%.3f%n", totalInitialDistance / CLOSED_LOOP_CASES));
        report.append(String.format(Locale.ROOT, "avgPeakDistance=%.3f%n", totalPeakDistance / CLOSED_LOOP_CASES));
        report.append(String.format(Locale.ROOT, "avgFinalDistance=%.3f%n", totalFinalDistance / CLOSED_LOOP_CASES));
        appendReport(REPORT_FILE, report);
        assertTrue(failures.length() == 0, report + "\nfailures:\n" + failures);
    }

    private static GroundClearance randomClearance(Random random) {
        return new GroundClearance(
                randomRange(random, 0.2D, 8.0D),
                randomRange(random, 0.2D, 8.0D),
                randomRange(random, 0.2D, 8.0D),
                randomRange(random, 0.2D, 8.0D));
    }

    private static ClosedLoopResult simulateSteerToward(SimController controller, Vec3 target,
                                                        boolean preferStableFire, int ticks) {
        int turnDirection = 0;
        int forwardTicks = 0;
        int backTicks = 0;
        int brakeTicks = 0;
        int sprintTicks = 0;
        double minDistance = horizontalDistance(controller.position(), target);
        double maxDistance = minDistance;
        for (int tick = 0; tick < ticks; tick++) {
            GroundSteering.SteeringResult steering = GroundSteering.steerToward(controller, target,
                    target.subtract(controller.position()).horizontalDistanceSqr(), preferStableFire, turnDirection);
            turnDirection = steering.turnDirection();
            VehicleInput input = steering.input();
            if (input.forward()) {
                forwardTicks++;
            }
            if (input.back()) {
                backTicks++;
            }
            if (input.brake()) {
                brakeTicks++;
            }
            if (input.sprint()) {
                sprintTicks++;
            }
            applyGroundInput(controller, input);
            double distance = horizontalDistance(controller.position(), target);
            minDistance = Math.min(minDistance, distance);
            maxDistance = Math.max(maxDistance, distance);
        }
        return new ClosedLoopResult(controller.position(), controller.yaw(), controller.velocity(),
                minDistance, maxDistance, forwardTicks, backTicks, brakeTicks, sprintTicks);
    }

    private static ClosedLoopResult simulateRam(SimController controller, Vec3 target, int ticks) {
        int turnDirection = 0;
        int forwardTicks = 0;
        int backTicks = 0;
        int brakeTicks = 0;
        int sprintTicks = 0;
        double minDistance = horizontalDistance(controller.position(), target);
        double maxDistance = minDistance;
        boolean windup = GroundDriveStrategy.shouldStartRamWindup(
                horizontalDistanceSqr(controller.position(), target),
                Math.abs(yawDiffTo(controller.yaw(), target.subtract(controller.position()))));
        for (int tick = 0; tick < ticks; tick++) {
            double distanceSqr = horizontalDistanceSqr(controller.position(), target);
            double yawDiff = Math.abs(yawDiffTo(controller.yaw(), target.subtract(controller.position())));
            if (windup && !GroundDriveStrategy.shouldContinueRamWindup(distanceSqr, yawDiff)) {
                windup = false;
            }
            GroundSteering.SteeringResult steering = windup
                    ? GroundSteering.ramWindupAway(controller, target, turnDirection)
                    : GroundSteering.steerRamToward(controller, target, turnDirection);
            turnDirection = steering.turnDirection();
            VehicleInput input = steering.input();
            if (input.forward()) {
                forwardTicks++;
            }
            if (input.back()) {
                backTicks++;
            }
            if (input.brake()) {
                brakeTicks++;
            }
            if (input.sprint()) {
                sprintTicks++;
            }
            applyGroundInput(controller, input);
            double distance = horizontalDistance(controller.position(), target);
            minDistance = Math.min(minDistance, distance);
            maxDistance = Math.max(maxDistance, distance);
        }
        return new ClosedLoopResult(controller.position(), controller.yaw(), controller.velocity(),
                minDistance, maxDistance, forwardTicks, backTicks, brakeTicks, sprintTicks);
    }

    private static void applyGroundInput(SimController controller, VehicleInput input) {
        double yawRate = controller.movementType() == VehicleMovementType.TRACK ? 7.5D : 5.0D;
        if (input.left()) {
            controller.yaw = wrapDegrees(controller.yaw - yawRate);
        }
        if (input.right()) {
            controller.yaw = wrapDegrees(controller.yaw + yawRate);
        }

        Vec3 forward = yawForward(controller.yaw);
        double signedSpeed = controller.velocity().x * forward.x + controller.velocity().z * forward.z;
        if (input.brake()) {
            signedSpeed *= 0.45D;
        }
        if (input.forward()) {
            signedSpeed += input.sprint() ? 0.075D : 0.048D;
        }
        if (input.back()) {
            signedSpeed -= 0.042D;
        }
        if (!input.forward() && !input.back() && !input.brake()) {
            signedSpeed *= 0.84D;
        }
        signedSpeed = clamp(signedSpeed * 0.92D, -0.42D, input.sprint() ? 0.82D : 0.64D);
        Vec3 velocity = forward.scale(signedSpeed);
        controller.velocity = velocity;
        controller.position = controller.position.add(velocity);
    }

    private static Vec3 yawForward(float yaw) {
        double yawRadians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        return Math.sqrt(horizontalDistanceSqr(first, second));
    }

    private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return dx * dx + dz * dz;
    }

    private static double yawFacing(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return Math.atan2(dz, dx) * (180.0D / Math.PI) - 90.0D;
    }

    private static float wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0D;
        if (wrapped >= 180.0D) {
            wrapped -= 360.0D;
        }
        if (wrapped < -180.0D) {
            wrapped += 360.0D;
        }
        return (float) wrapped;
    }

    private static Vec3 targetAt(double distance, double yawDiffDegrees) {
        double targetYaw = 0.0D + yawDiffDegrees;
        double radians = Math.toRadians(targetYaw + 90.0D);
        return new Vec3(Math.cos(radians) * distance, 0.0D, Math.sin(radians) * distance);
    }

    private static double yawDiffTo(float yaw, Vec3 point) {
        float targetYaw = (float) (Math.atan2(point.z, point.x) * (180F / Math.PI)) - 90.0F;
        double diff = targetYaw - yaw;
        while (diff >= 180.0D) {
            diff -= 360.0D;
        }
        while (diff < -180.0D) {
            diff += 360.0D;
        }
        return diff;
    }

    private static void appendManeuverFailure(StringBuilder failures, String bucket, int index, GroundClearance clearance,
                                              GroundManeuverStep initial, GroundManeuverStep preferredStraight,
                                              GroundManeuverStep preferredTurn) {
        if (failures.length() > 6000) {
            return;
        }
        failures.append(String.format(Locale.ROOT,
                "%s case=%d clearance=%s initial=%s preferredStraight=%s preferredTurn=%s%n",
                bucket, index, clearance, initial, preferredStraight, preferredTurn));
    }

    private static void appendTransitionFailure(StringBuilder failures, String bucket, int index, GroundClearance clearance,
                                                int stepTicks, int noProgressTicks, GroundManeuverStep next) {
        if (failures.length() > 6000) {
            return;
        }
        failures.append(String.format(Locale.ROOT,
                "%s case=%d clearance=%s stepTicks=%d noProgressTicks=%d next=%s%n",
                bucket, index, clearance, stepTicks, noProgressTicks, next));
    }

    private static void appendTurnFailure(StringBuilder failures, String bucket, int index,
                                          GroundClearance clearance, int direction) {
        if (failures.length() > 6000) {
            return;
        }
        failures.append(String.format(Locale.ROOT,
                "%s case=%d clearance=%s direction=%d%n",
                bucket, index, clearance, direction));
    }

    private static void appendInputFailure(StringBuilder failures, String bucket, int index,
                                           int direction, VehicleInput input) {
        if (failures.length() > 6000) {
            return;
        }
        failures.append(String.format(Locale.ROOT,
                "%s case=%d direction=%d input=%s%n",
                bucket, index, direction, input));
    }

    private static void appendRamFailure(StringBuilder failures, String bucket, int index,
                                         double distanceSqr, double yawDiff, VehicleInput input) {
        if (failures.length() > 6000) {
            return;
        }
        failures.append(String.format(Locale.ROOT,
                "%s case=%d distanceSqr=%.3f yawDiff=%.3f input=%s%n",
                bucket, index, distanceSqr, yawDiff, input));
    }

    private static void appendClosedLoopFailure(StringBuilder failures, String bucket, int index,
                                                VehicleMovementType type, double initialDistance, double finalDistance,
                                                double initialYawError, double finalYawError,
                                                ClosedLoopResult result) {
        if (failures.length() > 6000) {
            return;
        }
        failures.append(String.format(Locale.ROOT,
                "%s case=%d type=%s initialDistance=%.3f finalDistance=%.3f initialYawError=%.3f finalYawError=%.3f minDistance=%.3f maxDistance=%.3f finalPos=(%.3f,%.3f,%.3f) finalYaw=%.3f velocity=(%.3f,%.3f,%.3f) forwardTicks=%d backTicks=%d brakeTicks=%d sprintTicks=%d%n",
                bucket,
                index,
                type,
                initialDistance,
                finalDistance,
                initialYawError,
                finalYawError,
                result.minDistance(),
                result.maxDistance(),
                result.position().x,
                result.position().y,
                result.position().z,
                result.yaw(),
                result.velocity().x,
                result.velocity().y,
                result.velocity().z,
                result.forwardTicks(),
                result.backTicks(),
                result.brakeTicks(),
                result.sprintTicks()));
    }

    private static void appendReport(String fileName, StringBuilder report) throws IOException {
        Path path = Path.of("build", "test-metrics", fileName);
        Files.createDirectories(path.getParent());
        boolean exists = Files.exists(path);
        Files.writeString(path, exists ? "\n" + report : report.toString(), StandardCharsets.UTF_8, exists
                ? java.nio.file.StandardOpenOption.APPEND
                : java.nio.file.StandardOpenOption.CREATE);
    }

    private static double randomRange(Random random, double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private static int randomRangeInt(Random random, int minInclusive, int maxExclusive) {
        return minInclusive + random.nextInt(maxExclusive - minInclusive);
    }

    private static double randomSign(Random random) {
        return random.nextBoolean() ? 1.0D : -1.0D;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ClosedLoopResult(Vec3 position, float yaw, Vec3 velocity,
                                    double minDistance, double maxDistance,
                                    int forwardTicks, int backTicks, int brakeTicks, int sprintTicks) {
    }

    private static final class SimController implements VehicleController {
        private Vec3 position;
        private float yaw;
        private Vec3 velocity;
        private final VehicleMovementType movementType;

        private SimController(Vec3 position, float yaw, Vec3 velocity, VehicleMovementType movementType) {
            this.position = position;
            this.yaw = yaw;
            this.velocity = velocity;
            this.movementType = movementType;
        }

        @Override
        public Entity entity() {
            return null;
        }

        @Override
        public Level level() {
            return null;
        }

        @Override
        public AABB boundingBox() {
            return new AABB(this.position.x - 0.5D, this.position.y, this.position.z - 0.5D,
                    this.position.x + 0.5D, this.position.y + 1.0D, this.position.z + 0.5D);
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public boolean isWreck() {
            return false;
        }

        @Override
        public boolean isControllable() {
            return true;
        }

        @Override
        public Vec3 position() {
            return this.position;
        }

        @Override
        public float yaw() {
            return this.yaw;
        }

        @Override
        public Vec3 velocity() {
            return this.velocity;
        }

        @Override
        public double estimatedHorizontalStoppingDistance() {
            return 0.0D;
        }

        @Override
        public VehicleMovementType movementType() {
            return this.movementType;
        }

        @Override
        public VehicleDimensions pathingDimensions() {
            return VehicleDimensions.DEFAULT;
        }

        @Override
        public List<SeatHandle> seats() {
            return List.of();
        }

        @Override
        public Optional<SeatHandle> seatOf(Mob mob) {
            return Optional.empty();
        }

        @Override
        public Optional<Entity> controllingPassenger() {
            return Optional.empty();
        }

        @Override
        public List<Entity> passengers() {
            return List.of();
        }

        @Override
        public boolean canMount(Mob mob) {
            return false;
        }

        @Override
        public boolean mount(Mob mob) {
            return false;
        }

        @Override
        public boolean moveToSeat(Mob mob, SeatHandle seat) {
            return false;
        }

        @Override
        public boolean dismount(Mob mob) {
            return false;
        }

        @Override
        public boolean canDrive(Mob mob) {
            return true;
        }

        @Override
        public void drive(Mob mob, VehicleInput input) {
        }

        @Override
        public void stopDriving(Mob mob) {
        }

        @Override
        public List<WeaponHandle> weapons(Mob mob) {
            return List.of();
        }

        @Override
        public WeaponStatus weaponStatus(Mob mob, WeaponHandle weapon) {
            return new WeaponStatus(false, false, false, false, false, false, 0);
        }

        @Override
        public boolean selectWeapon(Mob mob, WeaponHandle weapon) {
            return false;
        }

        @Override
        public boolean aimAt(Mob mob, Entity target, Vec3 aimPoint) {
            return false;
        }

        @Override
        public boolean fire(Mob mob, Entity target) {
            return false;
        }

        @Override
        public boolean reload(Mob mob) {
            return false;
        }
    }

    private record TestController(Vec3 position, float yaw, Vec3 velocity,
                                  VehicleMovementType movementType) implements VehicleController {
        @Override
        public Entity entity() {
            return null;
        }

        @Override
        public Level level() {
            return null;
        }

        @Override
        public AABB boundingBox() {
            return new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public boolean isWreck() {
            return false;
        }

        @Override
        public boolean isControllable() {
            return true;
        }

        @Override
        public double estimatedHorizontalStoppingDistance() {
            return 0.0D;
        }

        @Override
        public VehicleDimensions pathingDimensions() {
            return VehicleDimensions.DEFAULT;
        }

        @Override
        public List<SeatHandle> seats() {
            return List.of();
        }

        @Override
        public Optional<SeatHandle> seatOf(Mob mob) {
            return Optional.empty();
        }

        @Override
        public Optional<Entity> controllingPassenger() {
            return Optional.empty();
        }

        @Override
        public List<Entity> passengers() {
            return List.of();
        }

        @Override
        public boolean canMount(Mob mob) {
            return false;
        }

        @Override
        public boolean mount(Mob mob) {
            return false;
        }

        @Override
        public boolean moveToSeat(Mob mob, SeatHandle seat) {
            return false;
        }

        @Override
        public boolean dismount(Mob mob) {
            return false;
        }

        @Override
        public boolean canDrive(Mob mob) {
            return true;
        }

        @Override
        public void drive(Mob mob, VehicleInput input) {
        }

        @Override
        public void stopDriving(Mob mob) {
        }

        @Override
        public List<WeaponHandle> weapons(Mob mob) {
            return List.of();
        }

        @Override
        public WeaponStatus weaponStatus(Mob mob, WeaponHandle weapon) {
            return new WeaponStatus(false, false, false, false, false, false, 0);
        }

        @Override
        public boolean selectWeapon(Mob mob, WeaponHandle weapon) {
            return false;
        }

        @Override
        public boolean aimAt(Mob mob, Entity target, Vec3 aimPoint) {
            return false;
        }

        @Override
        public boolean fire(Mob mob, Entity target) {
            return false;
        }

        @Override
        public boolean reload(Mob mob) {
            return false;
        }
    }
}
