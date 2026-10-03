package com.scarasol.vindicatorsrumble.gametest.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

record HelicopterScenario(int index, HelicopterObstacleKind kind, Vec3 start, Vec3 target,
                          BlockPos obstacleCenter, int forwardX, int forwardZ,
                          int width, int depth, int height, int groundY, float initialYaw) {
    private static final Direction[] DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    static HelicopterScenario generate(int index, Random random, BlockPos origin) {
        Direction direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        HelicopterObstacleKind kind = HelicopterObstacleKind.values()[index % HelicopterObstacleKind.values().length];
        int forwardX = direction.getStepX();
        int forwardZ = direction.getStepZ();
        int targetDistance = 52 + random.nextInt(7);
        int width;
        int depth;
        int baseHeight;
        switch (kind) {
            case WIDE_WALL -> {
                width = 36 + random.nextInt(25);
                depth = 4 + random.nextInt(5);
                baseHeight = 12 + random.nextInt(9);
            }
            case TALL_TOWER -> {
                width = 12 + random.nextInt(9);
                depth = 12 + random.nextInt(9);
                baseHeight = 28 + random.nextInt(17);
            }
            case WIDE_BUILDING -> {
                width = 28 + random.nextInt(25);
                depth = 14 + random.nextInt(13);
                baseHeight = 20 + random.nextInt(19);
            }
            case DEEP_BUILDING -> {
                width = 14 + random.nextInt(11);
                depth = 24 + random.nextInt(9);
                baseHeight = 18 + random.nextInt(15);
            }
            default -> throw new IllegalStateException("Unexpected obstacle kind " + kind);
        }
        int startHeight = 16 + random.nextInt(9);
        int targetHeight = switch ((index / HelicopterObstacleKind.values().length) % 3) {
            case 0 -> 1;
            case 1 -> 9;
            default -> 17;
        };
        int targetLateral = random.nextInt(17) - 8;
        int halfDepth = depth / 2;
        int minObstacleDistance = halfDepth + 10;
        int maxObstacleDistance = targetDistance - halfDepth - 8;
        int obstacleDistance = minObstacleDistance
                + random.nextInt(maxObstacleDistance - minObstacleDistance + 1);
        int lineHeight = (int) Math.ceil(startHeight
                + (targetHeight - startHeight) * obstacleDistance / (double) targetDistance);
        int height = Math.max(baseHeight, lineHeight + 5);
        Vec3 start = new Vec3(origin.getX() + 0.5D, origin.getY() + startHeight, origin.getZ() + 0.5D);
        Vec3 target = new Vec3(
                origin.getX() + 0.5D + forwardX * targetDistance - forwardZ * targetLateral,
                origin.getY() + targetHeight,
                origin.getZ() + 0.5D + forwardZ * targetDistance + forwardX * targetLateral);
        BlockPos obstacle = origin.offset(forwardX * obstacleDistance, 0, forwardZ * obstacleDistance);
        float targetYaw = (float) (Mth.atan2(target.z - start.z, target.x - start.x)
                * (180.0D / Math.PI)) - 90.0F;
        float yaw = Mth.wrapDegrees(targetYaw + random.nextFloat() * 240.0F - 120.0F);
        return new HelicopterScenario(index, kind, start, target, obstacle,
                forwardX, forwardZ, width, depth, height, origin.getY(), yaw);
    }

    double initialTargetDistance() {
        return horizontalDistance(this.start, this.target);
    }

    double allowedTargetDistance() {
        return this.initialTargetDistance() + 20.0D;
    }

    double allowedAltitude() {
        double startHeight = this.start.y - this.groundY;
        double targetHeight = this.target.y - this.groundY;
        return Math.max(Math.max(startHeight, targetHeight), this.height) + 18.0D;
    }

    double forwardCoordinate(Vec3 point) {
        return (point.x - this.start.x) * this.forwardX
                + (point.z - this.start.z) * this.forwardZ;
    }

    double lateralCoordinate(Vec3 point) {
        return (point.x - this.start.x) * -this.forwardZ
                + (point.z - this.start.z) * this.forwardX;
    }

    double obstacleForwardMin() {
        return this.forwardCoordinate(Vec3.atCenterOf(this.obstacleCenter)) - this.depth / 2.0D;
    }

    double obstacleForwardMax() {
        return this.forwardCoordinate(Vec3.atCenterOf(this.obstacleCenter)) + this.depth / 2.0D;
    }

    double obstacleLateralMin() {
        return this.lateralCoordinate(Vec3.atCenterOf(this.obstacleCenter)) - this.width / 2.0D;
    }

    double obstacleLateralMax() {
        return this.lateralCoordinate(Vec3.atCenterOf(this.obstacleCenter)) + this.width / 2.0D;
    }

    double obstacleTopY() {
        return this.groundY + this.height;
    }

    int targetHeight() {
        return (int) Math.round(this.target.y - this.groundY);
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        return Math.hypot(first.x - second.x, first.z - second.z);
    }
}
