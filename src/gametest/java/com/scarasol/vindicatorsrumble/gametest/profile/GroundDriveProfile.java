package com.scarasol.vindicatorsrumble.gametest.profile;

import com.mojang.logging.LogUtils;
import com.scarasol.vindicatorsrumble.VindicatorsRumbleMod;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleDimensions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber(modid = VindicatorsRumbleMod.MODID)
public final class GroundDriveProfile {
    private static final Map<String, List<Long>> SAMPLES = new LinkedHashMap<>();
    private static final ThreadLocal<Deque<Sample>> ACTIVE = ThreadLocal.withInitial(ArrayDeque::new);
    private static long directRamChecks;
    private static long ramSeatQueries;
    private static long ramAmmoQueries;
    private static final Set<Segment> SEGMENTS = new HashSet<>();
    private static long duplicateSegments;

    private GroundDriveProfile() {
    }

    public static void begin(String kind) {
        ACTIVE.get().push(new Sample(kind, System.nanoTime()));
    }

    public static void end() {
        Sample sample = ACTIVE.get().pop();
        long elapsed = System.nanoTime() - sample.start();
        SAMPLES.computeIfAbsent(sample.kind(), ignored -> new ArrayList<>()).add(elapsed);
    }

    public static void directRamCheck() {
        directRamChecks++;
    }

    public static void ramSeatQuery() {
        ramSeatQueries++;
    }

    public static void ramAmmoQuery() {
        ramAmmoQueries++;
    }

    public static void corridor(CollisionGetter level, Entity entity, Vec3 from, Vec3 to, VehicleDimensions dimensions) {
        if (!SEGMENTS.add(new Segment(level, entity, from, to, dimensions))) duplicateSegments++;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) SEGMENTS.clear();
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        SAMPLES.forEach((kind, samples) -> {
            long[] sorted = samples.stream().mapToLong(Long::longValue).sorted().toArray();
            long total = java.util.Arrays.stream(sorted).sum();
            LogUtils.getLogger().info(
                    "Ground drive timing kind={} calls={} totalMs={} meanUs={} p50Us={} p95Us={} p99Us={} maxUs={}",
                    kind, sorted.length, total / 1_000_000.0D, total / (1000.0D * sorted.length),
                    percentile(sorted, 0.5D), percentile(sorted, 0.95D), percentile(sorted, 0.99D),
                    sorted[sorted.length - 1] / 1000.0D);
        });
        LogUtils.getLogger().info("Ground drive work directRamChecks={} duplicateSegments={}",
                directRamChecks, duplicateSegments);
        LogUtils.getLogger().info("Ground ram work seatQueries={} ammoQueries={}", ramSeatQueries, ramAmmoQueries);
    }

    private static double percentile(long[] sorted, double fraction) {
        return sorted[Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * fraction) - 1)] / 1000.0D;
    }

    private record Sample(String kind, long start) {
    }

    private record Segment(CollisionGetter level, Entity entity, Vec3 from, Vec3 to, VehicleDimensions dimensions) {
    }
}
