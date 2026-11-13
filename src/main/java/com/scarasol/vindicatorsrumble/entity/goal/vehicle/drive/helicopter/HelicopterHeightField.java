package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.helicopter;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

final class HelicopterHeightField {
    static final int DEFICIT = 0;
    static final int BLOCKED_LENGTH = 1;
    static final int METRIC_COUNT = 2;

    private static final int MAX_CACHED_COLUMNS = 16_384;
    private static final int MISSING_HEIGHT = Integer.MIN_VALUE;
    private static final double SAMPLE_SPACING = 1.0D;
    private static final double BOTTOM_CLEARANCE = 0.35D;

    private final Long2IntOpenHashMap heights = new Long2IntOpenHashMap();

    HelicopterHeightField() {
        this.heights.defaultReturnValue(MISSING_HEIGHT);
    }

    void reset() {
        this.heights.clear();
    }

    boolean corridorClear(Level level, Vec3 start, Vec3 end,
                          double bottomOffset, double halfWidth) {
        double deltaX = end.x - start.x;
        double deltaY = end.y - start.y;
        double deltaZ = end.z - start.z;
        double horizontalLength = Math.hypot(deltaX, deltaZ);
        if (horizontalLength < 1.0E-4D) {
            double bottomY = Math.min(start.y, end.y) + bottomOffset;
            int minX = Mth.floor(start.x - halfWidth);
            int maxX = Mth.floor(Math.nextDown(start.x + halfWidth));
            int minZ = Mth.floor(start.z - halfWidth);
            int maxZ = Mth.floor(Math.nextDown(start.z + halfWidth));
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (bottomY < this.height(level, x, z)
                            + BOTTOM_CLEARANCE) {
                        return false;
                    }
                }
            }
            return true;
        }
        int steps = Math.max(1, Mth.ceil(horizontalLength / SAMPLE_SPACING));
        double sideX = horizontalLength < 1.0E-4D ? 0.0D : -deltaZ / horizontalLength;
        double sideZ = horizontalLength < 1.0E-4D ? 0.0D : deltaX / horizontalLength;
        for (int step = 1; step <= steps; step++) {
            double progress = step / (double) steps;
            double pointX = start.x + deltaX * progress;
            double pointZ = start.z + deltaZ * progress;
            double bottomY = start.y + deltaY * progress + bottomOffset;
            if (bottomY < this.maxColumnHeight(level, pointX, pointZ, sideX, sideZ, halfWidth)
                    + BOTTOM_CLEARANCE) {
                return false;
            }
        }
        return true;
    }

    double maxTerrainHeight(Level level, Vec3 start, Vec3 end, double halfWidth) {
        double deltaX = end.x - start.x;
        double deltaZ = end.z - start.z;
        double horizontalLength = Math.hypot(deltaX, deltaZ);
        int steps = Math.max(1, Mth.ceil(horizontalLength / SAMPLE_SPACING));
        double sideX = horizontalLength < 1.0E-4D ? 0.0D : -deltaZ / horizontalLength;
        double sideZ = horizontalLength < 1.0E-4D ? 0.0D : deltaX / horizontalLength;
        int maxHeight = Integer.MIN_VALUE;
        for (int step = 1; step <= steps; step++) {
            double progress = step / (double) steps;
            double pointX = start.x + deltaX * progress;
            double pointZ = start.z + deltaZ * progress;
            maxHeight = Math.max(maxHeight,
                    this.maxColumnHeight(level, pointX, pointZ, sideX, sideZ, halfWidth));
        }
        return maxHeight;
    }

    boolean mayHaveLineOfSight(Level level, Vec3 start, Vec3 end) {
        double deltaX = end.x - start.x;
        double deltaY = end.y - start.y;
        double deltaZ = end.z - start.z;
        double horizontalLength = Math.hypot(deltaX, deltaZ);
        int steps = Math.max(1, Mth.ceil(horizontalLength / SAMPLE_SPACING));
        for (int step = 1; step < steps; step++) {
            double progress = step / (double) steps;
            double pointX = start.x + deltaX * progress;
            double pointZ = start.z + deltaZ * progress;
            if (start.y + deltaY * progress < this.height(level, pointX, pointZ)) {
                return false;
            }
        }
        return true;
    }

    void corridorMetrics(Level level, Vec3 start, Vec3 end,
                         double bottomOffset, double halfWidth,
                         double requiredClearance, double[] metrics) {
        double deltaX = end.x - start.x;
        double deltaZ = end.z - start.z;
        double horizontalLength = Math.hypot(deltaX, deltaZ);
        int steps = Math.max(1, Mth.ceil(horizontalLength / SAMPLE_SPACING));
        double sideX = horizontalLength < 1.0E-4D ? 0.0D : -deltaZ / horizontalLength;
        double sideZ = horizontalLength < 1.0E-4D ? 0.0D : deltaX / horizontalLength;
        double stepLength = horizontalLength / steps;
        double bottomY = start.y + bottomOffset;
        double blocked = 0.0D;
        int maxHeight = Integer.MIN_VALUE;
        for (int step = 1; step <= steps; step++) {
            double progress = step / (double) steps;
            double pointX = start.x + deltaX * progress;
            double pointZ = start.z + deltaZ * progress;
            int terrainHeight = this.maxColumnHeight(level, pointX, pointZ, sideX, sideZ, halfWidth);
            maxHeight = Math.max(maxHeight, terrainHeight);
            if (bottomY < terrainHeight + BOTTOM_CLEARANCE) {
                blocked += stepLength;
            }
        }
        metrics[DEFICIT] = Math.max(0.0D,
                maxHeight + requiredClearance - start.y - bottomOffset);
        metrics[BLOCKED_LENGTH] = blocked;
    }

    private int maxColumnHeight(Level level, double pointX, double pointZ,
                                double sideX, double sideZ, double halfWidth) {
        int maxHeight = this.height(level, pointX, pointZ);
        if (halfWidth > 0.1D) {
            // Match the exclusive outer faces used by collision boxes, including wall tangency.
            maxHeight = Math.max(maxHeight,
                    this.height(level, Math.nextAfter(pointX + sideX * halfWidth, pointX),
                            Math.nextAfter(pointZ + sideZ * halfWidth, pointZ)));
            maxHeight = Math.max(maxHeight,
                    this.height(level, Math.nextAfter(pointX - sideX * halfWidth, pointX),
                            Math.nextAfter(pointZ - sideZ * halfWidth, pointZ)));
        }
        return maxHeight;
    }

    private int height(Level level, double pointX, double pointZ) {
        int x = Mth.floor(pointX);
        int z = Mth.floor(pointZ);
        long key = columnKey(x, z);
        int cached = this.heights.get(key);
        if (cached != MISSING_HEIGHT) {
            return cached;
        }
        int sampled = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (this.heights.size() < MAX_CACHED_COLUMNS) {
            this.heights.put(key, sampled);
        }
        return sampled;
    }

    private static long columnKey(int x, int z) {
        return ((x & 0xFFFFFFFFL) << 32) | (z & 0xFFFFFFFFL);
    }
}
