package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.helicopter;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleFlightInput;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleInput;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class HelicopterFlightControl {
    static final double ALTITUDE_SAMPLE = 64.0D;
    static final double SAFE_ALTITUDE = 12.0D;
    static final double STABILIZED_HORIZONTAL_SPEED = 0.22D;

    private static final int HOVER_TOGGLE_COOLDOWN = 20;
    private static final double MAX_LEVEL_PITCH = 5.0D;
    private static final double VERTICAL_TOLERANCE = 1.5D;
    private static final double RECOVERY_DESCENT_SPEED = -0.35D;
    static final double MAX_MOUSE_X = 48.0D;
    static final double MAX_MOUSE_Y = 2.25D;
    private static final double MAX_APPROACH_MOUSE_Y = 6.0D;
    static final double MAX_ATTACK_MOUSE_Y = 26.0D;
    private static final double MAX_PULL_OUT_MOUSE_Y = 8.0D;
    private static final double APPROACH_MIN_HORIZONTAL_ERROR = 3.0D;
    private static final double FULL_CRUISE_TURN_DEGREES = 12.0D;
    private static final double ZERO_CRUISE_TURN_DEGREES = 110.0D;
    private static final double LATERAL_HOVER_ENTER_SPEED = 0.55D;
    private static final double LATERAL_HOVER_EXIT_SPEED = 0.22D;
    private static final double LATERAL_STABILIZE_MAX_MOUSE_X = 18.0D;
    private static final double ROLL_COUNTER_MIN_MOUSE_X = 4.0D;
    private static final double APPROACH_BASE_PITCH = 10.0D;
    private static final double APPROACH_ALIGNMENT_PITCH = 12.0D;
    private static final double PASS_THROUGH_BASE_PITCH = 14.0D;
    private static final double PASS_THROUGH_ALIGNMENT_PITCH = 18.0D;
    private static final double APPROACH_CRUISE_SPEED = 0.68D;
    private static final double APPROACH_SPEED_CONTROL_BAND = 0.28D;
    private static final double ARRIVAL_BRAKE_TICKS = 12.0D;
    private static final double OPPOSING_COURSE_BRAKE_SPEED = 0.18D;
    private static final double APPROACH_ALTITUDE_TRIM_ERROR = 4.0D;
    private static final double APPROACH_PROJECTED_TRIM_ERROR = 1.0D;
    private static final double APPROACH_PROJECTED_TRIM_TICKS = 8.0D;
    private static final double APPROACH_VERTICAL_TRIM_SPEED = 0.08D;
    private static final float APPROACH_ALTITUDE_TRIM_POWER = 0.075F;
    private static final float APPROACH_MIN_MOVEMENT_POWER = 0.075F;
    private static final float PASS_THROUGH_MIN_POWER = 0.12F;
    private static final double TAKEOFF_MAX_MOUSE_X = 22.0D;
    static final double DIVE_PASS_PITCH_BONUS = 38.0D;
    private static final double DIVE_PASS_MIN_DIVE_PITCH = 62.0D;
    private static final double DIVE_PASS_MAX_UP_PITCH = -18.0D;
    private static final double DIVE_PASS_MAX_DIVE_PITCH = 78.0D;
    private static final double DIVE_PASS_PULL_OUT_PITCH = -8.0D;

    private int hoverToggleCooldown;
    private boolean settlingAfterDive;

    void reset() {
        this.hoverToggleCooldown = 0;
        this.settlingAfterDive = false;
    }

    static VehicleFlightInput neutralInput() {
        return VehicleFlightInput.NONE;
    }

    VehicleFlightInput command(HelicopterFlightPlanner.AircraftState state,
                               HelicopterFlightPlanner.FlightRequest request,
                               HelicopterEnvironment environment) {
        return this.command(state, request, environment, false);
    }

    VehicleFlightInput command(HelicopterFlightPlanner.AircraftState state,
                               HelicopterFlightPlanner.FlightRequest request,
                               HelicopterEnvironment environment, boolean decoy) {
        if (this.hoverToggleCooldown > 0) {
            this.hoverToggleCooldown--;
        }
        Vec3 position = state.position();
        double yError = request.desiredPosition().y - position.y;
        double horizontalError = horizontalDistance(position, request.desiredPosition());
        double horizontalSpeed = horizontalSpeed(state);
        double forwardSpeed = forwardSpeed(state);
        this.settlingAfterDive = request.divePitch()
                || this.settlingAfterDive && (Math.abs(state.pitch()) > MAX_LEVEL_PITCH
                || horizontalSpeed > STABILIZED_HORIZONTAL_SPEED);
        boolean pullOut = !request.divePitch() && (request.pullOutPitch() || this.settlingAfterDive);
        boolean recovery = state.altitude() < SAFE_ALTITUDE
                || state.velocity().y < RECOVERY_DESCENT_SPEED && yError > -VERTICAL_TOLERANCE;
        boolean engineReady = state.engineStarted();
        double yawDiff = yawDiffTo(state, request.aimPoint());
        boolean arrivalBrake = engineReady && shouldBrakeForArrival(state, request, horizontalError);
        boolean obstacleBrake = engineReady && shouldBrakeForObstacle(request, environment, horizontalSpeed);
        boolean courseBrake = engineReady && shouldBrakeOpposingCourse(state, request, horizontalError);
        boolean activeBrake = arrivalBrake || obstacleBrake || courseBrake;
        boolean hoverBrake = obstacleBrake || courseBrake
                || arrivalBrake && horizontalError <= APPROACH_MIN_HORIZONTAL_ERROR;
        boolean powerTrimDown = !pullOut && shouldDecreasePower(state, request, yError, recovery);
        boolean lateralStabilizing = !pullOut && shouldStabilizeLaterally(state, request, activeBrake);
        boolean forward = shouldPressForward(state, request, yError, horizontalError, activeBrake,
                recovery, pullOut, powerTrimDown, lateralStabilizing);
        boolean down = powerTrimDown;
        boolean desiredHover = engineReady
                && !request.divePitch()
                && (hoverBrake || lateralStabilizing || recovery || pullOut || request.stableHover());
        boolean urgentHover = desiredHover || request.divePitch();

        double mouseX = mouseX(state, yawDiff, request.takeoffYawLimit(), engineReady);
        if (lateralStabilizing) {
            mouseX = lateralStabilizeMouseX(mouseX);
        }
        boolean counterRoll = engineReady
                && Math.abs(mouseX) >= ROLL_COUNTER_MIN_MOUSE_X;
        boolean back = false;
        boolean left = counterRoll && mouseX < 0.0D;
        boolean right = counterRoll && mouseX > 0.0D;
        double targetPitch = targetPitch(state, request, horizontalError,
                forwardSpeed, activeBrake, lateralStabilizing, pullOut);
        if (recovery) {
            targetPitch = 0.0D;
        }
        double pitchDiff = Mth.wrapDegrees(targetPitch - state.pitch());
        double pitchScale = pullOut ? 1.35D
                : request.takeoff() || !request.hasTarget() ? 12.0D
                : request.divePitch() ? 1.5D
                : request.approach() ? 3.25D
                : 5.5D;
        double mouseLimit = request.divePitch() ? MAX_ATTACK_MOUSE_Y
                : pullOut ? MAX_PULL_OUT_MOUSE_Y
                : request.approach() ? MAX_APPROACH_MOUSE_Y
                : MAX_MOUSE_Y;
        double mouseY = engineReady ? clamp(pitchDiff / pitchScale, -mouseLimit, mouseLimit) : 0.0D;

        VehicleInput input = new VehicleInput(
                forward && (!activeBrake || request.throttleThroughBrake() || recovery || pullOut),
                back,
                left,
                right,
                false,
                request.sprint(),
                false,
                down,
                false,
                decoy);
        boolean tapHover = this.shouldTapHover(state, desiredHover, urgentHover);
        return new VehicleFlightInput(tapHover ? withHoverToggle(input, true) : input, mouseX, mouseY);
    }

    private static boolean shouldPressForward(HelicopterFlightPlanner.AircraftState state,
                                              HelicopterFlightPlanner.FlightRequest request,
                                              double yError,
                                              double horizontalError,
                                              boolean activeBrake,
                                              boolean recovery,
                                              boolean pullOut,
                                              boolean powerTrimDown,
                                              boolean lateralStabilizing) {
        if (!state.engineStarted() || recovery) {
            return true;
        }
        if (request.takeoff()) {
            return needsLiftPower(state, yError);
        }
        if (request.forceForward()) {
            return true;
        }
        if (request.pullOutPower() || pullOut) {
            return needsPullOutPower(state, yError);
        }
        if (request.hoverLift()
                && needsLiftPower(state, yError)
                && state.power() < 0.11F) {
            return true;
        }
        if (!request.approach() || activeBrake) {
            return false;
        }
        boolean needsApproachPower = needsLiftPower(state, yError)
                && state.power() < approachPowerThreshold(request);
        if (needsApproachPower) {
            return true;
        }
        return horizontalError >= APPROACH_MIN_HORIZONTAL_ERROR
                && !lateralStabilizing
                && !powerTrimDown
                && projectedYError(state, yError) >= -APPROACH_PROJECTED_TRIM_ERROR
                && state.power() < movementPowerThreshold(request);
    }

    private static boolean shouldDecreasePower(HelicopterFlightPlanner.AircraftState state,
                                               HelicopterFlightPlanner.FlightRequest request,
                                               double yError,
                                               boolean recovery) {
        if (!state.engineStarted()
                || recovery
                || request.takeoff()
                || request.forceForward()
                || request.pullOutPower()
                || state.power() < APPROACH_ALTITUDE_TRIM_POWER) {
            return false;
        }
        double projectedYError = projectedYError(state, yError);
        return projectedYError < -APPROACH_PROJECTED_TRIM_ERROR
                && (yError < -APPROACH_ALTITUDE_TRIM_ERROR
                || state.velocity().y > APPROACH_VERTICAL_TRIM_SPEED);
    }

    private static boolean shouldStabilizeLaterally(HelicopterFlightPlanner.AircraftState state,
                                                    HelicopterFlightPlanner.FlightRequest request,
                                                    boolean activeBrake) {
        if (!request.approach() || activeBrake) {
            return false;
        }
        double threshold = state.hoverMode() ? LATERAL_HOVER_EXIT_SPEED : LATERAL_HOVER_ENTER_SPEED;
        return horizontalLateralSpeed(state, request.desiredPosition()) > threshold;
    }

    static boolean lacksObstacleStoppingRoom(HelicopterEnvironment environment, double horizontalSpeed) {
        return environment.frontBlocked()
                && environment.frontClearance() <= Math.max(
                APPROACH_MIN_HORIZONTAL_ERROR,
                horizontalSpeed * ARRIVAL_BRAKE_TICKS);
    }

    static double divePassTargetPitch(HelicopterFlightPlanner.AircraftState state, Vec3 aimPoint) {
        Vec3 delta = aimPoint.subtract(state.position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 1.0E-4D) {
            return levelOrBrakePitch(forwardSpeed(state));
        }
        double pitch = -Math.toDegrees(Math.atan2(delta.y, horizontal));
        return clamp(pitch, DIVE_PASS_MAX_UP_PITCH, DIVE_PASS_MAX_DIVE_PITCH);
    }

    private boolean shouldTapHover(HelicopterFlightPlanner.AircraftState state, boolean desiredHover, boolean urgent) {
        if (state.hoverMode() == desiredHover || this.hoverToggleCooldown > 0 && !urgent) {
            return false;
        }
        this.hoverToggleCooldown = HOVER_TOGGLE_COOLDOWN;
        return true;
    }

    private static VehicleInput withHoverToggle(VehicleInput input, boolean tapHover) {
        return new VehicleInput(
                input.forward(),
                input.back(),
                input.left(),
                input.right(),
                input.brake(),
                input.sprint(),
                tapHover,
                input.down(),
                input.fire(),
                input.decoy());
    }

    private static double targetPitch(HelicopterFlightPlanner.AircraftState state,
                                      HelicopterFlightPlanner.FlightRequest request,
                                      double horizontalError,
                                      double forwardSpeed,
                                      boolean activeBrake,
                                      boolean lateralStabilizing,
                                      boolean pullOut) {
        if (request.divePitch()) {
            return divePassPitch(state, request.aimPoint(), request.divePitchBoost());
        }
        if (pullOut) {
            double yError = request.desiredPosition().y - state.position().y;
            return needsPullOutPower(state, yError) ? DIVE_PASS_PULL_OUT_PITCH : levelOrBrakePitch(forwardSpeed);
        }
        if (request.holdPitch()) {
            return state.pitch();
        }
        if (activeBrake || lateralStabilizing) {
            return levelOrBrakePitch(forwardSpeed);
        }
        if (!request.approach()) {
            return levelOrBrakePitch(forwardSpeed);
        }
        if (horizontalError < APPROACH_MIN_HORIZONTAL_ERROR) {
            return levelOrBrakePitch(forwardSpeed);
        }
        boolean passThrough = !request.stopAtDestination() && request.cruiseSpeed() > APPROACH_CRUISE_SPEED;
        double basePitch = passThrough ? PASS_THROUGH_BASE_PITCH : APPROACH_BASE_PITCH;
        double alignmentPitch = passThrough ? PASS_THROUGH_ALIGNMENT_PITCH : APPROACH_ALIGNMENT_PITCH;
        double distanceFactor = clamp(horizontalError / 32.0D, 0.4D, 1.0D);
        double turnScale = turnCruiseScale(movementYawError(state, request));
        double pitch = (basePitch + alignmentPitch * distanceFactor) * turnScale;
        double direction = movementDirectionSign(state, request);
        double cruiseSpeed = request.cruiseSpeed();
        double movementSpeed = horizontalClosingSpeed(state, request.desiredPosition());
        double speedScale = clamp((cruiseSpeed - movementSpeed) / APPROACH_SPEED_CONTROL_BAND,
                -0.3D,
                1.0D);
        double rollScale = clamp(1.0D - Math.abs(state.roll()) / 45.0D, 0.0D, 1.0D);
        return direction * pitch * speedScale * rollScale;
    }

    private static boolean shouldBrakeForArrival(HelicopterFlightPlanner.AircraftState state,
                                                 HelicopterFlightPlanner.FlightRequest request,
                                                 double horizontalError) {
        if (!request.approach() || !request.stopAtDestination()) {
            return false;
        }
        double closingSpeed = horizontalClosingSpeed(state, request.desiredPosition());
        if (closingSpeed <= 0.12D) {
            return false;
        }
        double stoppingDistance = Math.max(APPROACH_MIN_HORIZONTAL_ERROR, closingSpeed * ARRIVAL_BRAKE_TICKS);
        return horizontalError <= stoppingDistance;
    }

    private static boolean shouldBrakeForObstacle(HelicopterFlightPlanner.FlightRequest request,
                                                  HelicopterEnvironment environment,
                                                  double horizontalSpeed) {
        if (!request.approach() || horizontalSpeed <= 0.12D) {
            return false;
        }
        return lacksObstacleStoppingRoom(environment, horizontalSpeed);
    }

    private static double horizontalClosingSpeed(HelicopterFlightPlanner.AircraftState state, Vec3 destination) {
        double dx = destination.x - state.position().x;
        double dz = destination.z - state.position().z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0E-4D) {
            return horizontalSpeed(state);
        }
        return (state.velocity().x * dx + state.velocity().z * dz) / distance;
    }

    private static double horizontalLateralSpeed(HelicopterFlightPlanner.AircraftState state, Vec3 destination) {
        Vec3 velocity = state.velocity();
        double horizontalSpeedSqr = velocity.x * velocity.x + velocity.z * velocity.z;
        if (horizontalSpeedSqr < 1.0E-8D) {
            return 0.0D;
        }
        double closingSpeed = horizontalClosingSpeed(state, destination);
        return Math.sqrt(Math.max(0.0D, horizontalSpeedSqr - closingSpeed * closingSpeed));
    }

    private static double divePassPitch(HelicopterFlightPlanner.AircraftState state, Vec3 aimPoint, double boost) {
        double targetPitch = divePassTargetPitch(state, aimPoint);
        if (targetPitch <= 0.0D || boost <= 0.0D) {
            return targetPitch;
        }
        return clamp(Math.max(targetPitch + boost, DIVE_PASS_MIN_DIVE_PITCH),
                0.0D,
                DIVE_PASS_MAX_DIVE_PITCH);
    }

    private static boolean needsPullOutPower(HelicopterFlightPlanner.AircraftState state, double yError) {
        return yError > VERTICAL_TOLERANCE
                || state.velocity().y < RECOVERY_DESCENT_SPEED
                || state.pitch() > 30.0F;
    }

    private static double mouseX(HelicopterFlightPlanner.AircraftState state, double yawDiff,
                                 boolean takeoff, boolean engineReady) {
        if (!engineReady) {
            return 0.0D;
        }
        double roll = state.roll();
        double absRoll = Math.abs(roll);
        double rollFactor = clamp(1.0D - absRoll / 45.0D, 0.0D, 1.0D);
        double mouseLimit = takeoff ? TAKEOFF_MAX_MOUSE_X : MAX_MOUSE_X;
        double yawCommand = clamp(yawDiff / 1.75D, -mouseLimit, mouseLimit) * rollFactor;
        double rollCorrection = clamp(roll / 8.0D, -3.0D, 3.0D);
        double rollCorrectionWeight = clamp((60.0D - Math.abs(yawDiff)) / 60.0D, 0.0D, 1.0D);
        return clamp(yawCommand + rollCorrection * rollCorrectionWeight, -mouseLimit, mouseLimit);
    }

    private static double lateralStabilizeMouseX(double rawMouseX) {
        return clamp(rawMouseX, -LATERAL_STABILIZE_MAX_MOUSE_X, LATERAL_STABILIZE_MAX_MOUSE_X);
    }

    private static boolean needsLiftPower(HelicopterFlightPlanner.AircraftState state, double yError) {
        return projectedYError(state, yError) > APPROACH_PROJECTED_TRIM_ERROR
                && (yError > VERTICAL_TOLERANCE
                || state.velocity().y < -APPROACH_VERTICAL_TRIM_SPEED);
    }

    private static double projectedYError(HelicopterFlightPlanner.AircraftState state, double yError) {
        return yError - state.velocity().y * APPROACH_PROJECTED_TRIM_TICKS;
    }

    private static boolean shouldBrakeOpposingCourse(HelicopterFlightPlanner.AircraftState state,
                                                     HelicopterFlightPlanner.FlightRequest request,
                                                     double horizontalError) {
        return request.approach()
                && horizontalError >= APPROACH_MIN_HORIZONTAL_ERROR
                && horizontalClosingSpeed(state, request.desiredPosition()) < -OPPOSING_COURSE_BRAKE_SPEED;
    }

    private static double movementDirectionSign(HelicopterFlightPlanner.AircraftState state,
                                                HelicopterFlightPlanner.FlightRequest request) {
        Vec3 movement = request.desiredPosition().subtract(state.position());
        Vec3 facing = request.aimPoint().subtract(state.position());
        double dot = movement.x * facing.x + movement.z * facing.z;
        return dot < 0.0D ? -1.0D : 1.0D;
    }

    private static double yawDiffTo(HelicopterFlightPlanner.AircraftState state, Vec3 point) {
        Vec3 position = state.position();
        double dx = point.x - position.x;
        double dz = point.z - position.z;
        if (dx * dx + dz * dz < 1.0E-6D) {
            return 0.0D;
        }
        float targetYaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90.0F;
        return Mth.wrapDegrees(targetYaw - state.yaw());
    }

    private static double movementYawError(HelicopterFlightPlanner.AircraftState state,
                                           HelicopterFlightPlanner.FlightRequest request) {
        Vec3 movement = request.desiredPosition().subtract(state.position());
        double length = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        if (length < 1.0E-4D) {
            return 0.0D;
        }
        Vec3 driveDirection = yawForward(state).scale(movementDirectionSign(state, request));
        double dot = (driveDirection.x * movement.x + driveDirection.z * movement.z) / length;
        return Math.toDegrees(Math.acos(clamp(dot, -1.0D, 1.0D)));
    }

    private static double turnCruiseScale(double turnDegrees) {
        return 1.0D - clamp(
                (turnDegrees - FULL_CRUISE_TURN_DEGREES)
                        / (ZERO_CRUISE_TURN_DEGREES - FULL_CRUISE_TURN_DEGREES),
                0.0D,
                1.0D);
    }

    private static Vec3 yawForward(HelicopterFlightPlanner.AircraftState state) {
        double yawRadians = Math.toRadians(state.yaw());
        return new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
    }

    private static float approachPowerThreshold(HelicopterFlightPlanner.FlightRequest request) {
        return request.cruiseSpeed() > APPROACH_CRUISE_SPEED
                ? PASS_THROUGH_MIN_POWER
                : 0.11F;
    }

    private static float movementPowerThreshold(HelicopterFlightPlanner.FlightRequest request) {
        return request.cruiseSpeed() > APPROACH_CRUISE_SPEED
                ? PASS_THROUGH_MIN_POWER
                : APPROACH_MIN_MOVEMENT_POWER;
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double horizontalSpeed(HelicopterFlightPlanner.AircraftState state) {
        Vec3 velocity = state.velocity();
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }

    private static double forwardSpeed(HelicopterFlightPlanner.AircraftState state) {
        Vec3 velocity = state.velocity();
        Vec3 forward = yawForward(state);
        return velocity.x * forward.x + velocity.z * forward.z;
    }

    private static double levelOrBrakePitch(double forwardSpeed) {
        if (Math.abs(forwardSpeed) < 0.12D) {
            return 0.0D;
        }
        return clamp(-forwardSpeed * 8.0D, -MAX_LEVEL_PITCH, MAX_LEVEL_PITCH);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
