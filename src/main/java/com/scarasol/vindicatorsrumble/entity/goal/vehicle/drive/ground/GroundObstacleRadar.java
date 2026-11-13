package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

final class GroundObstacleRadar {
    static final double RANGE = 8.0D;
    private static final int TURN_SAMPLES = 12;

    private GroundObstacleRadar() {
    }

    static GroundClearance scan(VehicleController controller) {
        Level level = controller.level();
        Entity entity = controller.entity();
        if (level == null || entity == null) {
            return GroundClearance.open(RANGE);
        }

        AABB box = controller.localCollisionBounds();
        Vec3 center = box.getCenter();
        Vec3 forward = forward(controller.yaw());
        Vec3 left = new Vec3(forward.z, 0.0D, -forward.x);
        Vec3 origin = controller.position().add(left.scale(center.x)).add(forward.scale(center.z));
        double halfWidth = box.getXsize() * 0.5D;
        double halfLength = box.getZsize() * 0.5D;
        double minY = controller.position().y + box.minY + Math.min(0.08D, box.getYsize() * 0.1D);
        double maxY = controller.position().y + box.maxY - 0.01D;
        return new GroundClearance(
                sweepFace(level, entity, origin, forward, left, halfLength, halfWidth, minY, maxY),
                sweepFace(level, entity, origin, forward.scale(-1.0D), left, halfLength, halfWidth, minY, maxY),
                sweepFace(level, entity, origin, left, forward, halfWidth, halfLength, minY, maxY),
                sweepFace(level, entity, origin, left.scale(-1.0D), forward, halfWidth, halfLength, minY, maxY));
    }

    static boolean hasForwardTurnSpace(VehicleController controller, int turn, double yawDifference) {
        if (turn == 0) {
            return true;
        }
        AABB local = controller.localCollisionBounds();
        double turningRadius = controller.estimatedGroundTurningRadius();
        double radius = Math.max(local.getZsize() * 0.5D, turningRadius);
        // Full-lock radius does not include the straight lead while steering builds.
        double lead = Math.min(local.getZsize() * 0.5D, turningRadius * 0.25D);
        double angle = Math.min(Math.toRadians(Math.max(35.0D, Math.min(90.0D, Math.abs(yawDifference)))),
                Math.max(1.0D, RANGE - lead) / radius);
        Vec3 initialForward = forward(controller.yaw());
        Vec3 initialLeft = new Vec3(initialForward.z, 0.0D, -initialForward.x);
        Vec3 offset = local.getCenter();
        double footprint = Math.hypot(Math.max(Math.abs(local.minX), Math.abs(local.maxX)),
                Math.max(Math.abs(local.minZ), Math.abs(local.maxZ)));
        double padding = (radius + footprint) * angle / (2.0D * TURN_SAMPLES);
        double minY = controller.position().y + local.minY + Math.min(0.08D, local.getYsize() * 0.1D);
        double maxY = controller.position().y + local.maxY - 0.01D;
        // Midpoint envelopes include the intervening translation and corner rotation.
        for (int step = 0; step < TURN_SAMPLES; step++) {
            double theta = angle * (step + 0.5D) / TURN_SAMPLES;
            Vec3 heading = forward(controller.yaw() + (float) Math.toDegrees(theta * turn));
            Vec3 side = new Vec3(heading.z, 0.0D, -heading.x);
            // Cover the whole steering build-up interval, including an immediate turn.
            double halfWidth = local.getXsize() * 0.5D + padding + Math.abs(initialForward.dot(side)) * lead * 0.5D;
            double halfLength = local.getZsize() * 0.5D + padding + Math.abs(initialForward.dot(heading)) * lead * 0.5D;
            Vec3 center = controller.position()
                    .add(initialForward.scale(lead * 0.5D + radius * Math.sin(theta)))
                    .add(initialLeft.scale(-turn * radius * (1.0D - Math.cos(theta))))
                    .add(side.scale(offset.x)).add(heading.scale(offset.z));
            double extentX = Math.abs(side.x) * halfWidth + Math.abs(heading.x) * halfLength;
            double extentZ = Math.abs(side.z) * halfWidth + Math.abs(heading.z) * halfLength;
            AABB search = new AABB(center.x - extentX, minY, center.z - extentZ,
                    center.x + extentX, maxY, center.z + extentZ);
            GroundCollisionSweep sweep = new GroundCollisionSweep(heading, side, halfWidth, halfLength);
            for (VoxelShape shape : controller.level().getBlockCollisions(controller.entity(), search)) {
                for (AABB obstacle : shape.toAabbs()) {
                    if (obstacle.maxY <= minY || obstacle.minY >= maxY) {
                        continue;
                    }
                    if (sweep.contactDistance(center, obstacle, 0.0D) == 0.0D) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static double sweepFace(Level level, Entity entity, Vec3 origin, Vec3 direction, Vec3 side,
                                    double faceOffset, double halfSpan, double minY, double maxY) {
        double halfDepth = 0.025D;
        Vec3 face = origin.add(direction.scale(Math.max(0.0D, faceOffset - halfDepth)));
        double span = Math.max(0.0D, halfSpan - 0.01D);
        double extentX = Math.abs(side.x) * span + Math.abs(direction.x) * halfDepth;
        double extentZ = Math.abs(side.z) * span + Math.abs(direction.z) * halfDepth;
        AABB search = new AABB(face.x - extentX, minY, face.z - extentZ,
                face.x + extentX, maxY, face.z + extentZ).expandTowards(direction.scale(RANGE));
        GroundCollisionSweep sweep = new GroundCollisionSweep(direction, side, span, halfDepth);
        double distance = RANGE;
        // Sweep the entire leading face, including outer corners and all body heights.
        // Starting at each face keeps nose contact from also rejecting an open rear.
        for (VoxelShape shape : level.getBlockCollisions(entity, search)) {
            for (AABB obstacle : shape.toAabbs()) {
                if (obstacle.maxY <= minY || obstacle.minY >= maxY) {
                    continue;
                }
                distance = Math.min(distance, sweep.contactDistance(face, obstacle, distance));
                if (distance <= 0.0D) {
                    return 0.0D;
                }
            }
        }
        return distance;
    }

    private static Vec3 forward(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
    }
}
