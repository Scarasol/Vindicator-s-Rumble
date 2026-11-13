package com.scarasol.vindicatorsrumble.gametest.profile;

import com.mojang.logging.LogUtils;
import com.scarasol.vindicatorsrumble.VindicatorsRumbleMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = VindicatorsRumbleMod.MODID)
public final class GroundRadarProfile {
    private static final ThreadMXBean CPU = ManagementFactory.getThreadMXBean();
    private static final List<Long> WALL = new ArrayList<>();
    private static final List<Long> CPU_TIMES = new ArrayList<>();
    private static final List<Long> TURN_WALL = new ArrayList<>();
    private static final List<Long> TURN_CPU = new ArrayList<>();
    private static final List<Long> CORRIDOR_WALL = new ArrayList<>();
    private static final List<Long> CORRIDOR_CPU = new ArrayList<>();
    private static boolean turning;
    private static boolean corridor;
    private static long wallStart;
    private static long cpuStart;
    private static long tickNanos;
    private static long maxTickNanos;

    private GroundRadarProfile() {
    }

    public static void begin(boolean turn) {
        turning = turn;
        corridor = false;
        if (CPU.isCurrentThreadCpuTimeSupported() && !CPU.isThreadCpuTimeEnabled()) {
            CPU.setThreadCpuTimeEnabled(true);
        }
        cpuStart = CPU.isCurrentThreadCpuTimeSupported() ? CPU.getCurrentThreadCpuTime() : -1;
        wallStart = System.nanoTime();
    }

    public static void beginCorridor() {
        begin(false);
        corridor = true;
    }

    public static void end() {
        long wall = System.nanoTime() - wallStart;
        long cpu = cpuStart < 0 ? -1 : CPU.getCurrentThreadCpuTime() - cpuStart;
        (corridor ? CORRIDOR_WALL : turning ? TURN_WALL : WALL).add(wall);
        if (cpu >= 0) (corridor ? CORRIDOR_CPU : turning ? TURN_CPU : CPU_TIMES).add(cpu);
        tickNanos += wall;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            maxTickNanos = Math.max(maxTickNanos, tickNanos);
            tickNanos = 0;
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        report("radar", WALL, CPU_TIMES);
        report("turn", TURN_WALL, TURN_CPU);
        report("corridor", CORRIDOR_WALL, CORRIDOR_CPU);
    }

    private static void report(String kind, List<Long> wallTimes, List<Long> cpuTimes) {
        if (wallTimes.isEmpty()) return;
        long[] wall = wallTimes.stream().mapToLong(Long::longValue).sorted().toArray();
        long[] cpu = cpuTimes.stream().mapToLong(Long::longValue).sorted().toArray();
        LogUtils.getLogger().info(
                "Ground probe timing kind={} calls={} wallMeanUs={} wallP50Us={} wallP95Us={} wallP99Us={} cpuMeanUs={} maxTickMs={}",
                kind, wall.length, meanMicros(wall), percentileMicros(wall, 0.5D), percentileMicros(wall, 0.95D),
                percentileMicros(wall, 0.99D), meanMicros(cpu), maxTickNanos / 1_000_000.0D);
    }

    private static double meanMicros(long[] values) {
        return java.util.Arrays.stream(values).average().orElse(0.0D) / 1_000.0D;
    }

    private static double percentileMicros(long[] values, double percentile) {
        return values[Math.min(values.length - 1, (int) Math.ceil(values.length * percentile) - 1)] / 1_000.0D;
    }
}
