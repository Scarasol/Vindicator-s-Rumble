package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class GroundCollisionSweep {
    private final double[] projections = new double[16];

    GroundCollisionSweep(Vec3 direction, Vec3 side, double halfWidth, double halfLength) {
        this.project(0, direction.x, direction.z, direction, side, halfWidth, halfLength);
        this.project(4, side.x, side.z, direction, side, halfWidth, halfLength);
        this.project(8, 1.0D, 0.0D, direction, side, halfWidth, halfLength);
        this.project(12, 0.0D, 1.0D, direction, side, halfWidth, halfLength);
    }

    private void project(int index, double x, double z, Vec3 direction, Vec3 side,
                         double halfWidth, double halfLength) {
        this.projections[index] = x;
        this.projections[index + 1] = z;
        this.projections[index + 2] = halfWidth * Math.abs(side.x * x + side.z * z)
                + halfLength * Math.abs(direction.x * x + direction.z * z);
        this.projections[index + 3] = direction.x * x + direction.z * z;
    }

    double contactDistance(Vec3 origin, AABB obstacle, double limit) {
        double dx = (obstacle.minX + obstacle.maxX) * 0.5D - origin.x;
        double dz = (obstacle.minZ + obstacle.maxZ) * 0.5D - origin.z;
        double halfX = obstacle.getXsize() * 0.5D;
        double halfZ = obstacle.getZsize() * 0.5D;
        double enter = 0.0D;
        double exit = limit;
        // Body projections and movement speed are invariant across obstacles in this sweep.
        for (int index = 0; index < this.projections.length; index += 4) {
            double x = this.projections[index];
            double z = this.projections[index + 1];
            double offset = dx * x + dz * z;
            double radius = this.projections[index + 2] + halfX * Math.abs(x) + halfZ * Math.abs(z);
            double speed = this.projections[index + 3];
            if (Math.abs(speed) < 1.0E-7D) {
                if (Math.abs(offset) > radius) return Double.POSITIVE_INFINITY;
            } else {
                double a = (offset - radius) / speed;
                double b = (offset + radius) / speed;
                enter = Math.max(enter, Math.min(a, b));
                exit = Math.min(exit, Math.max(a, b));
                if (enter > exit) return Double.POSITIVE_INFINITY;
            }
        }
        return enter;
    }
}
