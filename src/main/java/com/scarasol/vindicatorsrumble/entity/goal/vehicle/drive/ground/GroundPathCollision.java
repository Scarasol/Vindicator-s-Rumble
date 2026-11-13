package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleDimensions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class GroundPathCollision {
    private GroundPathCollision() {
    }

    public static boolean isClear(CollisionGetter level, Entity entity, Vec3 from, Vec3 to, VehicleDimensions dimensions) {
        Vec3 delta = to.subtract(from).multiply(1, 0, 1);
        double distance = delta.horizontalDistance();
        if (distance < 1.0E-5D) return true;
        Vec3 heading = delta.scale(1.0D / distance);
        Vec3 side = new Vec3(heading.z, 0, -heading.x);
        double halfWidth = dimensions.width() * 0.5D - 0.01D;
        double halfLength = dimensions.length() * 0.5D - 0.01D;
        double extentX = Math.abs(side.x) * halfWidth + Math.abs(heading.x) * halfLength;
        double extentZ = Math.abs(side.z) * halfWidth + Math.abs(heading.z) * halfLength;
        double minY = Math.max(from.y, to.y) + 0.08D;
        double maxY = Math.max(from.y, to.y) + dimensions.height() - 0.01D;
        AABB search = new AABB(from.x - extentX, minY, from.z - extentZ,
                from.x + extentX, maxY, from.z + extentZ).expandTowards(delta);
        GroundCollisionSweep sweep = new GroundCollisionSweep(heading, side, halfWidth, halfLength);
        for (VoxelShape shape : level.getBlockCollisions(entity, search)) {
            for (AABB obstacle : shape.toAabbs()) {
                if (obstacle.maxY > minY && obstacle.minY < maxY
                        && sweep.contactDistance(from, obstacle, distance) <= distance) {
                    return false;
                }
            }
        }
        return true;
    }
}
