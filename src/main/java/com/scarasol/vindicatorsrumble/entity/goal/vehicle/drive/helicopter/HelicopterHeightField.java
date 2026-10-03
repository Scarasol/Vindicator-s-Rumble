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
        Vec3 delta = end.subtract(start);
        double horizontalLength = Math.hypot(delta.x, delta.z);
        int steps = Math.max(1, Mth.ceil(horizontalLength / SAMPLE_SPACING));
        Vec3 side = horizontalLength < 1.0E-4D
                ? Vec3.ZERO
                : new Vec3(-delta.z / horizontalLength, 0.0D, delta.x / horizontalLength);
        for (int step = 1; step <= steps; step++) {
            double progress = step / (double) steps;
            Vec3 point = start.add(delta.scale(progress));
            double bottomY = point.y + bottomOffset;
            if (bottomY < this.maxColumnHeight(level, point, side, halfWidth) + BOTTOM_CLEARANCE) {
                return false;
            }
        }
        return true;
    }

    double maxTerrainHeight(Level level, Vec3 start, Vec3 end, double halfWidth) {
        Vec3 delta = end.subtract(start);
        double horizontalLength = Math.hypot(delta.x, delta.z);
        int steps = Math.max(1, Mth.ceil(horizontalLength / SAMPLE_SPACING));
        Vec3 side = horizontalLength < 1.0E-4D
                ? Vec3.ZERO
                : new Vec3(-delta.z / horizontalLength, 0.0D, delta.x / horizontalLength);
        int maxHeight = Integer.MIN_VALUE;
        for (int step = 1; step <= steps; step++) {
            Vec3 point = start.add(delta.scale(step / (double) steps));
            maxHeight = Math.max(maxHeight, this.maxColumnHeight(level, point, side, halfWidth));
        }
        return maxHeight;
    }

    boolean mayHaveLineOfSight(Level level, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double horizontalLength = Math.hypot(delta.x, delta.z);
        int steps = Math.max(1, Mth.ceil(horizontalLength / SAMPLE_SPACING));
        for (int step = 1; step < steps; step++) {
            Vec3 point = start.add(delta.scale(step / (double) steps));
            if (point.y < this.height(level, point)) {
                return false;
            }
        }
        return true;
    }

    void corridorMetrics(Level level, Vec3 start, Vec3 end,
                         double bottomOffset, double halfWidth,
                         double requiredClearance, double[] metrics) {
        Vec3 delta = end.subtract(start);
        double horizontalLength = Math.hypot(delta.x, delta.z);
        int steps = Math.max(1, Mth.ceil(horizontalLength / SAMPLE_SPACING));
        Vec3 side = horizontalLength < 1.0E-4D
                ? Vec3.ZERO
                : new Vec3(-delta.z / horizontalLength, 0.0D, delta.x / horizontalLength);
        double bottomY = start.y + bottomOffset;
        double blocked = 0.0D;
        int maxHeight = Integer.MIN_VALUE;
        for (int step = 1; step <= steps; step++) {
            Vec3 point = start.add(delta.scale(step / (double) steps));
            int terrainHeight = this.maxColumnHeight(level, point, side, halfWidth);
            maxHeight = Math.max(maxHeight, terrainHeight);
            if (bottomY < terrainHeight + BOTTOM_CLEARANCE) {
                blocked += horizontalLength / steps;
            }
        }
        metrics[DEFICIT] = Math.max(0.0D,
                maxHeight + requiredClearance - start.y - bottomOffset);
        metrics[BLOCKED_LENGTH] = blocked;
    }

    private int maxColumnHeight(Level level, Vec3 point, Vec3 side, double halfWidth) {
        int maxHeight = this.height(level, point);
        if (halfWidth > 0.1D) {
            maxHeight = Math.max(maxHeight, this.height(level, point.add(side.scale(halfWidth))));
            maxHeight = Math.max(maxHeight, this.height(level, point.add(side.scale(-halfWidth))));
        }
        return maxHeight;
    }

    private int height(Level level, Vec3 point) {
        int x = Mth.floor(point.x);
        int z = Mth.floor(point.z);
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
