package com.scarasol.vindicatorsrumble.vehicleai.helicopter;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelicopterObstacleDecisionModelTest {
    private static final int SCENARIO_COUNT = 1_024;
    private static final int PAIRED_SCENE_COUNT = SCENARIO_COUNT / 2;
    private static final long SEED = 0x51DEB0A7L;
    private static final long SCENE_SEED_STEP = 0x9E3779B97F4A7C15L;
    private static final double FOLLOW_RANGE = 64.0D;
    private static final int PRIMARY_HORIZON = 180;
    private static final int[] REPORT_HORIZONS = {100, 140, 180, 200, 220, 240, 300};
    private static final Vec3 CENTER_OFFSET = new Vec3(0.0D, 1.0D, 0.0D);
    private static final double BOTTOM_OFFSET = 0.0D;
    private static final double HALF_WIDTH = 2.0D;
    private static final Direction[] DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    private static final HelicopterObstacleDecisionModel.FlightBudget AH6_BUDGET =
            HelicopterObstacleDecisionModel.FlightBudget.conservativeAh6();
    private static final HelicopterObstacleDecisionModel.FlightBudget MI28_BUDGET =
            HelicopterObstacleDecisionModel.FlightBudget.conservativeMi28();
    private static final String AH6 = "superbwarfare:ah_6";
    private static final String MI28 = "superbwarfare:mi_28";

    @Test
    void productionShadowModelRunsTheFullGeometryCorpus() throws IOException {
        List<Outcome> outcomes = runCorpus();
        long recovered = outcomes.stream().filter(outcome -> outcome.result.recovered()).count();
        long blockedFixtures = outcomes.stream().filter(Outcome::initiallyBlocked).count();

        writeReport(outcomes);

        assertEquals(SCENARIO_COUNT, outcomes.size());
        assertEquals(SCENARIO_COUNT, blockedFixtures);
        assertEquals(PAIRED_SCENE_COUNT,
                outcomes.stream().map(outcome -> outcome.scenario.index).distinct().count());
        assertEquals(PAIRED_SCENE_COUNT, outcomes.stream().filter(outcome -> AH6.equals(outcome.helicopter)).count());
        assertEquals(PAIRED_SCENE_COUNT, outcomes.stream().filter(outcome -> MI28.equals(outcome.helicopter)).count());
        assertTrue(recovered > 0, "shadow model should recover at least part of the generated corpus");
    }

    @Test
    void productionShadowSearchUsesBoundedCandidateExpansion() {
        for (int index = 0; index < PAIRED_SCENE_COUNT; index++) {
            Scenario scenario = Scenario.generate(index, sceneRandom(index));
            HelicopterObstacleDecisionModel.SearchResult result =
                    HelicopterObstacleDecisionModel.search(scenario.scene(), scenario.scene().aircraft(), Vec3.ZERO);

            assertTrue(result.expandedCandidates() <= 144,
                    "search expanded too many candidates: " + describe(scenario, result.expandedCandidates()));
        }
    }

    private static List<Outcome> runCorpus() {
        List<Outcome> outcomes = new ArrayList<>(SCENARIO_COUNT);
        for (int index = 0; index < PAIRED_SCENE_COUNT; index++) {
            Scenario scenario = Scenario.generate(index, sceneRandom(index));
            outcomes.add(new Outcome(AH6, scenario,
                    HelicopterObstacleDecisionModel.simulate(scenario.scene(), AH6_BUDGET)));
            outcomes.add(new Outcome(MI28, scenario,
                    HelicopterObstacleDecisionModel.simulate(scenario.scene(), MI28_BUDGET)));
        }
        return outcomes;
    }

    private static Random sceneRandom(int index) {
        return new Random(SEED ^ (SCENE_SEED_STEP * (index + 1L)));
    }

    private static void writeReport(List<Outcome> outcomes) throws IOException {
        Path path = Path.of("build", "reports", "vehicle-ai", "helicopter-shadow-model.txt");
        Files.createDirectories(path.getParent());
        Files.writeString(path, report(outcomes), StandardCharsets.UTF_8);
    }

    private static String report(List<Outcome> outcomes) {
        long recovered = outcomes.stream().filter(outcome -> outcome.result.recovered()).count();
        long rangeLost = outcomes.stream().filter(outcome -> outcome.result.rangeLost()).count();
        double recoveryRate = recovered / (double) Math.max(1, outcomes.size());
        double averageTicks = outcomes.stream().filter(outcome -> outcome.result.recovered())
                .mapToDouble(outcome -> outcome.result.ticks()).average().orElse(0.0D);
        double p50Ticks = percentile(outcomes.stream().filter(outcome -> outcome.result.recovered())
                .mapToDouble(outcome -> outcome.result.ticks()).sorted().toArray(), 0.50D);
        double p95Ticks = percentile(outcomes.stream().filter(outcome -> outcome.result.recovered())
                .mapToDouble(outcome -> outcome.result.ticks()).sorted().toArray(), 0.95D);
        StringBuilder builder = new StringBuilder(64_000);
        builder.append("helicopter-production-shadow-obstacle-model\n")
                .append("source=src/main HelicopterVisibilityRecovery search copied into src/test shadow model\n")
                .append("physics=idealized-waypoint-travel continuous-segment-los not-game-physics")
                .append(" followRange=").append(String.format(Locale.ROOT, "%.1f", FOLLOW_RANGE))
                .append(" primaryHorizon=").append(PRIMARY_HORIZON)
                .append(" pairedScenes=").append(PAIRED_SCENE_COUNT)
                .append(" helicopters=[").append(AH6).append(", ").append(MI28).append("]\n")
                .append(String.format(Locale.ROOT,
                        "scenarios=%d recovered=%d rate=%.3f rangeLost=%d avgTicks=%.1f recoveryP50=%.1f recoveryP95=%.1f%n",
                        outcomes.size(), recovered, recoveryRate, rangeLost, averageTicks, p50Ticks, p95Ticks));
        builder.append(horizonLine("unbounded", outcomes, Double.POSITIVE_INFINITY));
        for (int horizon : REPORT_HORIZONS) {
            builder.append(horizonLine(Integer.toString(horizon), outcomes, horizon));
        }

        for (String helicopter : List.of(AH6, MI28)) {
            List<Outcome> byHelicopter = outcomes.stream()
                    .filter(outcome -> helicopter.equals(outcome.helicopter)).toList();
            builder.append(String.format(Locale.ROOT,
                    "helicopter=%s recovered=%d/%d h180=%d h200=%d h240=%d rangeLost=%d%n",
                    helicopter,
                    recoveredBy(byHelicopter, Double.POSITIVE_INFINITY), byHelicopter.size(),
                    recoveredBy(byHelicopter, 180), recoveredBy(byHelicopter, 200),
                    recoveredBy(byHelicopter, 240),
                    byHelicopter.stream().filter(outcome -> outcome.result.rangeLost()).count()));
        }

        for (Kind kind : Kind.values()) {
            List<Outcome> byKind = outcomes.stream().filter(outcome -> outcome.scenario.kind == kind).toList();
            long kindRecovered = byKind.stream().filter(outcome -> outcome.result.recovered()).count();
            long kindRangeLost = byKind.stream().filter(outcome -> outcome.result.rangeLost()).count();
            double kindTicks = byKind.stream().mapToDouble(outcome -> outcome.result.ticks()).average().orElse(0.0D);
            builder.append(String.format(Locale.ROOT,
                    "kind=%s recovered=%d/%d rate=%.3f rangeLost=%d h100=%d h140=%d h180=%d h200=%d h220=%d h240=%d h300=%d avgTicks=%.1f motions=%s%n",
                    kind, kindRecovered, byKind.size(), kindRecovered / (double) Math.max(1, byKind.size()),
                    kindRangeLost,
                    recoveredBy(byKind, 100), recoveredBy(byKind, 140), recoveredBy(byKind, 180),
                    recoveredBy(byKind, 200), recoveredBy(byKind, 220), recoveredBy(byKind, 240),
                    recoveredBy(byKind, 300), kindTicks, motionCounts(byKind)));
        }

        List<Outcome> lateAtPrimaryHorizon = outcomes.stream()
                .filter(outcome -> !outcome.recoveredBy(PRIMARY_HORIZON))
                .sorted(Comparator.comparingDouble((Outcome outcome) -> outcome.result.ticks()).reversed())
                .limit(40)
                .toList();
        builder.append("representativeMissesAt").append(PRIMARY_HORIZON).append('=')
                .append(lateAtPrimaryHorizon.size()).append('\n');
        for (Outcome failure : lateAtPrimaryHorizon) {
            builder.append(describe(failure)).append('\n');
        }

        List<Outcome> failures = outcomes.stream()
                .filter(outcome -> !outcome.recoveredBy(300.0D))
                .sorted(Comparator.comparingDouble((Outcome outcome) -> outcome.result.progress().maxFarClearance()).reversed())
                .limit(24)
                .toList();
        builder.append("representativeMissesAt300=").append(failures.size()).append('\n');
        for (Outcome failure : failures) {
            builder.append(describe(failure)).append('\n');
        }
        return builder.toString();
    }

    private static String horizonLine(String label, List<Outcome> outcomes, double horizon) {
        long recovered = recoveredBy(outcomes, horizon);
        return String.format(Locale.ROOT,
                "horizon=%s recovered=%d/%d rate=%.3f%n",
                label, recovered, outcomes.size(), recovered / (double) Math.max(1, outcomes.size()));
    }

    private static long recoveredBy(List<Outcome> outcomes, double horizon) {
        return outcomes.stream().filter(outcome -> outcome.recoveredBy(horizon)).count();
    }

    private static String motionCounts(List<Outcome> outcomes) {
        Map<HelicopterObstacleDecisionModel.Step, Long> counts =
                new EnumMap<>(HelicopterObstacleDecisionModel.Step.class);
        for (HelicopterObstacleDecisionModel.Step step : HelicopterObstacleDecisionModel.Step.values()) {
            counts.put(step, 0L);
        }
        for (Outcome outcome : outcomes) {
            for (HelicopterObstacleDecisionModel.Step step : HelicopterObstacleDecisionModel.Step.values()) {
                counts.put(step, counts.get(step) + outcome.result.count(step));
            }
        }
        return counts.toString();
    }

    private static double percentile(double[] values, double percentile) {
        if (values.length == 0) {
            return Double.NaN;
        }
        int index = Math.min(values.length - 1, (int) Math.ceil(values.length * percentile) - 1);
        return values[index];
    }

    private static boolean lineClear(HelicopterObstacleDecisionModel.Scene scene, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(delta.x, delta.z)));
        for (int step = 1; step <= steps; step++) {
            Vec3 point = start.add(delta.scale(step / (double) steps));
            if (scene.blocksLineAt(point)) {
                return false;
            }
        }
        return true;
    }

    private static String describe(Outcome outcome) {
        HelicopterObstacleDecisionModel.GeometryProgress progress = outcome.result.progress();
        return String.format(Locale.ROOT,
                "helicopter=%s scene=%d dir=%d kind=%s targetY=%.0f size=%.0fx%.0fx%.0f recovered=%s rangeLost=%s ticks=%.1f searches=%d expanded=%d steps=%s maxSide=%.1f maxFar=%.1f maxTop=%.1f",
                outcome.helicopter,
                outcome.scenario.index,
                outcome.scenario.directionIndex,
                outcome.scenario.kind,
                outcome.scenario.targetY,
                outcome.scenario.width,
                outcome.scenario.depth,
                outcome.scenario.height,
                outcome.result.recovered(),
                outcome.result.rangeLost(),
                outcome.result.ticks(),
                outcome.result.searches(),
                outcome.result.expandedCandidates(),
                outcome.result.steps(),
                progress.maxSideClearance(),
                progress.maxFarClearance(),
                progress.maxTopClearance());
    }

    private static String describe(Scenario scenario, int expanded) {
        return String.format(Locale.ROOT,
                "scene=%d dir=%d kind=%s targetY=%.0f size=%.0fx%.0fx%.0f expanded=%d",
                scenario.index, scenario.directionIndex, scenario.kind, scenario.targetY,
                scenario.width, scenario.depth, scenario.height, expanded);
    }

    private record Outcome(String helicopter, Scenario scenario,
                           HelicopterObstacleDecisionModel.SimulationResult result) {
        boolean initiallyBlocked() {
            return !lineClear(this.scenario.scene(),
                    this.scenario.scene().aircraft().add(CENTER_OFFSET),
                    this.scenario.scene().target());
        }

        boolean recoveredBy(double horizon) {
            return this.result.recovered()
                    && (!Double.isFinite(horizon) || this.result.ticks() <= horizon);
        }
    }

    private enum Kind {
        WIDE_WALL(true),
        TALL_TOWER(false),
        WIDE_BUILDING(false),
        DEEP_BUILDING(false);

        private final boolean solid;

        Kind(boolean solid) {
            this.solid = solid;
        }
    }

    private record Scenario(int index, int directionIndex, Kind kind, HelicopterObstacleDecisionModel.Scene scene,
                            double targetY, double width, double depth, double height) {
        static Scenario generate(int index, Random random) {
            int directionIndex = random.nextInt(DIRECTIONS.length);
            Kind kind = Kind.values()[index % Kind.values().length];
            double targetDistance = 52 + random.nextInt(7);
            double width;
            double depth;
            double baseHeight;
            switch (kind) {
                case WIDE_WALL -> {
                    width = 36 + random.nextInt(25);
                    depth = 4 + random.nextInt(5);
                    baseHeight = 12 + random.nextInt(9);
                }
                case TALL_TOWER -> {
                    width = 12 + random.nextInt(9);
                    depth = 12 + random.nextInt(9);
                    baseHeight = 28 + random.nextInt(17);
                }
                case WIDE_BUILDING -> {
                    width = 28 + random.nextInt(25);
                    depth = 14 + random.nextInt(13);
                    baseHeight = 20 + random.nextInt(19);
                }
                case DEEP_BUILDING -> {
                    width = 14 + random.nextInt(11);
                    depth = 24 + random.nextInt(9);
                    baseHeight = 18 + random.nextInt(15);
                }
                default -> throw new IllegalStateException("Unexpected kind " + kind);
            }
            double startHeight = 16 + random.nextInt(9);
            double targetHeight = switch ((index / Kind.values().length) % 3) {
                case 0 -> 1.0D;
                case 1 -> 9.0D;
                default -> 17.0D;
            };
            double targetLateral = random.nextInt(17) - 8;
            int halfDepth = (int) depth / 2;
            int minObstacleDistance = halfDepth + 10;
            int maxObstacleDistance = (int) targetDistance - halfDepth - 8;
            int obstacleDistance = minObstacleDistance
                    + random.nextInt(maxObstacleDistance - minObstacleDistance + 1);
            double lineHeight = Math.ceil(startHeight
                    + (targetHeight - startHeight) * obstacleDistance / targetDistance);
            double height = Math.max(baseHeight, lineHeight + 5.0D);
            Vec3 start = new Vec3(0.0D, startHeight, 0.0D);
            Vec3 target = new Vec3(targetDistance, targetHeight, targetLateral);
            float targetYaw = (float) (Mth.atan2(target.z - start.z, target.x - start.x)
                    * (180.0D / Math.PI)) - 90.0F;
            float yaw = Mth.wrapDegrees(targetYaw + random.nextFloat() * 240.0F - 120.0F);
            HelicopterObstacleDecisionModel.Obstacle obstacle = new HelicopterObstacleDecisionModel.Obstacle(
                    obstacleDistance - depth / 2.0D,
                    obstacleDistance + depth / 2.0D,
                    -width / 2.0D,
                    width / 2.0D,
                    height,
                    kind.solid);
            HelicopterObstacleDecisionModel.Scene scene = new HelicopterObstacleDecisionModel.Scene(
                    start,
                    target,
                    obstacle,
                    FOLLOW_RANGE,
                    yaw,
                    CENTER_OFFSET,
                    BOTTOM_OFFSET,
                    HALF_WIDTH);
            return new Scenario(index, directionIndex, kind, scene, targetHeight, width, depth, height);
        }
    }
}
