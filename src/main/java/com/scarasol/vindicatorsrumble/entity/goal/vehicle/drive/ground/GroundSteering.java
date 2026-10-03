package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleInput;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleMovementType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class GroundSteering {
    private static final double RESIDUAL_MOTION_SQR = 1.0E-6D;
    private static final double TURN_BRAKE_SPEED_SQR = 0.0225D;

    static SteeringResult steerToward(VehicleController controller, Vec3 waypoint, double remainingDistanceSqr,
                                     boolean preferStableFire, int previousTurnDirection) {
        Vec3 position = controller.position();
        double dx = waypoint.x - position.x;
        double dz = waypoint.z - position.z;
        double distanceSqr = dx * dx + dz * dz;
        if (distanceSqr < 4.0D) {
            return new SteeringResult(brake(controller), 0);
        }

        float targetYaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90.0F;
        float diff = Mth.wrapDegrees(targetYaw - controller.yaw());
        VehicleMovementType type = controller.movementType();
        float turnThreshold = preferStableFire ? (type == VehicleMovementType.TRACK ? 5.0F : 8.0F) : (type == VehicleMovementType.TRACK ? 8.0F : 14.0F);
        int turnDirection = turnDirection(diff, turnThreshold, previousTurnDirection);
        boolean left = turnDirection < 0;
        boolean right = turnDirection > 0;
        float absDiff = Math.abs(diff);
        double horizontalSpeedSqr = horizontalSpeedSqr(controller);
        boolean reverse = distanceSqr < 25.0D && absDiff > 155.0F && horizontalSpeedSqr < TURN_BRAKE_SPEED_SQR;
        float brakeAngle = preferStableFire ? 65.0F : 95.0F;
        float forwardAngle = preferStableFire ? 80.0F : 135.0F;
        boolean brake = !reverse && absDiff > brakeAngle && horizontalSpeedSqr > TURN_BRAKE_SPEED_SQR;
        boolean forward = !reverse && !brake && (absDiff < forwardAngle || horizontalSpeedSqr < TURN_BRAKE_SPEED_SQR);
        boolean sprint = !preferStableFire && forward && !left && !right && absDiff < 18.0F && remainingDistanceSqr > 256.0D;

        return new SteeringResult(new VehicleInput(forward, reverse, left, right, brake, sprint, false, false, false, false), turnDirection);
    }

    static SteeringResult steerRamToward(VehicleController controller, Vec3 waypoint, int previousTurnDirection) {
        Vec3 position = controller.position();
        double dx = waypoint.x - position.x;
        double dz = waypoint.z - position.z;
        double distanceSqr = dx * dx + dz * dz;
        if (distanceSqr < 1.0E-4D) {
            return new SteeringResult(VehicleInput.NONE, 0);
        }

        float targetYaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90.0F;
        float diff = Mth.wrapDegrees(targetYaw - controller.yaw());
        VehicleMovementType type = controller.movementType();
        float turnThreshold = type == VehicleMovementType.TRACK ? 6.0F : 10.0F;
        int turnDirection = turnDirection(diff, turnThreshold, previousTurnDirection);
        boolean left = turnDirection < 0;
        boolean right = turnDirection > 0;
        float absDiff = Math.abs(diff);
        double horizontalSpeedSqr = horizontalSpeedSqr(controller);
        boolean close = distanceSqr < 36.0D;
        boolean forward = !close || absDiff < 18.0F;
        boolean brake = !forward && horizontalSpeedSqr > TURN_BRAKE_SPEED_SQR;
        boolean sprint = forward && absDiff < 18.0F && distanceSqr > 4.0D;

        return new SteeringResult(new VehicleInput(forward, false, left, right, brake, sprint, false, false, false, false), turnDirection);
    }

    static SteeringResult ramWindupAway(VehicleController controller, Vec3 target, int previousTurnDirection) {
        Vec3 position = controller.position();
        double dx = target.x - position.x;
        double dz = target.z - position.z;
        double distanceSqr = dx * dx + dz * dz;
        if (distanceSqr < 1.0E-4D) {
            return new SteeringResult(VehicleInput.BRAKE, 0);
        }

        float targetYaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90.0F;
        float diff = Mth.wrapDegrees(targetYaw - controller.yaw());
        float absDiff = Math.abs(diff);
        boolean reverseAway = absDiff < 130.0F;
        boolean forwardAway = !reverseAway;

        return new SteeringResult(new VehicleInput(forwardAway, reverseAway, false, false, false, false, false, false, false, false), 0);
    }

    static SteeringResult retreatFrom(VehicleController controller, Vec3 target, int previousTurnDirection) {
        Vec3 position = controller.position();
        double dx = target.x - position.x;
        double dz = target.z - position.z;
        double distanceSqr = dx * dx + dz * dz;
        if (distanceSqr < 1.0E-4D) {
            return new SteeringResult(brake(controller), 0);
        }

        float targetYaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90.0F;
        float towardDiff = Mth.wrapDegrees(targetYaw - controller.yaw());
        float awayDiff = Mth.wrapDegrees(towardDiff + 180.0F);
        VehicleMovementType type = controller.movementType();
        float turnThreshold = type == VehicleMovementType.TRACK ? 7.0F : 12.0F;
        boolean forwardAway = Math.abs(awayDiff) < Math.abs(towardDiff);
        float steeringDiff = forwardAway ? awayDiff : towardDiff;
        int turnDirection = turnDirection(steeringDiff, turnThreshold, previousTurnDirection);
        boolean left = forwardAway ? turnDirection < 0 : turnDirection > 0;
        boolean right = forwardAway ? turnDirection > 0 : turnDirection < 0;
        boolean sprint = forwardAway && Math.abs(awayDiff) < 18.0F && distanceSqr > 16.0D;

        return new SteeringResult(new VehicleInput(forwardAway, !forwardAway, left, right, false, sprint, false, false, false, false), turnDirection);
    }

    static boolean shouldBrake(VehicleController controller) {
        Vec3 velocity = controller.velocity();
        return velocity.lengthSqr() > RESIDUAL_MOTION_SQR;
    }

    static VehicleInput brake(VehicleController controller) {
        return shouldBrake(controller)
                ? VehicleInput.BRAKE
                : VehicleInput.NONE;
    }

    static VehicleInput maneuverBack(int turnDirection) {
        return new VehicleInput(false, true, turnDirection > 0, turnDirection < 0, false, false, false, false, false, false);
    }

    static VehicleInput maneuverForward(int turnDirection, boolean sprint) {
        return new VehicleInput(true, false, turnDirection < 0, turnDirection > 0, false, sprint, false, false, false, false);
    }

    static VehicleInput brakeToward(VehicleController controller, Vec3 target) {
        return brakeToward(controller, target, false);
    }

    static VehicleInput brakeToward(VehicleController controller, Vec3 target, boolean preferStableFire) {
        Vec3 position = controller.position();
        double dx = target.x - position.x;
        double dz = target.z - position.z;
        float targetYaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90.0F;
        float diff = Mth.wrapDegrees(targetYaw - controller.yaw());
        VehicleMovementType type = controller.movementType();
        float turnThreshold = preferStableFire ? (type == VehicleMovementType.TRACK ? 5.0F : 8.0F) : (type == VehicleMovementType.TRACK ? 8.0F : 14.0F);
        boolean left = diff < -turnThreshold;
        boolean right = diff > turnThreshold;
        boolean brake = shouldBrake(controller);
        if (!brake && !left && !right) {
            return VehicleInput.NONE;
        }

        return new VehicleInput(false, false, left, right, brake, false, false, false, false, false);
    }

    private static double horizontalSpeedSqr(VehicleController controller) {
        Vec3 velocity = controller.velocity();
        return velocity.x * velocity.x + velocity.z * velocity.z;
    }

    private static int turnDirection(float diff, float threshold, int previousTurnDirection) {
        float releaseThreshold = threshold * 0.45F;
        float reverseThreshold = threshold * 1.6F;
        if (previousTurnDirection < 0) {
            if (diff > reverseThreshold) {
                return 1;
            }
            return diff > -releaseThreshold ? 0 : -1;
        }
        if (previousTurnDirection > 0) {
            if (diff < -reverseThreshold) {
                return -1;
            }
            return diff < releaseThreshold ? 0 : 1;
        }
        if (diff < -threshold) {
            return -1;
        }
        if (diff > threshold) {
            return 1;
        }
        return 0;
    }

    record SteeringResult(VehicleInput input, int turnDirection) {
    }
}
