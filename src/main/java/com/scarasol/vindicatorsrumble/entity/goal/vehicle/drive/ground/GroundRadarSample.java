package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground;

import net.minecraft.world.phys.Vec3;

record GroundRadarSample(
        GroundClearance clearance,
        Vec3 origin,
        Vec3 forward,
        Vec3 left,
        double sideOffset,
        double frontCenter,
        double frontLeft,
        double frontRight,
        double backCenter,
        double backLeft,
        double backRight
) {
}
