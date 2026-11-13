package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.helicopter;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

record HelicopterRecoveryCandidate(Vec3 position, Vec3 heading,
                                   @Nullable HelicopterRecoveryCandidate parent,
                                   int lane, boolean climbed, double travelCost,
                                   double score) {
    List<Vec3> path(int limit) {
        int depth = 0;
        for (HelicopterRecoveryCandidate node = this; node.parent != null; node = node.parent) {
            depth++;
        }
        Vec3[] points = new Vec3[Math.min(limit, depth)];
        HelicopterRecoveryCandidate node = this;
        for (int index = depth - 1; index >= 0; index--, node = node.parent) {
            if (index < points.length) {
                points[index] = node.position;
            }
        }
        return List.of(points);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HelicopterRecoveryCandidate candidate)
                || !this.position.equals(candidate.position)
                || !this.heading.equals(candidate.heading)
                || this.lane != candidate.lane
                || this.climbed != candidate.climbed
                || Double.compare(this.travelCost, candidate.travelCost) != 0
                || Double.compare(this.score, candidate.score) != 0) {
            return false;
        }
        // Path identity includes waypoint positions, not ancestor scores or root state.
        HelicopterRecoveryCandidate left = this;
        HelicopterRecoveryCandidate right = candidate;
        while (left.parent != null && right.parent != null) {
            if (!left.position.equals(right.position)) {
                return false;
            }
            left = left.parent;
            right = right.parent;
        }
        return left.parent == null && right.parent == null;
    }

    @Override
    public int hashCode() {
        // Match ordered-list hashing without materializing the waypoint list.
        int pathHash = 0;
        int factor = 1;
        for (HelicopterRecoveryCandidate node = this; node.parent != null; node = node.parent) {
            pathHash += node.position.hashCode() * factor;
            factor *= 31;
        }
        pathHash += factor;
        int hash = this.position.hashCode();
        hash = 31 * hash + this.heading.hashCode();
        hash = 31 * hash + pathHash;
        hash = 31 * hash + this.lane;
        hash = 31 * hash + Boolean.hashCode(this.climbed);
        hash = 31 * hash + Double.hashCode(this.travelCost);
        return 31 * hash + Double.hashCode(this.score);
    }
}
