package com.scarasol.vindicatorsrumble.gametest.profile;

import com.google.gson.GsonBuilder;
import com.scarasol.vindicatorsrumble.VindicatorsRumbleMod;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

@Mod.EventBusSubscriber(modid = VindicatorsRumbleMod.MODID)
public final class HelicopterSearchProfile {
    private static final ThreadMXBean CPU = ManagementFactory.getThreadMXBean();
    private static final com.sun.management.ThreadMXBean ALLOCATION =
            CPU instanceof com.sun.management.ThreadMXBean bean ? bean : null;
    private static final List<Search> SEARCHES = new ArrayList<>();
    private static final List<Tick> TICKS = new ArrayList<>();
    private static boolean active;
    private static boolean fixtureTick;
    private static Boolean originalDebug;
    private static long owner;
    private static long tick;
    private static long tickWall;
    private static long tickCpu;
    private static long tickBytes;
    private static long tickSearchNanos;
    private static long searchWall;
    private static long searchCpu;
    private static long searchBytes;
    private static boolean searching;
    private static long candidates;
    private static long fullCorridorScans;
    private static long heights;
    private static long worldHeights;
    private static long rays;
    private static long geometryNanos;
    private static long rayNanos;
    private static long geometryStart;
    private static long rayStart;
    private static long probeSamples;
    private static long probeRays;
    private static long duplicateProbeRays;
    private static boolean probeSampling;
    private static Vec3 frontOrigin;
    private static Vec3 frontDirection;
    private static Vec3 elevatedOrigin;
    private static Vec3 elevatedDirection;

    private HelicopterSearchProfile() {
    }

    public static void start() {
        if (CPU.isThreadCpuTimeSupported() && !CPU.isThreadCpuTimeEnabled()) {
            CPU.setThreadCpuTimeEnabled(true);
        }
        if (ALLOCATION != null && ALLOCATION.isThreadAllocatedMemorySupported()
                && !ALLOCATION.isThreadAllocatedMemoryEnabled()) {
            ALLOCATION.setThreadAllocatedMemoryEnabled(true);
        }
        SEARCHES.clear();
        TICKS.clear();
        tick = 0;
        tickWall = 0;
        searching = false;
        probeSamples = probeRays = duplicateProbeRays = 0;
        probeSampling = false;
        originalDebug = CommonConfig.VEHICLE_AI_DEBUG.get();
        CommonConfig.VEHICLE_AI_DEBUG.set(false);
        owner = Thread.currentThread().getId();
        active = true;
    }

    public static void fixtureTick() {
        if (active) fixtureTick = true;
    }

    public static void beginSearch() {
        if (!active || Thread.currentThread().getId() != owner) return;
        candidates = fullCorridorScans = heights = worldHeights = rays = geometryNanos = rayNanos = 0;
        searchBytes = allocated();
        searchCpu = cpu();
        searchWall = System.nanoTime();
        searching = true;
    }

    public static void endSearch() {
        if (!searching) return;
        long wall = System.nanoTime() - searchWall;
        long cpu = difference(cpu(), searchCpu);
        long bytes = difference(allocated(), searchBytes);
        searching = false;
        tickSearchNanos += wall;
        SEARCHES.add(new Search(tick, wall, cpu, bytes, candidates, fullCorridorScans, heights, worldHeights,
                rays, geometryNanos, rayNanos));
    }

    public static void candidate() {
        if (searching) candidates++;
    }

    public static void fullCorridorScan() {
        if (searching) fullCorridorScans++;
    }

    public static void height() {
        if (searching) heights++;
    }

    public static void worldHeight() {
        if (searching) worldHeights++;
    }

    public static void beginGeometry() {
        if (searching) geometryStart = System.nanoTime();
    }

    public static void endGeometry() {
        if (searching) geometryNanos += System.nanoTime() - geometryStart;
    }

    public static void beginRay() {
        if (searching) {
            rays++;
            rayStart = System.nanoTime();
        }
    }

    public static void endRay() {
        if (searching) rayNanos += System.nanoTime() - rayStart;
    }

    public static void beginProbeSample() {
        if (!active || Thread.currentThread().getId() != owner) return;
        probeSamples++;
        probeSampling = true;
        frontOrigin = frontDirection = elevatedOrigin = elevatedDirection = null;
    }

    public static void probeRay(Vec3 origin, Vec3 direction, boolean front) {
        if (!active || Thread.currentThread().getId() != owner) return;
        probeRays++;
        if (!probeSampling) return;
        Vec3 previousOrigin = front ? frontOrigin : elevatedOrigin;
        Vec3 previousDirection = front ? frontDirection : elevatedDirection;
        if (origin.equals(previousOrigin) && direction.equals(previousDirection)) {
            duplicateProbeRays++;
        }
        if (front) {
            frontOrigin = origin;
            frontDirection = direction;
        } else {
            elevatedOrigin = origin;
            elevatedDirection = direction;
        }
    }

    public static void endProbeSample() {
        if (active && Thread.currentThread().getId() == owner) probeSampling = false;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (!active) return;
        if (event.phase == TickEvent.Phase.START) {
            tick++;
            fixtureTick = false;
            tickSearchNanos = 0;
            tickBytes = allocated();
            tickCpu = cpu();
            tickWall = System.nanoTime();
        } else if (tickWall != 0) {
            long wall = System.nanoTime() - tickWall;
            long cpu = difference(cpu(), tickCpu);
            long bytes = difference(allocated(), tickBytes);
            TICKS.add(new Tick(tick, fixtureTick, wall, cpu, bytes, tickSearchNanos));
            tickWall = 0;
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        if (!active) return;
        active = false;
        if (originalDebug != null) {
            CommonConfig.VEHICLE_AI_DEBUG.set(originalDebug);
            originalDebug = null;
        }
        String label = System.getenv().getOrDefault("VR_HELI_BENCH_LABEL", "unlabelled")
                .replaceAll("[^A-Za-z0-9._-]", "_");
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("label", label);
        report.put("profileMode", System.getProperty("vindicatorsrumble.helicopterProfile", "detailed"));
        report.put("sampleCountersMeasured",
                !"light".equals(System.getProperty("vindicatorsrumble.helicopterProfile")));
        report.put("candidateCountersMeasured", true);
        report.put("fullCorridorCountersMeasured", true);
        report.put("probeCountersMeasured", Boolean.getBoolean("vindicatorsrumble.helicopterProbeProfile"));
        report.put("probeSamples", probeSamples);
        report.put("probeRays", probeRays);
        report.put("duplicateProbeRaysWithinSample", duplicateProbeRays);
        report.put("scope", "instrumented normal server ticks; search geometry/rays are inclusive wall times");
        report.put("warmupTicksExcludedFromSteady", 200);
        report.put("steadyTickCaveat", "excludes batch construction only; includes obstacle insertion, cleanup, "
                + "fixture observations and other mods; not pure gameplay MSPT");
        report.put("instrumentationObserved", !SEARCHES.isEmpty());
        report.put("searchAll", searchStats(SEARCHES));
        report.put("searchSteady", searchStats(SEARCHES.stream().filter(s -> s.tick > 200).toList()));
        List<Tick> steady = TICKS.stream().filter(t -> t.tick > 200 && !t.fixture).toList();
        report.put("steadyTicks", Map.of(
                "wallNanos", stats(steady, Tick::wall),
                "cpuNanos", stats(steady, Tick::cpu),
                "allocatedBytes", stats(steady, Tick::bytes),
                "searchWallNanos", stats(steady, Tick::searchWall)));
        report.put("fixtureTicksExcluded", TICKS.stream().filter(Tick::fixture).count());
        report.put("cpuSupported", CPU.isCurrentThreadCpuTimeSupported());
        report.put("allocationSupported", ALLOCATION != null && ALLOCATION.isThreadAllocatedMemorySupported());
        Path directory = Path.of("vehicle-ai-benchmarks");
        try {
            Files.createDirectories(directory);
            Path output = directory.resolve(label + ".json").toAbsolutePath();
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
            VindicatorsRumbleMod.LOGGER.info("Helicopter profile output={} searches={} steadyTicks={}",
                    output, SEARCHES.size(), steady.size());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save helicopter benchmark", exception);
        }
    }

    private static Map<String, Object> searchStats(List<Search> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("wallNanos", stats(values, Search::wall));
        result.put("cpuNanos", stats(values, Search::cpu));
        result.put("allocatedBytes", stats(values, Search::bytes));
        result.put("candidateAttempts", stats(values, Search::candidates));
        result.put("fullCorridorScans", stats(values, Search::fullCorridorScans));
        result.put("heightLookups", stats(values, Search::heights));
        result.put("worldHeightReads", stats(values, Search::worldHeights));
        result.put("exactLosRays", stats(values, Search::rays));
        result.put("geometryWallNanos", stats(values, Search::geometry));
        result.put("rayWallNanos", stats(values, Search::ray));
        return result;
    }

    private static <T> Map<String, Number> stats(List<T> values, ToLongFunction<T> measure) {
        long[] sorted = values.stream().mapToLong(measure).filter(v -> v >= 0).sorted().toArray();
        long total = Arrays.stream(sorted).sum();
        return Map.of("count", sorted.length, "total", total,
                "mean", sorted.length == 0 ? 0.0D : total / (double) sorted.length,
                "p50", percentile(sorted, 0.50D), "p95", percentile(sorted, 0.95D),
                "p99", percentile(sorted, 0.99D), "max", sorted.length == 0 ? 0L : sorted[sorted.length - 1]);
    }

    private static long percentile(long[] sorted, double fraction) {
        return sorted.length == 0 ? 0L : sorted[(int) Math.ceil(sorted.length * fraction) - 1];
    }

    private static long cpu() {
        return CPU.isCurrentThreadCpuTimeSupported() ? CPU.getCurrentThreadCpuTime() : -1L;
    }

    private static long allocated() {
        return ALLOCATION != null && ALLOCATION.isThreadAllocatedMemorySupported()
                ? ALLOCATION.getThreadAllocatedBytes(Thread.currentThread().getId()) : -1L;
    }

    private static long difference(long end, long start) {
        return start < 0 || end < 0 ? -1L : end - start;
    }

    private record Search(long tick, long wall, long cpu, long bytes, long candidates, long fullCorridorScans,
                          long heights, long worldHeights, long rays, long geometry, long ray) {
    }

    private record Tick(long tick, boolean fixture, long wall, long cpu, long bytes, long searchWall) {
    }
}
