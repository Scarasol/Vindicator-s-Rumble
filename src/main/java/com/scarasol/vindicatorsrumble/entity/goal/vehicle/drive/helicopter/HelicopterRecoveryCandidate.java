package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.helicopter;

import net.minecraft.world.phys.Vec3;

import java.util.List;

record HelicopterRecoveryCandidate(Vec3 position, Vec3 heading, List<Vec3> path,
                                   int lane, boolean climbed, double travelCost,
                                   double score, double corridorDeficit,
                                   double blockedLength) {
}
