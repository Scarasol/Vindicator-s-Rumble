package com.scarasol.vindicatorsrumble.vehicleai.helicopter;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class HelicopterObstacleDecisionModel {
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
    private static final int MAX_SEARCHES = 64;
    private static final int SAFE_PREFIX_LENGTH = 2;
    private static final double SEGMENT_LENGTH = 11.0D;
    private static final double CLIMB_FORWARD_LENGTH = 5.0D;
    private static final double CLIMB_HEIGHT = 7.0D;
    private static final double CLIMB_LOOKAHEAD = 8.0D;
    private static final double CLIMB_CLEARANCE = 4.0D;
    private static final double TURN_DEGREES = 45.0D;
    private static final double VISITED_CELL_SIZE = 4.0D;
    private static final double MIN_FOLLOW_RANGE_MARGIN = 3.0D;
    private static final double FOLLOW_RANGE_BRAKE_TICKS = 12.0D;
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

    private final HeightField heightField = new HeightField();
    private final Set<Long> visited = new HashSet<>();
    private Vec3 episodeOrigin;
    private Vec3 episodeForward;
    private double maxLeftProgress;
    private double maxRightProgress;
    private double maxClimbProgress;

    private HelicopterObstacleDecisionModel() {
    }

    static SearchResult search(Scene scene, Vec3 position, Vec3 velocity) {
        HelicopterObstacleDecisionModel model = new HelicopterObstacleDecisionModel();
        model.beginEpisode(scene, position);
        return model.runSearch(scene, position, velocity);
    }

    static SimulationResult simulate(Scene scene, FlightBudget budget) {
        HelicopterObstacleDecisionModel model = new HelicopterObstacleDecisionModel();
        Vec3 position = scene.aircraft();
        Vec3 velocity = Vec3.ZERO;
        double ticks = 0.0D;
        int searches = 0;
        int expanded = 0;
        List<Step> steps = new ArrayList<>();
        GeometryProgress progress = GeometryProgress.initial(scene, position);
        model.beginEpisode(scene, position);

        while (searches < MAX_SEARCHES) {
            if (horizontalDistance(position, scene.target()) > scene.followRange()) {
                return new SimulationResult(false, true, ticks, searches, expanded, steps, progress);
            }
            if (lineClear(scene, position.add(scene.centerOffset()), scene.target())) {
                return new SimulationResult(true, false, ticks, searches, expanded, steps, progress);
            }
            SearchResult search = model.runSearch(scene, position, velocity);
            searches++;
            expanded += search.expandedCandidates();
            if (search.path().isEmpty()) {
                return new SimulationResult(false, false, ticks, searches, expanded, steps, progress);
            }
            for (Vec3 next : search.path()) {
                Step step = classifyStep(position, next);
                double segmentTicks = travelTicks(position, next, step, budget);
                SegmentOutcome segment = followSegment(scene, position, next, ticks, segmentTicks, progress);
                if (segment.recovered()) {
                    steps.add(step);
                    return new SimulationResult(true, false, segment.tick(), searches, expanded, steps, segment.progress());
                }
                if (segment.rangeLost()) {
                    steps.add(step);
                    return new SimulationResult(false, true, segment.tick(), searches, expanded, steps, segment.progress());
                }
                ticks += segmentTicks;
                velocity = next.subtract(position).scale(1.0D / Math.max(1.0D, segmentTicks));
                position = next;
                model.visited.add(visitedKey(position));
                model.updateFrontiers(position);
                steps.add(step);
                progress = segment.progress();
            }
        }
        return new SimulationResult(false, false, ticks, searches, expanded, steps, progress);
    }

    private SearchResult runSearch(Scene scene, Vec3 position, Vec3 velocity) {
        this.updateFrontiers(position);
        double horizontalSpeed = Math.hypot(velocity.x, velocity.z);
        double rangeMargin = Math.max(MIN_FOLLOW_RANGE_MARGIN, horizontalSpeed * FOLLOW_RANGE_BRAKE_TICKS);
        double maxTargetRange = Math.max(0.0D, scene.followRange() - rangeMargin);
        double currentDeficit = this.heightDeficit(scene, position);
        double currentBlockedLength = this.blockedLength(scene, position);
        Vec3 rootHeading = horizontalUnit(scene.target().subtract(position), yawForward(scene.initialYaw()));
        List<Candidate> beam = List.of(new Candidate(position, rootHeading, List.of(), List.of(),
                LANE_CENTER, false, 0.0D, 0.0D, currentDeficit, currentBlockedLength));
        Candidate bestPartial = null;
        int expandedCandidates = 0;

        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            List<Candidate> expanded = new ArrayList<>(beam.size() * ACTION_COUNT);
            for (Candidate candidate : beam) {
                for (int action = 0; action < ACTION_COUNT; action++) {
                    Candidate next = this.advance(scene, candidate, action, maxTargetRange);
                    expandedCandidates++;
                    if (next != null) {
                        expanded.add(next);
                    }
                }
            }
            if (expanded.isEmpty()) {
                break;
            }
            expanded.sort(Comparator.comparingDouble(Candidate::score));
            for (Candidate candidate : expanded) {
                if (lineClear(scene, candidate.position().add(scene.centerOffset()), scene.target())) {
                    return new SearchResult(candidate.path(), candidate.steps(), true, false, expandedCandidates);
                }
            }
            beam = this.retainDiversity(expanded);
            bestPartial = this.selectPartialCandidate(beam, scene, maxTargetRange,
                    currentDeficit, currentBlockedLength);
        }

        if (bestPartial == null || bestPartial.path().isEmpty()) {
            return new SearchResult(List.of(), List.of(), false, false, expandedCandidates);
        }
        int prefixLength = Math.min(bestPartial.climbed() ? 1 : SAFE_PREFIX_LENGTH, bestPartial.path().size());
        return new SearchResult(List.copyOf(bestPartial.path().subList(0, prefixLength)),
                List.copyOf(bestPartial.steps().subList(0, prefixLength)),
                false, true, expandedCandidates);
    }

    private void beginEpisode(Scene scene, Vec3 position) {
        this.episodeOrigin = position;
        this.episodeForward = horizontalUnit(scene.target().subtract(position), yawForward(scene.initialYaw()));
        this.visited.add(visitedKey(position));
    }

    private Candidate advance(Scene scene, Candidate candidate, int action, double maxTargetRange) {
        Vec3 nextHeading = candidate.heading();
        Vec3 movement;
        double actionCost;
        boolean climbed = candidate.climbed();
        Step step;
        if (action == ACTION_LEFT) {
            nextHeading = rotate(nextHeading, -TURN_DEGREES);
            movement = nextHeading.scale(SEGMENT_LENGTH);
            actionCost = TURN_TRAVEL_COST;
            step = Step.LEFT;
        } else if (action == ACTION_RIGHT) {
            nextHeading = rotate(nextHeading, TURN_DEGREES);
            movement = nextHeading.scale(SEGMENT_LENGTH);
            actionCost = TURN_TRAVEL_COST;
            step = Step.RIGHT;
        } else if (action == ACTION_CLIMB) {
            movement = nextHeading.scale(CLIMB_FORWARD_LENGTH).add(0.0D, CLIMB_HEIGHT, 0.0D);
            actionCost = CLIMB_TRAVEL_COST;
            climbed = true;
            step = Step.CLIMB;
        } else {
            movement = nextHeading.scale(SEGMENT_LENGTH);
            actionCost = FORWARD_TRAVEL_COST;
            step = Step.FORWARD;
        }

        Vec3 nextPosition = candidate.position().add(movement);
        int nextLane = this.lane(nextPosition);
        if (candidate.lane() != LANE_CENTER && nextLane == -candidate.lane()
                || horizontalDistance(nextPosition, scene.target()) > maxTargetRange
                || this.visited.contains(visitedKey(nextPosition))
                || action == ACTION_CLIMB && !this.climbUseful(scene, candidate.position(), nextPosition, nextHeading)
                || !this.heightField.corridorClear(scene, candidate.position(), nextPosition)) {
            return null;
        }

        List<Vec3> path = new ArrayList<>(candidate.path().size() + 1);
        path.addAll(candidate.path());
        path.add(nextPosition);
        List<Step> steps = new ArrayList<>(candidate.steps().size() + 1);
        steps.addAll(candidate.steps());
        steps.add(step);
        double travelCost = candidate.travelCost() + actionCost;
        double targetCorridorClearance = this.targetCorridorClearance(scene, nextPosition, nextHeading);
        double score = this.score(scene, nextPosition, nextHeading, travelCost, action,
                targetCorridorClearance, maxTargetRange);
        double corridorDeficit = this.heightDeficit(scene, nextPosition);
        double blockedLength = this.blockedLength(scene, nextPosition);
        return new Candidate(nextPosition, nextHeading, List.copyOf(path), List.copyOf(steps),
                nextLane, climbed, travelCost, score, corridorDeficit, blockedLength);
    }

    private List<Candidate> retainDiversity(List<Candidate> candidates) {
        Set<Candidate> retained = new LinkedHashSet<>();
        int[] lanes = {LANE_CENTER, LANE_LEFT, LANE_RIGHT};
        for (int lane : lanes) {
            candidates.stream().filter(candidate -> candidate.lane() == lane)
                    .findFirst().ifPresent(retained::add);
        }
        retainLateralExtreme(retained, candidates, LANE_LEFT);
        retainLateralExtreme(retained, candidates, LANE_RIGHT);
        candidates.stream().filter(Candidate::climbed)
                .max(Comparator.comparingDouble(this::flyoverProgress))
                .ifPresent(retained::add);
        for (Candidate candidate : candidates) {
            if (retained.size() >= BEAM_WIDTH) {
                break;
            }
            retained.add(candidate);
        }
        List<Candidate> result = new ArrayList<>(retained);
        result.sort(Comparator.comparingDouble(Candidate::score));
        return result.size() <= BEAM_WIDTH ? List.copyOf(result) : List.copyOf(result.subList(0, BEAM_WIDTH));
    }

    private void retainLateralExtreme(Set<Candidate> retained, List<Candidate> candidates, int lane) {
        candidates.stream().filter(candidate -> candidate.lane() == lane)
                .max(Comparator.comparingDouble(this::lateralProgress))
                .ifPresent(retained::add);
    }

    private Candidate selectPartialCandidate(List<Candidate> candidates, Scene scene, double maxTargetRange,
                                             double currentDeficit, double currentBlockedLength) {
        return candidates.stream()
                .filter(candidate -> this.expandsFrontier(candidate, currentDeficit, currentBlockedLength))
                .min(Comparator.comparingDouble(candidate -> this.partialCandidateScore(
                        candidate, scene, maxTargetRange, currentDeficit, currentBlockedLength)))
                .orElse(null);
    }

    private boolean expandsFrontier(Candidate candidate, double currentDeficit, double currentBlockedLength) {
        double lateral = this.lateralCoordinate(candidate.position());
        boolean expandsSide = candidate.lane() == LANE_LEFT
                && -lateral >= this.maxLeftProgress + MIN_FRONTIER_PROGRESS
                || candidate.lane() == LANE_RIGHT
                && lateral >= this.maxRightProgress + MIN_FRONTIER_PROGRESS;
        double flyoverGain = currentDeficit - candidate.corridorDeficit();
        double blockedProgress = currentBlockedLength - candidate.blockedLength();
        double forward = Math.max(0.0D, this.forwardProgress(candidate.position()));
        boolean highEnough = candidate.corridorDeficit() <= CLEAR_CORRIDOR_DEFICIT;
        boolean corridorOpen = highEnough && candidate.blockedLength() <= CLEAR_BLOCKED_LENGTH;
        boolean expandsFlyover = currentDeficit > CLEAR_CORRIDOR_DEFICIT
                && flyoverGain >= MIN_DEFICIT_PROGRESS
                || blockedProgress >= MIN_BLOCKED_PROGRESS
                || corridorOpen && forward >= MIN_FRONTIER_PROGRESS;
        return expandsFlyover || expandsSide;
    }

    private double partialCandidateScore(Candidate candidate, Scene scene, double maxTargetRange,
                                         double currentDeficit, double currentBlockedLength) {
        double sideProgress = this.lateralProgress(candidate);
        double forwardProgress = Math.max(0.0D, this.forwardProgress(candidate.position()));
        double flyoverProgress = this.flyoverProgress(candidate);
        double flyoverGain = Math.max(0.0D, currentDeficit - candidate.corridorDeficit());
        double blockedProgress = Math.max(0.0D, currentBlockedLength - candidate.blockedLength());
        double climbProgress = Math.max(0.0D, candidate.position().y - this.episodeOrigin.y - this.maxClimbProgress);
        double clearForward = candidate.corridorDeficit() <= CLEAR_CORRIDOR_DEFICIT
                && candidate.blockedLength() <= CLEAR_BLOCKED_LENGTH ? forwardProgress : 0.0D;
        double targetDistance = horizontalDistance(candidate.position(), scene.target());
        double rangeRatio = maxTargetRange <= 1.0D ? 0.0D : targetDistance / maxTargetRange;
        double pressure = clamp((rangeRatio - RANGE_PRESSURE_START) / (1.0D - RANGE_PRESSURE_START), 0.0D, 1.0D);
        double flyoverSupport = Math.max(flyoverProgress,
                flyoverGain * 1.5D + blockedProgress + Math.min(clearForward, 16.0D) * 0.5D);
        double nonSideSupport = Math.max(flyoverSupport,
                Math.max(climbProgress, Math.min(forwardProgress, 12.0D) * 0.35D));
        double weakSideFactor = flyoverGain > 0.0D || clearForward > 0.0D ? 1.0D : 0.35D;
        double sideReward = PARTIAL_SIDE_PROGRESS_WEIGHT * sideProgress
                * weakSideFactor * (1.0D - PARTIAL_SIDE_RANGE_REWARD_FADE * pressure);
        double lowSideProgress = Math.max(0.0D, sideProgress - nonSideSupport - PARTIAL_SIDE_FREE_PROGRESS);
        return candidate.score()
                - sideReward
                - PARTIAL_FLYOVER_PROGRESS_WEIGHT * flyoverProgress
                - PARTIAL_FLYOVER_GAIN_WEIGHT * flyoverGain
                - PARTIAL_CLEAR_FORWARD_WEIGHT * clearForward
                - PARTIAL_BLOCKED_PROGRESS_WEIGHT * blockedProgress
                + PARTIAL_LOW_SIDE_RANGE_PENALTY * pressure * lowSideProgress;
    }

    private double score(Scene scene, Vec3 position, Vec3 heading, double travelCost,
                         int action, double targetCorridorClearance, double maxTargetRange) {
        Vec3 targetDirection = horizontalUnit(scene.target().subtract(position), heading);
        double headingPenalty = (1.0D - clamp(heading.dot(targetDirection), -1.0D, 1.0D)) * HEADING_WEIGHT;
        double altitude = Math.max(0.0D, position.y - this.episodeOrigin.y);
        double targetDistance = horizontalDistance(position, scene.target());
        return travelCost
                + targetDistance * TARGET_DISTANCE_WEIGHT
                + headingPenalty
                + altitude * ALTITUDE_WEIGHT
                + rangePressure(targetDistance, maxTargetRange)
                + openCorridorAdjustment(action, targetCorridorClearance);
    }

    private double targetCorridorClearance(Scene scene, Vec3 position, Vec3 heading) {
        Vec3 targetDirection = horizontalUnit(scene.target().subtract(position), heading);
        Vec3 probe = position.add(targetDirection.scale(TARGET_CORRIDOR_LOOKAHEAD));
        return position.y + scene.bottomOffset() - this.heightField.maxTerrainHeight(scene, position, probe);
    }

    private double heightDeficit(Scene scene, Vec3 position) {
        double terrainTop = this.heightField.maxTerrainHeight(scene, position, scene.target());
        return Math.max(0.0D, terrainTop + TARGET_CORRIDOR_CLEARANCE - position.y - scene.bottomOffset());
    }

    private double blockedLength(Scene scene, Vec3 position) {
        return this.heightField.blockedLength(scene, position, scene.target());
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

    private boolean climbUseful(Scene scene, Vec3 currentPosition, Vec3 nextPosition, Vec3 heading) {
        Vec3 lookahead = nextPosition.add(heading.scale(CLIMB_LOOKAHEAD));
        double terrainTop = this.heightField.maxTerrainHeight(scene, nextPosition, lookahead);
        return currentPosition.y + scene.bottomOffset() <= terrainTop + CLIMB_CLEARANCE;
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

    private double lateralProgress(Candidate candidate) {
        double lateral = this.lateralCoordinate(candidate.position());
        return candidate.lane() == LANE_LEFT
                ? -lateral
                : candidate.lane() == LANE_RIGHT ? lateral : 0.0D;
    }

    private double flyoverProgress(Candidate candidate) {
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

    private static boolean lineClear(Scene scene, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double horizontalLength = Math.hypot(delta.x, delta.z);
        int steps = Math.max(1, (int) Math.ceil(horizontalLength));
        for (int step = 1; step <= steps; step++) {
            double progress = step / (double) steps;
            Vec3 point = start.add(delta.scale(progress));
            if (scene.blocksLineAt(point)) {
                return false;
            }
        }
        return true;
    }

    private static SegmentOutcome followSegment(Scene scene, Vec3 start, Vec3 end, double startTick,
                                                double segmentTicks, GeometryProgress progress) {
        Vec3 delta = end.subtract(start);
        double distance = delta.length();
        int steps = Math.max(1, (int) Math.ceil(Math.max(distance, segmentTicks)));
        GeometryProgress currentProgress = progress;
        for (int step = 1; step <= steps; step++) {
            double ratio = step / (double) steps;
            Vec3 position = start.add(delta.scale(ratio));
            currentProgress = currentProgress.observe(scene, position);
            if (horizontalDistance(position, scene.target()) > scene.followRange()) {
                return new SegmentOutcome(false, true, startTick + segmentTicks * ratio, currentProgress);
            }
            if (lineClear(scene, position.add(scene.centerOffset()), scene.target())) {
                return new SegmentOutcome(true, false, startTick + segmentTicks * ratio, currentProgress);
            }
        }
        return new SegmentOutcome(false, false, startTick + segmentTicks, currentProgress);
    }

    private static Step classifyStep(Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        if (delta.y > 2.0D) {
            return Step.CLIMB;
        }
        return delta.z < -1.0D ? Step.LEFT : delta.z > 1.0D ? Step.RIGHT : Step.FORWARD;
    }

    private static double travelTicks(Vec3 start, Vec3 end, Step step, FlightBudget budget) {
        Vec3 delta = end.subtract(start);
        double horizontal = Math.hypot(delta.x, delta.z);
        double horizontalSpeed = step == Step.LEFT || step == Step.RIGHT ? budget.sideSpeed() : budget.forwardSpeed();
        double horizontalTicks = horizontal / horizontalSpeed;
        double verticalTicks = Math.abs(delta.y) / budget.climbSpeed();
        double turnTicks = step == Step.LEFT || step == Step.RIGHT ? budget.turnTicks() : 0.0D;
        return Math.max(horizontalTicks, verticalTicks) + turnTicks;
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
        return length < 1.0E-4D ? fallback : new Vec3(vector.x / length, 0.0D, vector.z / length);
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        return Math.hypot(first.x - second.x, first.z - second.z);
    }

    private static long visitedKey(Vec3 position) {
        return (((long) Math.floor(position.x / VISITED_CELL_SIZE) & 0x3FFFFFFL) << 38)
                | (((long) Math.floor(position.y / VISITED_CELL_SIZE) & 0xFFFL) << 26)
                | ((long) Math.floor(position.z / VISITED_CELL_SIZE) & 0x3FFFFFFL);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    enum Step {
        FORWARD,
        LEFT,
        RIGHT,
        CLIMB
    }

    record FlightBudget(double forwardSpeed, double sideSpeed, double climbSpeed, double turnTicks) {
        static FlightBudget conservativeAh6() {
            return new FlightBudget(0.55D, 0.38D, 0.28D, 8.0D);
        }

        static FlightBudget conservativeMi28() {
            return new FlightBudget(0.41D, 0.30D, 0.28D, 9.5D);
        }
    }

    record Scene(Vec3 aircraft, Vec3 target, Obstacle obstacle, double followRange,
                 float initialYaw, Vec3 centerOffset, double bottomOffset, double halfWidth) {
        double heightAt(Vec3 point) {
            return this.obstacle.containsColumn(point.x, point.z) ? this.obstacle.top() : 0.0D;
        }

        boolean blocksLineAt(Vec3 point) {
            return this.obstacle.blocks(point);
        }
    }

    record Obstacle(double near, double far, double left, double right, double top, boolean solid) {
        boolean containsColumn(double x, double z) {
            return x >= this.near && x <= this.far && z >= this.left && z <= this.right;
        }

        boolean blocks(Vec3 point) {
            if (!this.containsColumn(point.x, point.z) || point.y < 0.0D || point.y > this.top) {
                return false;
            }
            if (this.solid) {
                return true;
            }
            double wall = 1.0D;
            boolean sideWall = point.x <= this.near + wall
                    || point.x >= this.far - wall
                    || point.z <= this.left + wall
                    || point.z >= this.right - wall;
            boolean roof = point.y >= this.top - 1.0D;
            return sideWall || roof;
        }
    }

    record SearchResult(List<Vec3> path, List<Step> steps, boolean complete,
                        boolean partial, int expandedCandidates) {
    }

    record SimulationResult(boolean recovered, boolean rangeLost, double ticks, int searches, int expandedCandidates,
                            List<Step> steps, GeometryProgress progress) {
        long count(Step step) {
            return this.steps.stream().filter(value -> value == step).count();
        }
    }

    private record SegmentOutcome(boolean recovered, boolean rangeLost, double tick, GeometryProgress progress) {
    }

    record GeometryProgress(double maxForward, double minLateral, double maxLateral,
                            double maxSideClearance, double maxFarClearance, double maxTopClearance) {
        static GeometryProgress initial(Scene scene, Vec3 position) {
            return new GeometryProgress(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY).observe(scene, position);
        }

        GeometryProgress observe(Scene scene, Vec3 position) {
            double forward = position.x;
            double lateral = position.z;
            double side = Math.max(scene.obstacle.left() - lateral, lateral - scene.obstacle.right());
            double far = forward - scene.obstacle.far();
            double top = position.y - scene.obstacle.top();
            return new GeometryProgress(
                    Math.max(this.maxForward, forward),
                    Math.min(this.minLateral, lateral),
                    Math.max(this.maxLateral, lateral),
                    Math.max(this.maxSideClearance, side),
                    Math.max(this.maxFarClearance, far),
                    Math.max(this.maxTopClearance, top));
        }
    }

    private record Candidate(Vec3 position, Vec3 heading, List<Vec3> path, List<Step> steps,
                             int lane, boolean climbed, double travelCost, double score,
                             double corridorDeficit, double blockedLength) {
    }

    private static final class HeightField {
        private static final double SAMPLE_SPACING = 2.0D;
        private static final double BOTTOM_CLEARANCE = 0.35D;

        boolean corridorClear(Scene scene, Vec3 start, Vec3 end) {
            Vec3 delta = end.subtract(start);
            double horizontalLength = Math.hypot(delta.x, delta.z);
            int steps = Math.max(1, (int) Math.ceil(horizontalLength / SAMPLE_SPACING));
            Vec3 side = horizontalLength < 1.0E-4D
                    ? Vec3.ZERO
                    : new Vec3(-delta.z / horizontalLength, 0.0D, delta.x / horizontalLength);
            for (int step = 1; step <= steps; step++) {
                double progress = step / (double) steps;
                Vec3 point = start.add(delta.scale(progress));
                double bottomY = point.y + scene.bottomOffset();
                if (!this.columnClear(scene, point, bottomY)
                        || scene.halfWidth() > 0.1D
                        && (!this.columnClear(scene, point.add(side.scale(scene.halfWidth())), bottomY)
                        || !this.columnClear(scene, point.add(side.scale(-scene.halfWidth())), bottomY))) {
                    return false;
                }
            }
            return true;
        }

        double maxTerrainHeight(Scene scene, Vec3 start, Vec3 end) {
            Vec3 delta = end.subtract(start);
            double horizontalLength = Math.hypot(delta.x, delta.z);
            int steps = Math.max(1, (int) Math.ceil(horizontalLength / SAMPLE_SPACING));
            Vec3 side = horizontalLength < 1.0E-4D
                    ? Vec3.ZERO
                    : new Vec3(-delta.z / horizontalLength, 0.0D, delta.x / horizontalLength);
            double maxHeight = 0.0D;
            for (int step = 1; step <= steps; step++) {
                Vec3 point = start.add(delta.scale(step / (double) steps));
                maxHeight = Math.max(maxHeight, scene.heightAt(point));
                if (scene.halfWidth() > 0.1D) {
                    maxHeight = Math.max(maxHeight, scene.heightAt(point.add(side.scale(scene.halfWidth()))));
                    maxHeight = Math.max(maxHeight, scene.heightAt(point.add(side.scale(-scene.halfWidth()))));
                }
            }
            return maxHeight;
        }

        double blockedLength(Scene scene, Vec3 start, Vec3 end) {
            Vec3 delta = end.subtract(start);
            double horizontalLength = Math.hypot(delta.x, delta.z);
            int steps = Math.max(1, (int) Math.ceil(horizontalLength / SAMPLE_SPACING));
            Vec3 side = horizontalLength < 1.0E-4D
                    ? Vec3.ZERO
                    : new Vec3(-delta.z / horizontalLength, 0.0D, delta.x / horizontalLength);
            double bottomY = start.y + scene.bottomOffset();
            double blocked = 0.0D;
            for (int step = 1; step <= steps; step++) {
                Vec3 point = start.add(delta.scale(step / (double) steps));
                if (!this.columnClear(scene, point, bottomY)
                        || scene.halfWidth() > 0.1D
                        && (!this.columnClear(scene, point.add(side.scale(scene.halfWidth())), bottomY)
                        || !this.columnClear(scene, point.add(side.scale(-scene.halfWidth())), bottomY))) {
                    blocked += horizontalLength / steps;
                }
            }
            return blocked;
        }

        private boolean columnClear(Scene scene, Vec3 point, double bottomY) {
            return bottomY >= scene.heightAt(point) + BOTTOM_CLEARANCE;
        }
    }
}
