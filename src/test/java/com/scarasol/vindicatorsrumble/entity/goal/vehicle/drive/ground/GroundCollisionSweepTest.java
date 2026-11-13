package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroundCollisionSweepTest {
    @Test
    void reservesTheWholeBodyAheadOfItsOrigin() {
        GroundCollisionSweep sweep = new GroundCollisionSweep(new Vec3(0, 0, 1), new Vec3(1, 0, 0), 1, 2);
        assertEquals(3.0D, sweep.contactDistance(Vec3.ZERO, new AABB(-0.5D, 0, 5, 0.5D, 1, 6), 10), 1.0E-10D);
    }

    @Test
    void rejectsAnObstacleOutsideTheSweptWidth() {
        GroundCollisionSweep sweep = new GroundCollisionSweep(new Vec3(0, 0, 1), new Vec3(1, 0, 0), 1, 2);
        assertEquals(Double.POSITIVE_INFINITY,
                sweep.contactDistance(Vec3.ZERO, new AABB(1.1D, 0, 3, 2, 1, 4), 10));
    }

    @Test
    void diagonalBroadphaseCornerDoesNotBlockTheRotatedBody() {
        double diagonal = Math.sqrt(0.5D);
        GroundCollisionSweep sweep = new GroundCollisionSweep(
                new Vec3(diagonal, 0, diagonal), new Vec3(diagonal, 0, -diagonal), 1, 3);
        assertEquals(Double.POSITIVE_INFINITY,
                sweep.contactDistance(Vec3.ZERO, new AABB(2.4D, 0, -2.6D, 2.6D, 1, -2.4D), 0));
    }

    @Test
    void includesContactAtTheSweepEndpoint() {
        GroundCollisionSweep sweep = new GroundCollisionSweep(new Vec3(0, 0, 1), new Vec3(1, 0, 0), 1, 2);
        assertEquals(3.0D, sweep.contactDistance(Vec3.ZERO, new AABB(-0.5D, 0, 5, 0.5D, 1, 6), 3), 1.0E-10D);
        assertEquals(Double.POSITIVE_INFINITY,
                sweep.contactDistance(Vec3.ZERO, new AABB(-0.5D, 0, 5, 0.5D, 1, 6), 2.9D));
    }

    @Test
    void aBlockBehindTheRearFaceDoesNotBlockForwardTravel() {
        GroundCollisionSweep sweep = new GroundCollisionSweep(new Vec3(0, 0, 1), new Vec3(1, 0, 0), 1, 2);
        assertEquals(Double.POSITIVE_INFINITY,
                sweep.contactDistance(Vec3.ZERO, new AABB(-0.5D, 0, -4, 0.5D, 1, -3), 10));
    }
}
