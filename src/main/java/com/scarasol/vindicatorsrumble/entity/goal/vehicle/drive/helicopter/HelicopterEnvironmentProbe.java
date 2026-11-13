package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.helicopter;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Arrays;

final class HelicopterEnvironmentProbe {
    private static final int HOVER_SAMPLE_COUNT = 7;
    private static final int MOVING_SAMPLE_COUNT = 13;
    private static final int HOVER_TERRAIN_BUDGET = 3;
    private static final int MOVING_TERRAIN_BUDGET = 4;
    private static final int URGENT_TERRAIN_BUDGET = 5;
    static final double RAY_RANGE = 24.0D;
    private static final double FRONT_BLOCKED_DISTANCE = 12.0D;
    private static final double DIRECTION_REALIGN_DOT = 0.965925826D;
    private static final double ELEVATED_RAY_Y_OFFSET = 8.0D;
    private static final double MAX_TERRAIN_RISE_PER_TICK = 2.5D;
    private static final double MAX_TERRAIN_DROP_PER_TICK = 1.25D;
    private static final double TERRAIN_DELTA_MIN = -8.0D;
    private static final double TERRAIN_DELTA_MAX = 30.0D;
    private static final int RAY_FRONT = 0;
    private static final int RAY_ELEVATED_FRONT = 1;
    private static final double[][] TERRAIN_SAMPLES = {
            {0.0D, 0.0D, 1.25D},
            {8.0D, 0.0D, 1.35D},
            {16.0D, 0.0D, 1.2D},
            {28.0D, 0.0D, 0.95D},
            {12.0D, -8.0D, 0.75D},
            {12.0D, 8.0D, 0.75D},
            {24.0D, -10.0D, 0.62D},
            {24.0D, 10.0D, 0.62D},
            {36.0D, -12.0D, 0.45D},
            {36.0D, 12.0D, 0.45D},
            {16.0D, -16.0D, 0.35D},
            {16.0D, 16.0D, 0.35D},
            {40.0D, 0.0D, 0.35D}
    };

    private final double[] terrainHeights = new double[TERRAIN_SAMPLES.length];
    private final boolean[] terrainValid = new boolean[TERRAIN_SAMPLES.length];
    private int terrainCursor;
    private int rayCursor;
    private double smoothedTerrainPreferredY = Double.NaN;
    private double frontClearance = RAY_RANGE;
    private double frontObstacleY = Double.NaN;
    private double elevatedFrontClearance = RAY_RANGE;
    private Vec3 sampledMovementDirection;
    private Vec3 terrainSampleDirection;

    void reset() {
        Arrays.fill(this.terrainHeights, 0.0D);
        Arrays.fill(this.terrainValid, false);
        this.terrainCursor = 0;
        this.rayCursor = 0;
        this.smoothedTerrainPreferredY = Double.NaN;
        this.frontClearance = RAY_RANGE;
        this.frontObstacleY = Double.NaN;
        this.elevatedFrontClearance = RAY_RANGE;
        this.sampledMovementDirection = null;
        this.terrainSampleDirection = null;
    }

    HelicopterEnvironment sample(VehicleController controller, @Nullable Entity target,
                                 HelicopterFlightPlanner.AircraftState state,
                                 HelicopterFlightPlanner.Plan plan,
                                 double hoverHeight,
                                 @Nullable Vec3 movementDestination) {
        Level level = controller.level();
        Entity vehicle = controller.entity();
        if (level == null || vehicle == null) {
            return HelicopterEnvironment.NONE;
        }

        Vec3 direction = movementDirection(state, plan, movementDestination);
        boolean moving = usesMovingTerrainSamples(plan, state);
        HitResult lineOfSightHit = target == null
                ? null
                : lineHit(level, vehicle, controller.boundingBox().getCenter(), target.getBoundingBox().getCenter());
        boolean hasLineOfSight = target == null || lineOfSightHit.getType() == HitResult.Type.MISS;
        boolean urgent = target != null && (!hasLineOfSight || fastPass(plan));
        int sampleCount = moving ? MOVING_SAMPLE_COUNT : HOVER_SAMPLE_COUNT;
        int terrainBudget = urgent ? URGENT_TERRAIN_BUDGET : moving ? MOVING_TERRAIN_BUDGET : HOVER_TERRAIN_BUDGET;
        this.resetTerrainSamplesIfDirectionChanged(direction);
        this.refreshTerrain(level, state, direction, sampleCount, terrainBudget);
        double terrainPreferredY = this.terrainPreferredY(state, hoverHeight, sampleCount);

        boolean shouldProbeObstacle = !hasLineOfSight
                || fastPass(plan)
                || plan.phase() == HelicopterFlightPlanner.Phase.APPROACH
                || plan.phase() == HelicopterFlightPlanner.Phase.PULL_OUT;
        if (shouldProbeObstacle) {
            if (this.directionChanged(direction)) {
                this.refreshForwardRays(level, vehicle, controller.boundingBox(), direction);
                // Both rays are fresh; preserve the next-tick rotation without casting them twice.
                this.rayCursor = urgent ? RAY_FRONT : RAY_ELEVATED_FRONT;
            } else {
                this.refreshObstacleRays(level, vehicle, controller.boundingBox(), direction, urgent ? 2 : 1);
            }
        } else {
            this.frontClearance = RAY_RANGE;
            this.frontObstacleY = Double.NaN;
            this.elevatedFrontClearance = RAY_RANGE;
        }

        boolean frontBlocked = shouldProbeObstacle && this.frontClearance < FRONT_BLOCKED_DISTANCE;
        boolean elevatedFrontClear = this.elevatedFrontClearance >= FRONT_BLOCKED_DISTANCE;
        return new HelicopterEnvironment(
                hasLineOfSight,
                frontBlocked,
                elevatedFrontClear,
                0,
                terrainPreferredY,
                this.frontClearance,
                this.elevatedFrontClearance);
    }

    double frontObstacleY() {
        return this.frontObstacleY;
    }

    HelicopterEnvironment realignMovement(VehicleController controller,
                                           HelicopterFlightPlanner.AircraftState state,
                                           HelicopterFlightPlanner.Plan plan,
                                           HelicopterEnvironment environment) {
        Level level = controller.level();
        Entity vehicle = controller.entity();
        Vec3 direction = movementDirection(state, plan, null);
        if (level == null || vehicle == null || !this.directionChanged(direction)) {
            return environment;
        }

        this.refreshForwardRays(level, vehicle, controller.boundingBox(), direction);
        boolean frontBlocked = this.frontClearance < FRONT_BLOCKED_DISTANCE;
        boolean elevatedFrontClear = this.elevatedFrontClearance >= FRONT_BLOCKED_DISTANCE;
        return new HelicopterEnvironment(
                environment.hasLineOfSight(),
                frontBlocked,
                elevatedFrontClear,
                0,
                environment.terrainPreferredY(),
                this.frontClearance,
                this.elevatedFrontClearance);
    }

    private void refreshTerrain(Level level, HelicopterFlightPlanner.AircraftState state,
                                Vec3 direction, int sampleCount, int budget) {
        Vec3 right = right(direction);
        for (int i = 0; i < budget; i++) {
            int index = this.terrainCursor % sampleCount;
            double[] sample = TERRAIN_SAMPLES[index];
            Vec3 point = state.position()
                    .add(direction.scale(sample[0]))
                    .add(right.scale(sample[1]));
            this.terrainHeights[index] = level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(point.x),
                    Mth.floor(point.z));
            this.terrainValid[index] = true;
            this.terrainCursor = (index + 1) % sampleCount;
        }
    }

    private void resetTerrainSamplesIfDirectionChanged(Vec3 direction) {
        if (this.terrainSampleDirection != null
                && this.terrainSampleDirection.dot(direction) >= DIRECTION_REALIGN_DOT) {
            return;
        }
        Arrays.fill(this.terrainValid, false);
        this.terrainCursor = 0;
        this.smoothedTerrainPreferredY = Double.NaN;
        this.terrainSampleDirection = direction;
    }

    private double terrainPreferredY(HelicopterFlightPlanner.AircraftState state, double hoverHeight, int sampleCount) {
        double baseGroundY = state.position().y - Math.min(state.altitude(), HelicopterFlightControl.ALTITUDE_SAMPLE);
        double weighted = 0.0D;
        double totalWeight = 0.0D;
        for (int i = 0; i < sampleCount; i++) {
            if (!this.terrainValid[i]) {
                continue;
            }
            double cappedDelta = clamp(this.terrainHeights[i] - baseGroundY, TERRAIN_DELTA_MIN, TERRAIN_DELTA_MAX);
            weighted += (baseGroundY + cappedDelta) * TERRAIN_SAMPLES[i][2];
            totalWeight += TERRAIN_SAMPLES[i][2];
        }
        if (totalWeight <= 1.0E-4D) {
            return Double.NaN;
        }
        double rawPreferredY = weighted / totalWeight + hoverHeight;
        if (Double.isNaN(this.smoothedTerrainPreferredY)) {
            this.smoothedTerrainPreferredY = rawPreferredY;
        } else {
            double delta = clamp(rawPreferredY - this.smoothedTerrainPreferredY,
                    -MAX_TERRAIN_DROP_PER_TICK,
                    MAX_TERRAIN_RISE_PER_TICK);
            this.smoothedTerrainPreferredY += delta;
        }
        return this.smoothedTerrainPreferredY;
    }

    private void refreshObstacleRays(Level level, Entity vehicle, AABB box, Vec3 direction, int budget) {
        for (int i = 0; i < budget; i++) {
            switch (this.rayCursor) {
                case RAY_FRONT -> this.frontClearance = rayDistance(level, vehicle, rayOrigin(box, 0.0D), direction, true);
                case RAY_ELEVATED_FRONT -> this.elevatedFrontClearance = rayDistance(level, vehicle, rayOrigin(box, ELEVATED_RAY_Y_OFFSET), direction, false);
                default -> {
                }
            }
            this.rayCursor = (this.rayCursor + 1) & 1;
        }
    }

    private void refreshForwardRays(Level level, Entity vehicle, AABB box, Vec3 direction) {
        this.frontClearance = rayDistance(level, vehicle, rayOrigin(box, 0.0D), direction, true);
        this.elevatedFrontClearance = rayDistance(level, vehicle,
                rayOrigin(box, ELEVATED_RAY_Y_OFFSET), direction, false);
        this.sampledMovementDirection = direction;
        this.rayCursor = RAY_FRONT;
    }

    private boolean directionChanged(Vec3 direction) {
        return this.sampledMovementDirection == null
                || this.sampledMovementDirection.dot(direction) < DIRECTION_REALIGN_DOT;
    }

    private static HitResult lineHit(Level level, Entity vehicle, Vec3 start, Vec3 end) {
        return level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                vehicle));
    }

    private double rayDistance(Level level, Entity vehicle, Vec3 start, Vec3 direction, boolean front) {
        Vec3 end = start.add(direction.scale(RAY_RANGE));
        HitResult hit = level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                vehicle));
        if (front) {
            this.frontObstacleY = hit.getType() == HitResult.Type.BLOCK
                    ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    ((BlockHitResult) hit).getBlockPos().getX(), ((BlockHitResult) hit).getBlockPos().getZ())
                    : Double.NaN;
        }
        return hit.getType() == HitResult.Type.MISS
                ? RAY_RANGE
                : Math.min(RAY_RANGE, start.distanceTo(hit.getLocation()));
    }

    private static Vec3 rayOrigin(AABB box, double yOffset) {
        Vec3 center = box.getCenter();
        double y = box.minY + clamp(box.getYsize() * 0.55D, 0.7D, 2.4D) + yOffset;
        return new Vec3(center.x, y, center.z);
    }

    private static boolean usesMovingTerrainSamples(HelicopterFlightPlanner.Plan plan,
                                                    HelicopterFlightPlanner.AircraftState state) {
        if (fastPass(plan)
                || plan.phase() == HelicopterFlightPlanner.Phase.APPROACH
                || plan.phase() == HelicopterFlightPlanner.Phase.PULL_OUT
                || plan.phase() == HelicopterFlightPlanner.Phase.TAKEOFF) {
            return true;
        }
        Vec3 velocity = state.velocity();
        return velocity.x * velocity.x + velocity.z * velocity.z > 0.04D;
    }

    private static boolean fastPass(HelicopterFlightPlanner.Plan plan) {
        return plan.phase() == HelicopterFlightPlanner.Phase.ATTACK_RUN
                || plan.phase() == HelicopterFlightPlanner.Phase.DIVE_PASS;
    }

    private static Vec3 movementDirection(HelicopterFlightPlanner.AircraftState state,
                                          HelicopterFlightPlanner.Plan plan,
                                          @Nullable Vec3 movementDestination) {
        Vec3 destination = movementDestination == null ? plan.desiredPosition() : movementDestination;
        Vec3 delta = destination.subtract(state.position());
        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
        if (horizontal.lengthSqr() > 1.0D) {
            return horizontal.normalize();
        }
        return yawForward(state.yaw());
    }

    private static Vec3 yawForward(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
    }

    private static Vec3 right(Vec3 forward) {
        return new Vec3(-forward.z, 0.0D, forward.x);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
