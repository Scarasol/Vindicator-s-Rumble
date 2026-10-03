package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class GroundObstacleRadar {
    static final double RANGE = 8.0D;

    private GroundObstacleRadar() {
    }

    static GroundClearance scan(VehicleController controller) {
        return sample(controller).clearance();
    }

    static GroundRadarSample sample(VehicleController controller) {
        Level level = controller.level();
        Entity entity = controller.entity();
        if (level == null || entity == null) {
            GroundClearance clearance = GroundClearance.open(RANGE);
            return new GroundRadarSample(clearance, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0.0D,
                    RANGE, RANGE, RANGE, RANGE, RANGE, RANGE);
        }

        AABB box = controller.boundingBox();
        Vec3 center = box.getCenter();
        double y = box.minY + clamp(box.getYsize() * 0.45D, 0.35D, 1.4D);
        Vec3 origin = new Vec3(center.x, y, center.z);
        Vec3 forward = forward(controller.yaw());
        Vec3 left = new Vec3(forward.z, 0.0D, -forward.x);
        Vec3 right = left.scale(-1.0D);
        Vec3 back = forward.scale(-1.0D);

        double halfWidth = Math.max(box.getXsize(), box.getZsize()) * 0.5D;
        double offset = clamp(halfWidth * 0.75D, 0.35D, 2.5D);

        GroundDirectionSample frontSample = sampleDirection(level, entity, origin, forward, left, offset);
        GroundDirectionSample backSample = sampleDirection(level, entity, origin, back, left, offset);
        double leftDistance = scanDirection(level, entity, origin, left, forward, offset);
        double rightDistance = scanDirection(level, entity, origin, right, forward, offset);
        GroundClearance clearance = new GroundClearance(frontSample.min(), backSample.min(), leftDistance, rightDistance);
        return new GroundRadarSample(clearance, origin, forward, left, offset,
                frontSample.center(), frontSample.left(), frontSample.right(),
                backSample.center(), backSample.left(), backSample.right());
    }

    private static double scanDirection(Level level, Entity entity, Vec3 origin, Vec3 direction, Vec3 side, double offset) {
        return sampleDirection(level, entity, origin, direction, side, offset).min();
    }

    private static GroundDirectionSample sampleDirection(Level level, Entity entity, Vec3 origin, Vec3 direction, Vec3 side, double offset) {
        double center = rayDistance(level, entity, origin, direction);
        double left = rayDistance(level, entity, origin.add(side.scale(offset)), direction);
        double right = rayDistance(level, entity, origin.add(side.scale(-offset)), direction);
        return new GroundDirectionSample(center, left, right);
    }

    private static double rayDistance(Level level, Entity entity, Vec3 start, Vec3 direction) {
        Vec3 end = start.add(direction.scale(RANGE));
        HitResult hit = level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                entity));
        return hit.getType() == HitResult.Type.MISS
                ? RANGE
                : Math.min(RANGE, start.distanceTo(hit.getLocation()));
    }

    private static Vec3 forward(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

}
