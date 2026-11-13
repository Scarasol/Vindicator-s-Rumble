package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.helicopter;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

final class HelicopterVisibilityRecovery {
    private static final int ACTION_FORWARD = 0;
    private static final int ACTION_LEFT = 1;
    private static final int ACTION_RIGHT = 2;
    private static final int ACTION_CLIMB = 3;
    private static final int ACTION_COUNT = 4;
    private static final int LANE_LEFT = -1;
    private static final int LANE_CENTER = 0;
    private static final int LANE_RIGHT = 1;
    private static final int BEAM_WIDTH = 6;
    private static final int MAX_DEPTH = 6;
    private static final int SAFE_PREFIX_LENGTH = 2;
    private static final int MAX_TRAIL_POINTS = 16;
    private static final int FAILED_SEARCH_RETRY_TICKS = 20;
    private static final int MAX_SEARCHES_PER_LEVEL_TICK = 8;
    private static final Map<Level, SearchBudget> SEARCH_BUDGETS = new WeakHashMap<>();
    private static final double SEGMENT_LENGTH = 11.0D;
    private static final double CLIMB_FORWARD_LENGTH = 5.0D;
    private static final double CLIMB_HEIGHT = 7.0D;
    private static final double CLIMB_LOOKAHEAD = 8.0D;
    private static final double CLIMB_CLEARANCE = 4.0D;
    private static final double TURN_DEGREES = 45.0D;
    private static final double WAYPOINT_PASS_DISTANCE = HelicopterFlightPlanner.MOVEMENT_PASS_DISTANCE;
    private static final double WAYPOINT_VERTICAL_REACHED_DISTANCE = 4.0D;
    private static final int WAYPOINT_STALE_TICKS = 32;
    private static final double WAYPOINT_PROGRESS_EPSILON = 0.75D;
    private static final double WAYPOINT_STALE_MIN_DISTANCE = 12.0D;
    private static final double VISITED_CELL_SIZE = 4.0D;
    private static final long NO_REJECTED_LEG = Long.MIN_VALUE;
    private static final double MIN_FOLLOW_RANGE_MARGIN = 3.0D;
    private static final double LANE_CENTER_WIDTH = 2.0D;
    private static final double MIN_FRONTIER_PROGRESS = 2.5D;
    private static final double MIN_CLIMB_PROGRESS = 3.0D;
    private static final double TURN_TRAVEL_COST = 3.6D;
    private static final double FORWARD_TRAVEL_COST = 1.0D;
    private static final double CLIMB_TRAVEL_COST = 1.65D;
    private static final double TARGET_DISTANCE_WEIGHT = 0.10D;
    private static final double HEADING_WEIGHT = 1.5D;
    private static final double ALTITUDE_WEIGHT = 0.04D;
    private static final double PARTIAL_SIDE_PROGRESS_WEIGHT = 0.14D;
    private static final double PARTIAL_FLYOVER_PROGRESS_WEIGHT = 0.08D;
    private static final double PARTIAL_SIDE_RANGE_REWARD_FADE = 0.45D;
    private static final double PARTIAL_LOW_SIDE_RANGE_PENALTY = 0.55D;
    private static final double PARTIAL_SIDE_FREE_PROGRESS = 6.0D;
    private static final double PARTIAL_FLYOVER_GAIN_WEIGHT = 0.24D;
    private static final double PARTIAL_CLEAR_FORWARD_WEIGHT = 0.05D;
    private static final double PARTIAL_BLOCKED_PROGRESS_WEIGHT = 0.07D;
    private static final double MIN_DEFICIT_PROGRESS = 1.5D;
    private static final double MIN_BLOCKED_PROGRESS = 2.0D;
    private static final double CLEAR_CORRIDOR_DEFICIT = 1.0D;
    private static final double CLEAR_BLOCKED_LENGTH = 1.0D;
    private static final double RANGE_PRESSURE_START = 0.78D;
    private static final double RANGE_PRESSURE_WEIGHT = 1.55D;
    private static final double TARGET_CORRIDOR_LOOKAHEAD = 12.0D;
    private static final double TARGET_CORRIDOR_CLEARANCE = 5.0D;
    private static final double OPEN_CORRIDOR_FORWARD_BONUS = 0.12D;
    private static final double OPEN_CORRIDOR_SIDE_COST = 0.12D;
    private static final double OPEN_CORRIDOR_CLIMB_COST = 0.45D;
    private final ArrayDeque<Vec3> route = new ArrayDeque<>();
    private final ArrayDeque<Vec3> trail = new ArrayDeque<>();
    private final Set<Long> visited = new HashSet<>();
    private final HelicopterHeightField heightField = new HelicopterHeightField();
    private final double[] corridorMetrics = new double[HelicopterHeightField.METRIC_COUNT];
    private int targetId = -1;
    private Vec3 episodeOrigin;
    private Vec3 episodeForward;
    private Vec3 waypoint;
    private Vec3 waypointStart;
    private boolean backtracking;
    private int waypointTicks;
    private double bestWaypointDistance;
    private double bestWaypointProgress;
    private double maxLeftProgress;
    private double maxRightProgress;
    private double maxClimbProgress;
    private long rejectedLegOrigin = NO_REJECTED_LEG;
    private long rejectedLegDestination = NO_REJECTED_LEG;
    private long nextSearchTick = Long.MIN_VALUE;
    private Vec3 failedSearchPosition;
    private Vec3 failedSearchTarget;

    void reset() {
        this.route.clear();
        this.trail.clear();
        this.visited.clear();
        this.heightField.reset();
        this.targetId = -1;
        this.episodeOrigin = null;
        this.episodeForward = null;
        this.waypoint = null;
        this.waypointStart = null;
        this.backtracking = false;
        this.resetWaypointProgress();
        this.maxLeftProgress = 0.0D;
        this.maxRightProgress = 0.0D;
        this.maxClimbProgress = 0.0D;
        this.rejectedLegOrigin = NO_REJECTED_LEG;
        this.rejectedLegDestination = NO_REJECTED_LEG;
        this.nextSearchTick = Long.MIN_VALUE;
        this.failedSearchPosition = null;
        this.failedSearchTarget = null;
    }

    void invalidateCurrentRoute(HelicopterFlightPlanner.AircraftState state) {
        if (this.waypoint != null) {
            this.rejectedLegOrigin = visitedKey(state.position());
            this.rejectedLegDestination = visitedKey(this.waypoint);
        }
        this.clearCurrentRoute();
    }

    private void clearCurrentRoute() {
        this.route.clear();
        this.waypoint = null;
        this.waypointStart = null;
        this.backtracking = false;
        this.resetWaypointProgress();
    }

    @Nullable
    Vec3 update(VehicleController controller, @Nullable Entity target,
                HelicopterFlightPlanner.AircraftState state,
                boolean hasLineOfSight, boolean frontBlocked, double followRange) {
        if (target == null) {
            this.reset();
            return null;
        }
        if (hasLineOfSight && !frontBlocked) {
            this.reset();
            return null;
        }
        if (target.getId() != this.targetId) {
            this.beginEpisode(target, state);
        }
        this.updateFrontiers(state.position());
        if (needsRangeBrake(controller, state, target, followRange)) {
            this.clearCurrentRoute();
            return null;
        }
        Vec3 currentWaypoint = this.continueRoute(state);
        if (currentWaypoint != null) {
            return currentWaypoint;
        }

        long now = controller.level().getGameTime();
        if (now < this.nextSearchTick && this.failedSearchPosition != null
                && state.position().distanceToSqr(this.failedSearchPosition) < 4.0D
                && target.position().distanceToSqr(this.failedSearchTarget) < 4.0D) {
            return null;
        }
        if (!SEARCH_BUDGETS.computeIfAbsent(controller.level(), key -> new SearchBudget())
                .acquire(controller.entity().getUUID(), now)) {
            return null;
        }
        double minimumY = HelicopterFlightPlanner.recoveryFloorY(controller, state, target)
                + HelicopterFlightPlanner.VERTICAL_TOLERANCE;
        List<Vec3> planned = this.search(controller, target, state, followRange, minimumY);
        if (!planned.isEmpty()) {
            this.failedSearchPosition = null;
            this.failedSearchTarget = null;
            this.route.addAll(planned);
            return this.takeNextWaypoint(state);
        }
        this.nextSearchTick = now + FAILED_SEARCH_RETRY_TICKS + Math.floorMod(controller.entity().getId(), 5);
        this.failedSearchPosition = state.position();
        this.failedSearchTarget = target.position();
        if (controller.estimatedHorizontalStoppingDistance() > MIN_FOLLOW_RANGE_MARGIN) {
            return null;
        }
        return this.beginBacktrack(state, minimumY);
    }

    private static final class SearchBudget {
        private final LinkedHashMap<UUID, Long> waiting = new LinkedHashMap<>();
        private long tick = Long.MIN_VALUE;
        private int searches;

        boolean acquire(UUID vehicle, long now) {
            if (now != this.tick) {
                if (now < this.tick) this.waiting.clear();
                this.tick = now;
                this.searches = 0;
            }
            // Preserve queue order so entity tick order cannot monopolize the shared budget.
            this.waiting.put(vehicle, now);
            var iterator = this.waiting.entrySet().iterator();
            while (iterator.hasNext()) {
                var first = iterator.next();
                if (first.getValue() < now - 2L) {
                    iterator.remove();
                    continue;
                }
                if (this.searches >= MAX_SEARCHES_PER_LEVEL_TICK || !first.getKey().equals(vehicle)) {
                    return false;
                }
                iterator.remove();
                this.searches++;
                return true;
            }
            return false;
        }
    }

    @Nullable
    Vec3 waypoint() {
        return this.waypoint;
    }

    boolean recovering() {
        return this.targetId >= 0;
    }

    private void beginEpisode(Entity target, HelicopterFlightPlanner.AircraftState state) {
        this.reset();
        this.targetId = target.getId();
        this.episodeOrigin = state.position();
        Vec3 targetDirection = target.getBoundingBox().getCenter().subtract(state.position());
        this.episodeForward = horizontalUnit(targetDirection, yawForward(state.yaw()));
        this.remember(state.position());
    }

    private List<Vec3> search(VehicleController controller, Entity target,
                              HelicopterFlightPlanner.AircraftState state, double followRange, double minimumY) {
        Level level = controller.level();
        Entity vehicle = controller.entity();
        if (level == null || vehicle == null || this.episodeOrigin == null
                || this.episodeForward == null || followRange <= MIN_FOLLOW_RANGE_MARGIN) {
            return List.of();
        }
        long rootKey = visitedKey(state.position());
        if (this.rejectedLegOrigin != rootKey) {
            this.rejectedLegOrigin = NO_REJECTED_LEG;
            this.rejectedLegDestination = NO_REJECTED_LEG;
        }

        AABB box = controller.boundingBox();
        Vec3 entityPosition = vehicle.position();
        Vec3 centerOffset = box.getCenter().subtract(entityPosition);
        double bottomOffset = box.minY - entityPosition.y;
        double halfWidth = Math.max(box.getXsize(), box.getZsize()) * 0.5D;
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        Vec3 targetHeading = horizontalUnit(targetCenter.subtract(state.position()), yawForward(state.yaw()));
        Vec3 horizontalVelocity = state.velocity().multiply(1.0D, 0.0D, 1.0D);
        Vec3 rootHeading = boundedInitialHeading(targetHeading, initialHeading(state));
        double stoppingDistance = controller.estimatedHorizontalStoppingDistance();
        double maxTargetRange = Math.max(0.0D, followRange - MIN_FOLLOW_RANGE_MARGIN);
        Vec3 inertiaPoint = projectedInertiaPoint(
                state.position(), horizontalVelocity, stoppingDistance);
        this.heightField.corridorMetrics(level, state.position(), targetCenter,
                bottomOffset, halfWidth, TARGET_CORRIDOR_CLEARANCE, this.corridorMetrics);
        double currentDeficit = this.corridorMetrics[HelicopterHeightField.DEFICIT];
        double currentBlockedLength = this.corridorMetrics[HelicopterHeightField.BLOCKED_LENGTH];
        List<HelicopterRecoveryCandidate> beam = List.of(new HelicopterRecoveryCandidate(
                state.position(), rootHeading, null, LANE_CENTER, false,
                0.0D, 0.0D));
        HelicopterRecoveryCandidate bestPartial = null;

        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            List<HelicopterRecoveryCandidate> expanded = new ArrayList<>(beam.size() * ACTION_COUNT);
            for (HelicopterRecoveryCandidate candidate : beam) {
                for (int action = 0; action < ACTION_COUNT; action++) {
                    HelicopterRecoveryCandidate next = this.advance(
                            level, bottomOffset, halfWidth, candidate, action,
                            targetCenter, maxTargetRange, followRange, inertiaPoint, minimumY);
                    if (next != null) {
                        expanded.add(next);
                    }
                }
            }
            if (expanded.isEmpty()) {
                break;
            }
            expanded.sort(Comparator.comparingDouble(HelicopterRecoveryCandidate::score));
            for (HelicopterRecoveryCandidate candidate : expanded) {
                Vec3 sightStart = candidate.position().add(centerOffset);
                if (this.candidateHasLineOfSight(level, vehicle, sightStart, targetCenter)) {
                    return candidate.path(MAX_DEPTH);
                }
            }
            beam = this.retainDiversity(expanded);
            HelicopterRecoveryCandidate depthPartial = this.selectPartialCandidate(
                    level, beam, targetCenter, bottomOffset, halfWidth, maxTargetRange,
                    currentDeficit, currentBlockedLength);
            if (depthPartial != null) {
                bestPartial = depthPartial;
            }
        }

        if (bestPartial == null || bestPartial.parent() == null) {
            return List.of();
        }
        return bestPartial.path(SAFE_PREFIX_LENGTH);
    }

    @Nullable
    private HelicopterRecoveryCandidate advance(Level level, double bottomOffset, double halfWidth,
                                                 HelicopterRecoveryCandidate candidate, int action,
                                                 Vec3 targetCenter, double maxTargetRange,
                                                 double followRange, Vec3 inertiaPoint, double minimumY) {
        Vec3 nextHeading = candidate.heading();
        Vec3 movement;
        double actionCost;
        boolean climbed = candidate.climbed();
        if (action == ACTION_LEFT) {
            nextHeading = rotate(nextHeading, -TURN_DEGREES);
            movement = nextHeading.scale(SEGMENT_LENGTH);
            actionCost = TURN_TRAVEL_COST;
        } else if (action == ACTION_RIGHT) {
            nextHeading = rotate(nextHeading, TURN_DEGREES);
            movement = nextHeading.scale(SEGMENT_LENGTH);
            actionCost = TURN_TRAVEL_COST;
        } else if (action == ACTION_CLIMB) {
            movement = nextHeading.scale(CLIMB_FORWARD_LENGTH).add(0.0D, CLIMB_HEIGHT, 0.0D);
            actionCost = CLIMB_TRAVEL_COST;
            climbed = true;
        } else {
            movement = nextHeading.scale(SEGMENT_LENGTH);
            actionCost = FORWARD_TRAVEL_COST;
        }
        Vec3 nextPosition = candidate.position().add(movement);
        if (nextPosition.y < minimumY) {
            nextPosition = new Vec3(nextPosition.x, minimumY, nextPosition.z);
        }
        if (action == ACTION_CLIMB && !this.heightField.corridorClear(
                level, candidate.position(), nextPosition, bottomOffset, halfWidth)) {
            // A wall can leave room to ascend but not to translate while climbing.
            nextPosition = candidate.position().add(0.0D, CLIMB_HEIGHT, 0.0D);
        }
        int nextLane = this.lane(nextPosition);
        boolean firstLeg = candidate.parent() == null;
        if (candidate.lane() != LANE_CENTER && nextLane == -candidate.lane()
                || !withinRangeEnvelope(candidate.position(), nextPosition, targetCenter,
                maxTargetRange, followRange)
                || firstLeg && !this.firstLegClear(level, candidate.position(), inertiaPoint,
                nextPosition, targetCenter, bottomOffset, halfWidth, followRange)
                || firstLeg && this.rejectedLegOrigin == visitedKey(candidate.position())
                && this.rejectedLegDestination == visitedKey(nextPosition)
                || this.visited.contains(visitedKey(nextPosition))
                || action == ACTION_CLIMB
                && !this.climbUseful(level, candidate.position(), nextPosition,
                nextHeading, bottomOffset, halfWidth)
                || !this.heightField.corridorClear(
                level, candidate.position(), nextPosition, bottomOffset, halfWidth)) {
            return null;
        }

        double travelCost = candidate.travelCost() + actionCost;
        double targetCorridorClearance = this.targetCorridorClearance(
                level, nextPosition, targetCenter, nextHeading, bottomOffset, halfWidth);
        double score = this.score(nextPosition, nextHeading, targetCenter,
                travelCost, action, targetCorridorClearance, maxTargetRange);
        return new HelicopterRecoveryCandidate(
                nextPosition, nextHeading, candidate, nextLane, climbed,
                travelCost, score);
    }

    private List<HelicopterRecoveryCandidate> retainDiversity(
            List<HelicopterRecoveryCandidate> candidates) {
        Set<HelicopterRecoveryCandidate> retained = new LinkedHashSet<>();
        int[] lanes = {LANE_CENTER, LANE_LEFT, LANE_RIGHT};
        for (int lane : lanes) {
            candidates.stream()
                    .filter(candidate -> candidate.lane() == lane)
                    .findFirst()
                    .ifPresent(retained::add);
        }
        retainLateralExtreme(retained, candidates, LANE_LEFT);
        retainLateralExtreme(retained, candidates, LANE_RIGHT);
        candidates.stream()
                .filter(HelicopterRecoveryCandidate::climbed)
                .max(Comparator.comparingDouble(this::flyoverProgress))
                .ifPresent(retained::add);
        for (HelicopterRecoveryCandidate candidate : candidates) {
            if (retained.size() >= BEAM_WIDTH) {
                break;
            }
            retained.add(candidate);
        }
        List<HelicopterRecoveryCandidate> result = new ArrayList<>(retained);
        result.sort(Comparator.comparingDouble(HelicopterRecoveryCandidate::score));
        return result.size() <= BEAM_WIDTH
                ? List.copyOf(result)
                : List.copyOf(result.subList(0, BEAM_WIDTH));
    }

    private void retainLateralExtreme(Set<HelicopterRecoveryCandidate> retained,
                                      List<HelicopterRecoveryCandidate> candidates, int lane) {
        candidates.stream()
                .filter(candidate -> candidate.lane() == lane)
                .max(Comparator.comparingDouble(this::lateralProgress))
                .ifPresent(retained::add);
    }

    @Nullable
    private HelicopterRecoveryCandidate selectPartialCandidate(
            Level level, List<HelicopterRecoveryCandidate> candidates, Vec3 targetCenter,
            double bottomOffset, double halfWidth, double maxTargetRange, double currentDeficit,
            double currentBlockedLength) {
        HelicopterRecoveryCandidate best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        // Full target corridors only rank partial frontiers, not complete routes or beam diversity.
        for (HelicopterRecoveryCandidate candidate : candidates) {
            this.heightField.corridorMetrics(level, candidate.position(), targetCenter,
                    bottomOffset, halfWidth, TARGET_CORRIDOR_CLEARANCE, this.corridorMetrics);
            double corridorDeficit = this.corridorMetrics[HelicopterHeightField.DEFICIT];
            double blockedLength = this.corridorMetrics[HelicopterHeightField.BLOCKED_LENGTH];
            if (!this.expandsFrontier(candidate, corridorDeficit, blockedLength,
                    currentDeficit, currentBlockedLength)) {
                continue;
            }
            double score = this.partialCandidateScore(candidate, targetCenter, maxTargetRange,
                    corridorDeficit, blockedLength, currentDeficit, currentBlockedLength);
            if (best == null || Double.compare(score, bestScore) < 0) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean expandsFrontier(HelicopterRecoveryCandidate candidate,
                                    double corridorDeficit, double blockedLength,
                                    double currentDeficit,
                                    double currentBlockedLength) {
        double lateral = this.lateralCoordinate(candidate.position());
        boolean expandsSide = candidate.lane() == LANE_LEFT
                && -lateral >= this.maxLeftProgress + MIN_FRONTIER_PROGRESS
                || candidate.lane() == LANE_RIGHT
                && lateral >= this.maxRightProgress + MIN_FRONTIER_PROGRESS;
        double flyoverGain = currentDeficit - corridorDeficit;
        double blockedProgress = currentBlockedLength - blockedLength;
        double forward = Math.max(0.0D, this.forwardProgress(candidate.position()));
        boolean highEnough = corridorDeficit <= CLEAR_CORRIDOR_DEFICIT;
        boolean corridorOpen = highEnough && blockedLength <= CLEAR_BLOCKED_LENGTH;
        boolean expandsFlyover = currentDeficit > CLEAR_CORRIDOR_DEFICIT
                && flyoverGain >= MIN_DEFICIT_PROGRESS
                || blockedProgress >= MIN_BLOCKED_PROGRESS
                || corridorOpen && forward >= MIN_FRONTIER_PROGRESS;
        return expandsFlyover || expandsSide;
    }

    private double partialCandidateScore(HelicopterRecoveryCandidate candidate,
                                         Vec3 targetCenter, double maxTargetRange,
                                         double corridorDeficit, double blockedLength,
                                         double currentDeficit,
                                         double currentBlockedLength) {
        double sideProgress = this.lateralProgress(candidate);
        double forwardProgress = Math.max(0.0D, this.forwardProgress(candidate.position()));
        double flyoverProgress = this.flyoverProgress(candidate);
        double flyoverGain = Math.max(0.0D, currentDeficit - corridorDeficit);
        double blockedProgress = Math.max(0.0D, currentBlockedLength - blockedLength);
        double climbProgress = Math.max(0.0D,
                candidate.position().y - this.episodeOrigin.y - this.maxClimbProgress);
        double clearForward = corridorDeficit <= CLEAR_CORRIDOR_DEFICIT
                && blockedLength <= CLEAR_BLOCKED_LENGTH
                ? forwardProgress : 0.0D;
        double targetDistance = horizontalDistance(candidate.position(), targetCenter);
        double rangeRatio = maxTargetRange <= 1.0D ? 0.0D : targetDistance / maxTargetRange;
        double pressure = Mth.clamp(
                (rangeRatio - RANGE_PRESSURE_START) / (1.0D - RANGE_PRESSURE_START),
                0.0D, 1.0D);
        double flyoverSupport = Math.max(flyoverProgress,
                flyoverGain * 1.5D + blockedProgress
                        + Math.min(clearForward, 16.0D) * 0.5D);
        double nonSideSupport = Math.max(flyoverSupport,
                Math.max(climbProgress, Math.min(forwardProgress, 12.0D) * 0.35D));
        double weakSideFactor = flyoverGain > 0.0D || clearForward > 0.0D ? 1.0D : 0.35D;
        double sideReward = PARTIAL_SIDE_PROGRESS_WEIGHT * sideProgress
                * weakSideFactor * (1.0D - PARTIAL_SIDE_RANGE_REWARD_FADE * pressure);
        double lowSideProgress = Math.max(0.0D,
                sideProgress - nonSideSupport - PARTIAL_SIDE_FREE_PROGRESS);
        return candidate.score()
                - sideReward
                - PARTIAL_FLYOVER_PROGRESS_WEIGHT * flyoverProgress
                - PARTIAL_FLYOVER_GAIN_WEIGHT * flyoverGain
                - PARTIAL_CLEAR_FORWARD_WEIGHT * clearForward
                - PARTIAL_BLOCKED_PROGRESS_WEIGHT * blockedProgress
                + PARTIAL_LOW_SIDE_RANGE_PENALTY * pressure * lowSideProgress;
    }

    private double score(Vec3 position, Vec3 heading, Vec3 targetCenter,
                         double travelCost, int action,
                         double targetCorridorClearance, double maxTargetRange) {
        Vec3 targetDirection = horizontalUnit(targetCenter.subtract(position), heading);
        double headingPenalty = (1.0D
                - Mth.clamp(heading.dot(targetDirection), -1.0D, 1.0D)) * HEADING_WEIGHT;
        double altitude = Math.max(0.0D, position.y - this.episodeOrigin.y);
        double targetDistance = horizontalDistance(position, targetCenter);
        double rangePenalty = this.rangePressure(targetDistance, maxTargetRange);
        double corridorAdjustment = this.openCorridorAdjustment(action, targetCorridorClearance);
        return travelCost
                + targetDistance * TARGET_DISTANCE_WEIGHT
                + headingPenalty
                + altitude * ALTITUDE_WEIGHT
                + rangePenalty
                + corridorAdjustment;
    }

    private double targetCorridorClearance(Level level, Vec3 position, Vec3 targetCenter,
                                           Vec3 heading, double bottomOffset, double halfWidth) {
        Vec3 targetDirection = horizontalUnit(targetCenter.subtract(position), heading);
        Vec3 probe = position.add(targetDirection.scale(TARGET_CORRIDOR_LOOKAHEAD));
        double terrainTop = this.heightField.maxTerrainHeight(level, position, probe, halfWidth);
        return position.y + bottomOffset - terrainTop;
    }

    private static double rangePressure(double targetDistance, double maxTargetRange) {
        if (maxTargetRange <= 1.0D) {
            return 0.0D;
        }
        double ratio = targetDistance / maxTargetRange;
        if (ratio <= RANGE_PRESSURE_START) {
            return 0.0D;
        }
        double pressure = (ratio - RANGE_PRESSURE_START) / (1.0D - RANGE_PRESSURE_START);
        return pressure * pressure * RANGE_PRESSURE_WEIGHT;
    }

    private static double openCorridorAdjustment(int action, double targetCorridorClearance) {
        if (targetCorridorClearance < TARGET_CORRIDOR_CLEARANCE) {
            return 0.0D;
        }
        if (action == ACTION_FORWARD) {
            return -OPEN_CORRIDOR_FORWARD_BONUS;
        }
        if (action == ACTION_CLIMB) {
            return OPEN_CORRIDOR_CLIMB_COST;
        }
        return action == ACTION_LEFT || action == ACTION_RIGHT ? OPEN_CORRIDOR_SIDE_COST : 0.0D;
    }

    private boolean climbUseful(Level level, Vec3 currentPosition, Vec3 nextPosition,
                                Vec3 heading, double bottomOffset, double halfWidth) {
        Vec3 lookahead = nextPosition.add(heading.scale(CLIMB_LOOKAHEAD));
        double terrainTop = this.heightField.maxTerrainHeight(
                level, nextPosition, lookahead, halfWidth);
        return currentPosition.y + bottomOffset <= terrainTop + CLIMB_CLEARANCE;
    }

    private static boolean lineClear(Level level, Entity vehicle, Vec3 start, Vec3 end) {
        return level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                vehicle)).getType() == HitResult.Type.MISS;
    }

    private boolean candidateHasLineOfSight(Level level, Entity vehicle, Vec3 start, Vec3 targetCenter) {
        return this.heightField.mayHaveLineOfSight(level, start, targetCenter)
                && lineClear(level, vehicle, start, targetCenter);
    }

    boolean directDestinationClear(VehicleController controller, Vec3 targetCenter,
                                   Vec3 currentPosition, Vec3 destination) {
        Level level = controller.level();
        Entity vehicle = controller.entity();
        AABB box = controller.boundingBox();
        Vec3 centerOffset = box.getCenter().subtract(vehicle.position());
        double bottomOffset = box.minY - vehicle.position().y;
        double halfWidth = Math.max(box.getXsize(), box.getZsize()) * 0.5D;
        Vec3 sightStart = destination.add(centerOffset);
        return this.heightField.corridorClear(
                level, currentPosition, destination, bottomOffset, halfWidth)
                && this.heightField.mayHaveLineOfSight(level, sightStart, targetCenter)
                && lineClear(level, vehicle, sightStart, targetCenter);
    }

    @Nullable
    private Vec3 continueRoute(HelicopterFlightPlanner.AircraftState state) {
        if (this.waypoint != null && waypointPassErrorSqr(state)
                > WAYPOINT_STALE_MIN_DISTANCE * WAYPOINT_STALE_MIN_DISTANCE) {
            this.invalidateCurrentRoute(state);
        }
        if (this.waypoint != null && !this.reachedOrPassed(state)) {
            if (!this.waypointStale(state)) {
                return this.waypoint;
            }
            this.invalidateCurrentRoute(state);
        }
        if (this.waypoint != null) {
            if (this.backtracking) {
                this.backtracking = false;
            } else {
                this.remember(state.position());
            }
            this.waypoint = null;
            this.waypointStart = null;
            this.resetWaypointProgress();
        }
        return this.route.isEmpty() ? null : this.takeNextWaypoint(state);
    }

    private Vec3 takeNextWaypoint(HelicopterFlightPlanner.AircraftState state) {
        this.waypointStart = state.position();
        this.waypoint = this.route.removeFirst();
        this.resetWaypointProgress();
        return this.waypoint;
    }

    @Nullable
    private Vec3 beginBacktrack(HelicopterFlightPlanner.AircraftState state, double minimumY) {
        if (this.trail.size() <= 1) {
            return null;
        }
        this.trail.removeLast();
        this.waypointStart = state.position();
        Vec3 previous = this.trail.peekLast();
        this.waypoint = new Vec3(previous.x,
                Math.max(minimumY, Math.max(previous.y, state.position().y)), previous.z);
        this.backtracking = true;
        this.resetWaypointProgress();
        return this.waypoint;
    }

    private boolean reachedOrPassed(HelicopterFlightPlanner.AircraftState state) {
        Vec3 toWaypoint = this.waypoint.subtract(state.position());
        boolean pendingClimb = toWaypoint.y > WAYPOINT_VERTICAL_REACHED_DISTANCE;
        double passErrorSqr = pendingClimb ? -1.0D : this.waypointPassErrorSqr(state);
        return HelicopterFlightPlanner.reachedOrPassed(state, this.waypoint, pendingClimb)
                || passErrorSqr >= 0.0D
                && passErrorSqr <= WAYPOINT_STALE_MIN_DISTANCE * WAYPOINT_STALE_MIN_DISTANCE;
    }

    private boolean waypointStale(HelicopterFlightPlanner.AircraftState state) {
        Vec3 toWaypoint = this.waypoint.subtract(state.position());
        double distance = toWaypoint.y > WAYPOINT_VERTICAL_REACHED_DISTANCE
                ? toWaypoint.length()
                : Math.hypot(toWaypoint.x, toWaypoint.z);
        double progress = this.waypointSegmentProgress(state.position());
        boolean progressed = false;
        if (distance + WAYPOINT_PROGRESS_EPSILON < this.bestWaypointDistance) {
            this.bestWaypointDistance = distance;
            progressed = true;
        }
        if (progress >= this.bestWaypointProgress + WAYPOINT_PROGRESS_EPSILON) {
            this.bestWaypointProgress = progress;
            progressed = true;
        }
        if (progressed) {
            this.waypointTicks = 0;
            return false;
        }
        return ++this.waypointTicks >= WAYPOINT_STALE_TICKS;
    }

    private double waypointPassErrorSqr(HelicopterFlightPlanner.AircraftState state) {
        if (this.waypointStart == null || this.waypoint == null
                || this.waypoint.y - state.position().y > WAYPOINT_VERTICAL_REACHED_DISTANCE) {
            return -1.0D;
        }
        Vec3 segment = this.waypoint.subtract(this.waypointStart).multiply(1.0D, 0.0D, 1.0D);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-4D) {
            return -1.0D;
        }
        Vec3 fromStart = state.position().subtract(this.waypointStart).multiply(1.0D, 0.0D, 1.0D);
        double t = fromStart.dot(segment) / lengthSqr;
        return t < 1.0D ? -1.0D : fromStart.subtract(segment.scale(t)).lengthSqr();
    }

    private double waypointSegmentProgress(Vec3 position) {
        if (this.waypointStart == null || this.waypoint == null) {
            return Double.NEGATIVE_INFINITY;
        }
        Vec3 segment = this.waypoint.subtract(this.waypointStart);
        double length = segment.length();
        return length < 1.0E-4D ? 0.0D
                : position.subtract(this.waypointStart).dot(segment) / length;
    }

    private void resetWaypointProgress() {
        this.waypointTicks = 0;
        this.bestWaypointDistance = Double.POSITIVE_INFINITY;
        this.bestWaypointProgress = Double.NEGATIVE_INFINITY;
    }

    private void remember(Vec3 position) {
        if (this.trail.isEmpty() || this.trail.peekLast().distanceToSqr(position) > 1.0D) {
            this.trail.addLast(position);
            while (this.trail.size() > MAX_TRAIL_POINTS) {
                this.trail.removeFirst();
            }
        }
        this.visited.add(visitedKey(position));
    }

    private void updateFrontiers(Vec3 position) {
        double lateral = this.lateralCoordinate(position);
        this.maxLeftProgress = Math.max(this.maxLeftProgress, -lateral);
        this.maxRightProgress = Math.max(this.maxRightProgress, lateral);
        this.maxClimbProgress = Math.max(this.maxClimbProgress,
                Math.max(0.0D, position.y - this.episodeOrigin.y));
    }

    private int lane(Vec3 position) {
        double lateral = this.lateralCoordinate(position);
        if (lateral <= -LANE_CENTER_WIDTH) {
            return LANE_LEFT;
        }
        return lateral >= LANE_CENTER_WIDTH ? LANE_RIGHT : LANE_CENTER;
    }

    private double lateralProgress(HelicopterRecoveryCandidate candidate) {
        double lateral = this.lateralCoordinate(candidate.position());
        return candidate.lane() == LANE_LEFT
                ? -lateral
                : candidate.lane() == LANE_RIGHT ? lateral : 0.0D;
    }

    private double flyoverProgress(HelicopterRecoveryCandidate candidate) {
        double forward = Math.max(0.0D, this.forwardProgress(candidate.position()));
        double climb = Math.max(0.0D, candidate.position().y - this.episodeOrigin.y);
        return Math.min(forward, climb * 3.0D);
    }

    private double forwardProgress(Vec3 position) {
        return position.subtract(this.episodeOrigin).dot(this.episodeForward);
    }

    private double lateralCoordinate(Vec3 position) {
        Vec3 offset = position.subtract(this.episodeOrigin);
        Vec3 right = new Vec3(-this.episodeForward.z, 0.0D, this.episodeForward.x);
        return offset.dot(right);
    }

    private static Vec3 initialHeading(HelicopterFlightPlanner.AircraftState state) {
        Vec3 velocity = state.velocity().multiply(1.0D, 0.0D, 1.0D);
        return velocity.lengthSqr() > 0.0225D ? velocity.normalize() : yawForward(state.yaw());
    }

    private static Vec3 boundedInitialHeading(Vec3 targetHeading, Vec3 currentHeading) {
        double cross = targetHeading.x * currentHeading.z - targetHeading.z * currentHeading.x;
        double dot = Mth.clamp(targetHeading.dot(currentHeading), -1.0D, 1.0D);
        double offset = Math.toDegrees(Math.atan2(cross, dot));
        return Math.abs(offset) <= TURN_DEGREES
                ? currentHeading
                : rotate(targetHeading, Math.copySign(TURN_DEGREES, offset));
    }

    private static Vec3 projectedInertiaPoint(Vec3 position, Vec3 horizontalVelocity,
                                              double stoppingDistance) {
        double speed = horizontalVelocity.length();
        if (speed < 1.0E-4D || stoppingDistance <= 0.0D) {
            return position;
        }
        return position.add(horizontalVelocity.scale(stoppingDistance / speed));
    }

    private static boolean needsRangeBrake(VehicleController controller,
                                           HelicopterFlightPlanner.AircraftState state,
                                           Entity target, double followRange) {
        Vec3 horizontalVelocity = state.velocity().multiply(1.0D, 0.0D, 1.0D);
        double stoppingDistance = controller.estimatedHorizontalStoppingDistance();
        if (horizontalVelocity.lengthSqr() < 1.0E-8D
                || stoppingDistance <= MIN_FOLLOW_RANGE_MARGIN) {
            return false;
        }
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        Vec3 projected = state.position().add(horizontalVelocity.normalize().scale(stoppingDistance));
        return horizontalDistance(projected, targetCenter)
                + MIN_FOLLOW_RANGE_MARGIN > followRange;
    }

    private boolean firstLegClear(Level level, Vec3 position, Vec3 inertiaPoint,
                                  Vec3 nextPosition, Vec3 targetCenter,
                                  double bottomOffset, double halfWidth, double followRange) {
        if (horizontalDistance(inertiaPoint, targetCenter) > followRange) {
            return false;
        }
        if (horizontalDistance(position, inertiaPoint) < 0.25D) {
            return true;
        }
        return this.heightField.corridorClear(
                level, position, inertiaPoint, bottomOffset, halfWidth)
                && this.heightField.corridorClear(
                level, inertiaPoint, nextPosition, bottomOffset, halfWidth);
    }

    private static boolean withinRangeEnvelope(Vec3 position, Vec3 nextPosition,
                                               Vec3 targetCenter, double maxTargetRange,
                                               double followRange) {
        Vec3 movement = nextPosition.subtract(position).multiply(1.0D, 0.0D, 1.0D);
        Vec3 outward = nextPosition.subtract(targetCenter).multiply(1.0D, 0.0D, 1.0D);
        double outwardFraction = movement.lengthSqr() < 1.0E-6D || outward.lengthSqr() < 1.0E-6D
                ? 0.0D
                : Math.max(0.0D, movement.normalize().dot(outward.normalize()));
        double movementRange = followRange - Math.max(
                MIN_FOLLOW_RANGE_MARGIN,
                WAYPOINT_PASS_DISTANCE * outwardFraction);
        return horizontalDistance(nextPosition, targetCenter)
                <= Math.min(maxTargetRange, movementRange);
    }

    private static Vec3 rotate(Vec3 direction, double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return new Vec3(
                direction.x * cos - direction.z * sin,
                0.0D,
                direction.x * sin + direction.z * cos).normalize();
    }

    private static Vec3 yawForward(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
    }

    private static Vec3 horizontalUnit(Vec3 vector, Vec3 fallback) {
        double length = Math.hypot(vector.x, vector.z);
        return length < 1.0E-4D
                ? fallback
                : new Vec3(vector.x / length, 0.0D, vector.z / length);
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        return Math.hypot(first.x - second.x, first.z - second.z);
    }

    private static long visitedKey(Vec3 position) {
        return BlockPos.asLong(
                Mth.floor(position.x / VISITED_CELL_SIZE),
                Mth.floor(position.y / VISITED_CELL_SIZE),
                Mth.floor(position.z / VISITED_CELL_SIZE));
    }
}
