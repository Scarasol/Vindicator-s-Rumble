package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.scarasol.vindicatorsrumble.VindicatorsRumbleMod;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

final class HelicopterObstacleCorpus {
    private static final int SCENARIO_COUNT = 1_024;
    private static final int BATCH_SIZE = 16;
    private static final int GRID_COLUMNS = 4;
    private static final int CELL_SIZE = 160;
    private static final int CHUNK_MARGIN = 40;
    private static final long SEED = 0x51DEB0A7L;
    private static final long SCENE_SEED_STEP = 0x9E3779B97F4A7C15L;
    private static final List<ResourceLocation> HELICOPTERS = List.of(
            new ResourceLocation("superbwarfare", "ah_6"),
            new ResourceLocation("superbwarfare", "mi_28"));

    private static final double REQUIRED_RECOVERY_RATE = 0.98D;
    private static final double REQUIRED_YAW_RESPONSE_RATE = 0.95D;
    private static final double MAX_QUALITY_FAILURE_RATE = 0.05D;
    private static final int MAX_FIRST_MOVEMENT_TICKS = 40;
    private static final int MAX_STALL_TICKS = 24;
    private static final int MAX_PAUSES = 1;
    private static final int MAX_YAW_RESPONSE_TICKS = 10;
    private static final int MAX_YAW_ALIGNMENT_TICKS = 50;
    private static final int MAX_POST_RECOVERY_BLOCKED_TICKS = 7;
    private static final double MIN_AVERAGE_HORIZONTAL_SPEED = 0.08D;
    private static final double MIN_AVERAGE_TOTAL_SPEED = 0.12D;
    private static final double MIN_KIND_HORIZONTAL_SPEED = 0.06D;
    private static final double MIN_KIND_TOTAL_SPEED = 0.10D;
    private static final double MAX_ATTITUDE = 75.0D;
    private static final double MAX_UNSTABLE_ATTITUDE_RATIO = 0.10D;
    private static final double DIAGNOSTIC_NEAR_MID_LOSS_DISTANCE = 60.0D;
    private static final double DIAGNOSTIC_CLEARANCE_MARGIN = 2.0D;

    private final GameTestHelper helper;
    private final ServerLevel level;
    private final BlockPos arenaOrigin;
    private final List<HelicopterScenarioRun> active = new ArrayList<>(BATCH_SIZE);
    private final List<HelicopterScenarioRun> completed = new ArrayList<>(SCENARIO_COUNT);
    private final Set<Long> forcedChunks = new HashSet<>();
    private int generated;
    private boolean ended;
    private Boolean originalNeedsAmmo;
    private String lastFailureTraceStatus = "none";

    HelicopterObstacleCorpus(GameTestHelper helper) {
        this.helper = helper;
        this.level = helper.getLevel();
        BlockPos template = helper.absolutePos(new BlockPos(2, 1, 2));
        this.arenaOrigin = template.offset(72, 0, 72);
    }

    void start() {
        this.originalNeedsAmmo = CommonConfig.VEHICLE_AI_NEEDS_AMMO.get();
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(true);
        try {
            this.spawnBatch();
            this.helper.onEachTick(this::onServerTick);
        } catch (RuntimeException | Error throwable) {
            this.restoreAmmoConfig();
            throw throwable;
        }
    }

    private void onServerTick() {
        if (this.ended) {
            return;
        }
        try {
            for (HelicopterScenarioRun run : this.active) {
                run.observeServerTick();
            }
            this.finishCompletedRuns();
            if (!this.active.isEmpty()) {
                return;
            }
            if (this.generated < SCENARIO_COUNT) {
                this.spawnBatch();
                return;
            }
            this.finishTest();
        } catch (GameTestAssertException assertion) {
            this.restoreAmmoConfig();
            throw assertion;
        } catch (Throwable throwable) {
            this.abort(throwable);
        }
    }

    private void spawnBatch() {
        com.scarasol.vindicatorsrumble.gametest.profile.HelicopterSearchProfile.fixtureTick();
        int count = Math.min(BATCH_SIZE, SCENARIO_COUNT - this.generated);
        for (int slot = 0; slot < count; slot++) {
            int slotX = slot % GRID_COLUMNS;
            int slotZ = slot / GRID_COLUMNS;
            int x = this.arenaOrigin.getX() + slotX * CELL_SIZE;
            int z = this.arenaOrigin.getZ() + slotZ * CELL_SIZE;
            this.level.getChunk(x >> 4, z >> 4);
            int y = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int runIndex = this.generated++;
            int sceneIndex = runIndex / HELICOPTERS.size();
            ResourceLocation helicopterId = HELICOPTERS.get(runIndex % HELICOPTERS.size());
            HelicopterScenario scene = HelicopterScenario.generate(
                    sceneIndex,
                    new Random(SEED ^ (SCENE_SEED_STEP * (sceneIndex + 1L))),
                    new BlockPos(x, y, z));
            this.forceSceneChunks(scene);
            HelicopterScenarioRun run = new HelicopterScenarioRun(this.level, scene, helicopterId);
            run.spawn();
            this.active.add(run);
        }
        this.finishCompletedRuns();
    }

    private void finishCompletedRuns() {
        for (int i = this.active.size() - 1; i >= 0; i--) {
            HelicopterScenarioRun run = this.active.get(i);
            if (!run.complete()) {
                continue;
            }
            run.cleanup();
            if (this.failureReasons(run).isEmpty()) {
                run.discardTrace();
            }
            this.completed.add(run);
            this.active.remove(i);
        }
    }

    private void forceSceneChunks(HelicopterScenario scene) {
        int minX = (int) Math.floor(Math.min(scene.start().x, scene.target().x)) - CHUNK_MARGIN;
        int maxX = (int) Math.ceil(Math.max(scene.start().x, scene.target().x)) + CHUNK_MARGIN;
        int minZ = (int) Math.floor(Math.min(scene.start().z, scene.target().z)) - CHUNK_MARGIN;
        int maxZ = (int) Math.ceil(Math.max(scene.start().z, scene.target().z)) + CHUNK_MARGIN;
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                long key = ChunkPos.asLong(chunkX, chunkZ);
                if (this.forcedChunks.add(key)) {
                    this.level.setChunkForced(chunkX, chunkZ, true);
                }
            }
        }
    }

    private void finishTest() {
        this.ended = true;
        String report = this.report();
        this.releaseChunks();
        this.restoreAmmoConfig();
        VindicatorsRumbleMod.LOGGER.info("{}", report);
        if (this.passed()) {
            this.helper.succeed();
        } else {
            this.helper.fail(this.conciseFailureMessage());
        }
    }

    private void abort(Throwable throwable) {
        this.ended = true;
        for (HelicopterScenarioRun run : this.active) {
            run.cleanup();
        }
        this.active.clear();
        this.releaseChunks();
        this.restoreAmmoConfig();
        this.helper.fail("helicopter full-goal corpus aborted: " + throwable);
    }

    private void restoreAmmoConfig() {
        if (this.originalNeedsAmmo != null) {
            CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(this.originalNeedsAmmo);
            this.originalNeedsAmmo = null;
        }
    }

    private void releaseChunks() {
        for (long key : this.forcedChunks) {
            this.level.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false);
        }
        this.forcedChunks.clear();
    }

    private boolean passed() {
        long recovered = this.completed.stream().filter(HelicopterScenarioRun::recovered).count();
        long responsive = this.completed.stream().filter(run -> run.recovered()
                && run.yawResponseTicks() >= 0 && run.yawResponseTicks() <= MAX_YAW_RESPONSE_TICKS).count();
        long aligned = this.completed.stream().filter(run -> run.recovered()
                && run.yawAlignmentTicks() >= 0 && run.yawAlignmentTicks() <= MAX_YAW_ALIGNMENT_TICKS).count();
        long delayed = this.completed.stream().filter(run -> run.firstMovementAt() < 0
                || run.firstMovementAt() > MAX_FIRST_MOVEMENT_TICKS).count();
        long stalled = this.completed.stream().filter(run -> run.longestStall() > MAX_STALL_TICKS).count();
        long pauseHeavy = this.completed.stream().filter(run -> run.pauseCount() > MAX_PAUSES).count();
        double recoveryRate = recovered / (double) SCENARIO_COUNT;
        double averageHorizontal = this.completed.stream()
                .mapToDouble(HelicopterScenarioRun::averageHorizontalSpeed).average().orElse(0.0D);
        double averageTotal = this.completed.stream()
                .mapToDouble(HelicopterScenarioRun::averageTotalSpeed).average().orElse(0.0D);
        return this.completed.size() == SCENARIO_COUNT
                && recoveryRate >= REQUIRED_RECOVERY_RATE
                && responsive / (double) Math.max(1L, recovered) >= REQUIRED_YAW_RESPONSE_RATE
                && aligned / (double) Math.max(1L, recovered) >= REQUIRED_YAW_RESPONSE_RATE
                && delayed / (double) SCENARIO_COUNT <= MAX_QUALITY_FAILURE_RATE
                && stalled / (double) SCENARIO_COUNT <= MAX_QUALITY_FAILURE_RATE
                && pauseHeavy / (double) SCENARIO_COUNT <= MAX_QUALITY_FAILURE_RATE
                && averageHorizontal >= MIN_AVERAGE_HORIZONTAL_SPEED
                && averageTotal >= MIN_AVERAGE_TOTAL_SPEED
                && this.allObstacleKindsMeetSpeedFloor()
                && this.completed.stream().noneMatch(this::hasCriticalFailure);
    }

    private boolean allObstacleKindsMeetSpeedFloor() {
        for (HelicopterObstacleKind kind : HelicopterObstacleKind.values()) {
            double horizontal = this.completed.stream().filter(run -> run.scene().kind() == kind)
                    .mapToDouble(HelicopterScenarioRun::averageHorizontalSpeed).average().orElse(0.0D);
            double total = this.completed.stream().filter(run -> run.scene().kind() == kind)
                    .mapToDouble(HelicopterScenarioRun::averageTotalSpeed).average().orElse(0.0D);
            if (horizontal < MIN_KIND_HORIZONTAL_SPEED || total < MIN_KIND_TOTAL_SPEED) {
                return false;
            }
        }
        return true;
    }

    private String report() {
        long recovered = this.completed.stream().filter(HelicopterScenarioRun::recovered).count();
        long invalid = this.completed.stream().filter(run -> !run.valid()).count();
        long delayed = this.completed.stream().filter(run -> run.firstMovementAt() < 0
                || run.firstMovementAt() > MAX_FIRST_MOVEMENT_TICKS).count();
        long stalled = this.completed.stream().filter(run -> run.longestStall() > MAX_STALL_TICKS).count();
        long pauseHeavy = this.completed.stream().filter(run -> run.pauseCount() > MAX_PAUSES).count();
        long unresponsive = this.completed.stream().filter(run -> run.recovered()
                && (run.yawResponseTicks() < 0 || run.yawResponseTicks() > MAX_YAW_RESPONSE_TICKS)).count();
        long unaligned = this.completed.stream().filter(run -> run.recovered()
                && (run.yawAlignmentTicks() < 0 || run.yawAlignmentTicks() > MAX_YAW_ALIGNMENT_TICKS)).count();
        long collided = this.completed.stream().filter(run -> run.collisionTicks() > 0).count();
        long reoccluded = this.completed.stream().filter(run ->
                run.longestPostRecoveryBlockedTicks() > MAX_POST_RECOVERY_BLOCKED_TICKS).count();
        long postRecoveryOcclusions = this.completed.stream()
                .mapToLong(HelicopterScenarioRun::postRecoveryOcclusions).sum();
        int longestReocclusion = this.completed.stream()
                .mapToInt(HelicopterScenarioRun::longestPostRecoveryBlockedTicks).max().orElse(0);
        long targetLost = this.completed.stream().filter(HelicopterScenarioRun::targetLost).count();
        long targetVisibleAtLoss = this.completed.stream().filter(run ->
                run.targetLost() && run.targetVisibleAtLoss()).count();
        long dismounted = this.completed.stream().filter(HelicopterScenarioRun::dismounted).count();
        long wrecked = this.completed.stream().filter(HelicopterScenarioRun::wrecked).count();
        long unstable = this.completed.stream().filter(run -> run.maxPitch() > MAX_ATTITUDE
                || run.maxRoll() > MAX_ATTITUDE
                || run.unstableAttitudeRatio() > MAX_UNSTABLE_ATTITUDE_RATIO).count();
        long altitudeRunaway = this.completed.stream().filter(HelicopterScenarioRun::altitudeRunaway).count();
        long rangeRunaway = this.completed.stream().filter(run ->
                run.maxTargetDistance() > run.scene().allowedTargetDistance()).count();
        long slow = this.completed.stream().filter(run -> run.averageHorizontalSpeed() < MIN_AVERAGE_HORIZONTAL_SPEED
                || run.averageTotalSpeed() < MIN_AVERAGE_TOTAL_SPEED).count();
        double averageHorizontal = this.completed.stream()
                .mapToDouble(HelicopterScenarioRun::averageHorizontalSpeed).average().orElse(0.0D);
        double averageTotal = this.completed.stream()
                .mapToDouble(HelicopterScenarioRun::averageTotalSpeed).average().orElse(0.0D);
        double averageAltitude = this.completed.stream()
                .mapToDouble(HelicopterScenarioRun::averageAltitude).average().orElse(0.0D);
        long adInputTicks = this.completed.stream().mapToLong(HelicopterScenarioRun::adInputTicks).sum();
        long deltaRotTicks = this.completed.stream().mapToLong(HelicopterScenarioRun::deltaRotTicks).sum();
        long opposedRollTicks = this.completed.stream().mapToLong(HelicopterScenarioRun::opposedRollTicks).sum();
        double mouseRollDemand = this.completed.stream().mapToDouble(HelicopterScenarioRun::mouseRollDemandSum).sum();
        double adRollDemand = this.completed.stream().mapToDouble(HelicopterScenarioRun::adRollDemandSum).sum();
        double netRollDemand = this.completed.stream().mapToDouble(HelicopterScenarioRun::netRollDemandSum).sum();
        double maxMouseRollDemand = this.completed.stream()
                .mapToDouble(HelicopterScenarioRun::maxMouseRollDemand).max().orElse(0.0D);
        double maxAdRollDemand = this.completed.stream()
                .mapToDouble(HelicopterScenarioRun::maxAdRollDemand).max().orElse(0.0D);
        int p50Recovery = percentileRecovery(0.50D);
        int p95Recovery = percentileRecovery(0.95D);
        int p50TargetLoss = percentileTargetLossTick(0.50D);
        int p95TargetLoss = percentileTargetLossTick(0.95D);
        double p50TargetLossDistance = percentileTargetLossDistance(0.50D);
        double p95TargetLossDistance = percentileTargetLossDistance(0.95D);
        double p50TargetLossDistance3d = percentileTargetLossDistance3d(0.50D);
        double p95TargetLossDistance3d = percentileTargetLossDistance3d(0.95D);
        long nearMidTargetLoss = this.completed.stream()
                .filter(this::isNearMidTargetLoss).count();
        long nearMidSideClear = this.completed.stream()
                .filter(this::isNearMidTargetLoss)
                .filter(run -> run.maxSideClearance() >= DIAGNOSTIC_CLEARANCE_MARGIN).count();
        long nearMidFarClear = this.completed.stream()
                .filter(this::isNearMidTargetLoss)
                .filter(run -> run.maxFarClearance() >= DIAGNOSTIC_CLEARANCE_MARGIN).count();
        long nearMidTopClear = this.completed.stream()
                .filter(this::isNearMidTargetLoss)
                .filter(run -> run.maxTopClearance() >= DIAGNOSTIC_CLEARANCE_MARGIN).count();

        StringBuilder report = new StringBuilder(256_000);
        report.append("helicopter-full-goal-obstacle-corpus\n")
                .append("execution=normal-server-ticks goalInjection=EntityJoinLevelEvent targetFixture=vanilla-visible-acquisition motionMetrics=until-los-recovery manualProductionTicks=0\n")
                .append("seed=").append(SEED)
                .append(" scenarios=").append(SCENARIO_COUNT)
                .append(" pairedScenes=").append(SCENARIO_COUNT / HELICOPTERS.size())
                .append(" helicopters=").append(HELICOPTERS)
                .append(" batches=").append((SCENARIO_COUNT + BATCH_SIZE - 1) / BATCH_SIZE)
                .append(" batchSize=").append(BATCH_SIZE).append('\n')
                .append(String.format(Locale.ROOT,
                        "recovered=%d/%d rate=%.3f required=%.3f recoveryP50=%d recoveryP95=%d avgHorizontal=%.3f avg3D=%.3f avgAltitude=%.2f%n",
                        recovered, SCENARIO_COUNT, recovered / (double) SCENARIO_COUNT,
                        REQUIRED_RECOVERY_RATE, p50Recovery, p95Recovery,
                        averageHorizontal, averageTotal, averageAltitude))
                .append(String.format(Locale.ROOT,
                        "invalid=%d delayed=%d slow=%d stalled=%d pauseHeavy=%d yawUnresponsive=%d yawUnaligned=%d collided=%d reoccluded=%d targetLost=%d dismounted=%d unstable=%d altitudeRunaway=%d rangeRunaway=%d wrecked=%d%n",
                        invalid, delayed, slow, stalled, pauseHeavy, unresponsive, unaligned, collided, reoccluded, targetLost,
                        dismounted, unstable, altitudeRunaway, rangeRunaway, wrecked))
                .append(String.format(Locale.ROOT,
                        "postRecoveryOcclusions=%d longestReocclusion=%d allowedBlockedTicks=%d observationTicks=%d%n",
                        postRecoveryOcclusions, longestReocclusion, MAX_POST_RECOVERY_BLOCKED_TICKS,
                        HelicopterScenarioRun.POST_RECOVERY_TICKS))
                .append(String.format(Locale.ROOT,
                        "targetLossTickP50=%d targetLossTickP95=%d targetLossHorizontalP50=%.1f targetLossHorizontalP95=%.1f targetLossDistance3dP50=%.1f targetLossDistance3dP95=%.1f visibleAtLoss=%d%n",
                        p50TargetLoss, p95TargetLoss, p50TargetLossDistance, p95TargetLossDistance,
                        p50TargetLossDistance3d,
                        p95TargetLossDistance3d, targetVisibleAtLoss))
                .append(String.format(Locale.ROOT,
                        "nearMidTargetLoss=%d sideCleared=%d farCleared=%d topCleared=%d clearanceMargin=%.1f%n",
                        nearMidTargetLoss, nearMidSideClear, nearMidFarClear, nearMidTopClear,
                        DIAGNOSTIC_CLEARANCE_MARGIN))
                .append(String.format(Locale.ROOT,
                        "rollControl adInputTicks=%d deltaRotTicks=%d opposedRate=%.3f mouseAvg=%.3f adAvg=%.3f netAvg=%.3f mouseMax=%.3f adMax=%.3f%n",
                        adInputTicks, deltaRotTicks, opposedRollTicks / (double) Math.max(1L, deltaRotTicks),
                        mouseRollDemand / Math.max(1L, deltaRotTicks),
                        adRollDemand / Math.max(1L, deltaRotTicks),
                        netRollDemand / Math.max(1L, deltaRotTicks),
                        maxMouseRollDemand, maxAdRollDemand));

        for (ResourceLocation helicopterId : HELICOPTERS) {
            long modelTotal = this.completed.stream().filter(run -> run.helicopterId().equals(helicopterId)).count();
            long modelRecovered = this.completed.stream().filter(run -> run.helicopterId().equals(helicopterId)
                    && run.recovered()).count();
            long modelTargetLost = this.completed.stream().filter(run -> run.helicopterId().equals(helicopterId)
                    && run.targetLost()).count();
            long modelUnstable = this.completed.stream().filter(run -> run.helicopterId().equals(helicopterId)
                    && (run.maxPitch() > MAX_ATTITUDE || run.maxRoll() > MAX_ATTITUDE
                    || run.unstableAttitudeRatio() > MAX_UNSTABLE_ATTITUDE_RATIO)).count();
            long modelSlow = this.completed.stream().filter(run -> run.helicopterId().equals(helicopterId)
                    && (run.averageHorizontalSpeed() < MIN_AVERAGE_HORIZONTAL_SPEED
                    || run.averageTotalSpeed() < MIN_AVERAGE_TOTAL_SPEED)).count();
            long modelAltitudeRunaway = this.completed.stream().filter(run -> run.helicopterId().equals(helicopterId)
                    && run.altitudeRunaway()).count();
            double modelHorizontal = this.completed.stream().filter(run -> run.helicopterId().equals(helicopterId))
                    .mapToDouble(HelicopterScenarioRun::averageHorizontalSpeed).average().orElse(0.0D);
            double modelTotalSpeed = this.completed.stream().filter(run -> run.helicopterId().equals(helicopterId))
                    .mapToDouble(HelicopterScenarioRun::averageTotalSpeed).average().orElse(0.0D);
            report.append(String.format(Locale.ROOT,
                    "helicopter=%s recovered=%d/%d targetLost=%d slow=%d unstable=%d altitudeRunaway=%d avgHorizontal=%.3f avg3D=%.3f%n",
                    helicopterId, modelRecovered, modelTotal, modelTargetLost, modelSlow,
                    modelUnstable, modelAltitudeRunaway,
                    modelHorizontal, modelTotalSpeed));
        }

        for (HelicopterObstacleKind kind : HelicopterObstacleKind.values()) {
            long kindTotal = this.completed.stream().filter(run -> run.scene().kind() == kind).count();
            long kindRecovered = this.completed.stream().filter(run -> run.scene().kind() == kind && run.recovered()).count();
            long kindTargetLost = this.completed.stream().filter(run ->
                    run.scene().kind() == kind && run.targetLost()).count();
            double kindHorizontal = this.completed.stream().filter(run -> run.scene().kind() == kind)
                    .mapToDouble(HelicopterScenarioRun::averageHorizontalSpeed).average().orElse(0.0D);
            double kindTotalSpeed = this.completed.stream().filter(run -> run.scene().kind() == kind)
                    .mapToDouble(HelicopterScenarioRun::averageTotalSpeed).average().orElse(0.0D);
            report.append(String.format(Locale.ROOT,
                    "kind=%s recovered=%d/%d targetLost=%d avgHorizontal=%.3f avg3D=%.3f%n",
                    kind, kindRecovered, kindTotal, kindTargetLost, kindHorizontal, kindTotalSpeed));
        }

        for (int targetHeight : new int[]{1, 9, 17}) {
            long heightTotal = this.completed.stream().filter(run ->
                    run.scene().targetHeight() == targetHeight).count();
            long heightRecovered = this.completed.stream().filter(run ->
                    run.scene().targetHeight() == targetHeight && run.recovered()).count();
            long heightTargetLost = this.completed.stream().filter(run ->
                    run.scene().targetHeight() == targetHeight && run.targetLost()).count();
            report.append(String.format(Locale.ROOT,
                    "targetY=%d recovered=%d/%d targetLost=%d%n",
                    targetHeight, heightRecovered, heightTotal, heightTargetLost));
        }

        List<HelicopterScenarioRun> failures = this.completed.stream()
                .filter(run -> !this.failureReasons(run).isEmpty())
                .sorted(Comparator.comparingInt(this::severity).reversed())
                .toList();
        this.lastFailureTraceStatus = this.writeFailureTraceFiles(failures);
        report.append(this.failureProgressSummary(failures));
        report.append(this.failureMotionSummary(failures));
        report.append("failureTrace=per-scene-files count=").append(failures.size())
                .append(" status=").append(this.lastFailureTraceStatus).append('\n');
        failures.forEach(run -> {
            String reasons = this.failureReasons(run);
            report.append(run.describe(reasons)).append('\n');
        });
        return report.toString();
    }

    private String writeFailureTraceFiles(List<HelicopterScenarioRun> failures) {
        if (failures.isEmpty()) {
            return "none";
        }
        Path traceDir = this.traceDirectory().resolve("helicopter-obstacle-" + System.currentTimeMillis());
        try {
            Files.createDirectories(traceDir);
            StringBuilder index = new StringBuilder(failures.size() * 512);
            index.append("helicopter failure traces\n");
            index.append("count=").append(failures.size()).append('\n');
            for (HelicopterScenarioRun run : failures) {
                String reasons = this.failureReasons(run);
                String fileName = String.format(Locale.ROOT, "%s_scene_%04d_%s.txt",
                        run.helicopterId().getPath(), run.scene().index(), this.sanitizeFileName(reasons));
                Path file = traceDir.resolve(fileName);
                String description = run.describe(reasons);
                Files.writeString(file, description + System.lineSeparator() + run.trace(reasons),
                        StandardCharsets.UTF_8);
                index.append(fileName).append('\t').append(description).append('\n');
            }
            Files.writeString(traceDir.resolve("index.tsv"), index.toString(), StandardCharsets.UTF_8);
            return traceDir.toAbsolutePath().normalize().toString();
        } catch (IOException exception) {
            return "write_failed:" + exception.getClass().getSimpleName() + ':' + exception.getMessage();
        }
    }

    private Path traceDirectory() {
        String configured = System.getProperty("vindicatorsrumble.gametest.traceDir");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("VR_GAMETEST_TRACE_DIR");
        }
        if (configured == null || configured.isBlank()) {
            configured = "vehicle-ai-test-traces";
        }
        return Path.of(configured);
    }

    private String sanitizeFileName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String conciseFailureMessage() {
        long recovered = this.completed.stream().filter(HelicopterScenarioRun::recovered).count();
        long targetLost = this.completed.stream().filter(HelicopterScenarioRun::targetLost).count();
        long reoccluded = this.completed.stream().filter(run ->
                run.longestPostRecoveryBlockedTicks() > MAX_POST_RECOVERY_BLOCKED_TICKS).count();
        long noLosRecovery = this.completed.stream()
                .filter(run -> !this.failureReasons(run).isEmpty())
                .filter(run -> !run.recovered()).count();
        long unstable = this.completed.stream().filter(run -> run.maxPitch() > MAX_ATTITUDE
                || run.maxRoll() > MAX_ATTITUDE
                || run.unstableAttitudeRatio() > MAX_UNSTABLE_ATTITUDE_RATIO).count();
        long altitudeRunaway = this.completed.stream().filter(HelicopterScenarioRun::altitudeRunaway).count();
        return String.format(Locale.ROOT,
                "helicopter corpus failed: recovered=%d/%d noLosRecovery=%d reoccluded=%d targetLost=%d unstable=%d altitudeRunaway=%d trace=%s",
                recovered, SCENARIO_COUNT, noLosRecovery, reoccluded, targetLost, unstable, altitudeRunaway,
                this.lastFailureTraceStatus);
    }

    private String failureProgressSummary(List<HelicopterScenarioRun> failures) {
        java.util.TreeMap<String, Integer> counts = new java.util.TreeMap<>();
        for (HelicopterScenarioRun run : failures) {
            counts.merge(run.progressDiagnosis(), 1, Integer::sum);
        }
        StringBuilder builder = new StringBuilder("failureProgress");
        for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
            builder.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.append('\n').toString();
    }

    private String failureMotionSummary(List<HelicopterScenarioRun> failures) {
        java.util.TreeMap<String, Integer> counts = new java.util.TreeMap<>();
        for (HelicopterScenarioRun run : failures) {
            counts.merge(run.lateMotion(), 1, Integer::sum);
        }
        StringBuilder builder = new StringBuilder("failureMotion");
        for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
            builder.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.append('\n').toString();
    }

    private int percentileRecovery(double percentile) {
        int[] values = this.completed.stream().filter(HelicopterScenarioRun::recovered)
                .mapToInt(HelicopterScenarioRun::recoveredAt).sorted().toArray();
        if (values.length == 0) {
            return -1;
        }
        int index = Math.min(values.length - 1, (int) Math.ceil(values.length * percentile) - 1);
        return values[index];
    }

    private int percentileTargetLossTick(double percentile) {
        int[] values = this.completed.stream().filter(HelicopterScenarioRun::targetLost)
                .mapToInt(HelicopterScenarioRun::targetLostAt).sorted().toArray();
        if (values.length == 0) {
            return -1;
        }
        int index = Math.min(values.length - 1, (int) Math.ceil(values.length * percentile) - 1);
        return values[index];
    }

    private double percentileTargetLossDistance3d(double percentile) {
        double[] values = this.completed.stream().filter(HelicopterScenarioRun::targetLost)
                .mapToDouble(HelicopterScenarioRun::targetLostDistance3d).sorted().toArray();
        if (values.length == 0) {
            return Double.NaN;
        }
        int index = Math.min(values.length - 1, (int) Math.ceil(values.length * percentile) - 1);
        return values[index];
    }

    private double percentileTargetLossDistance(double percentile) {
        double[] values = this.completed.stream().filter(HelicopterScenarioRun::targetLost)
                .mapToDouble(HelicopterScenarioRun::targetLostDistance).sorted().toArray();
        if (values.length == 0) {
            return Double.NaN;
        }
        int index = Math.min(values.length - 1, (int) Math.ceil(values.length * percentile) - 1);
        return values[index];
    }

    private boolean isNearMidTargetLoss(HelicopterScenarioRun run) {
        return run.targetLost()
                && run.targetLostDistance() < DIAGNOSTIC_NEAR_MID_LOSS_DISTANCE;
    }

    private String failureReasons(HelicopterScenarioRun run) {
        List<String> reasons = new ArrayList<>();
        if (!run.valid()) {
            reasons.add("invalid");
        }
        if (!run.recovered()) {
            reasons.add("no_los_recovery");
        }
        if (run.firstMovementAt() < 0 || run.firstMovementAt() > MAX_FIRST_MOVEMENT_TICKS) {
            reasons.add("delayed_start");
        }
        if (run.averageHorizontalSpeed() < MIN_AVERAGE_HORIZONTAL_SPEED
                || run.averageTotalSpeed() < MIN_AVERAGE_TOTAL_SPEED) {
            reasons.add("slow");
        }
        if (run.longestStall() > MAX_STALL_TICKS) {
            reasons.add("long_stall");
        }
        if (run.pauseCount() > MAX_PAUSES) {
            reasons.add("repeated_pauses");
        }
        if (run.recovered() && (run.yawResponseTicks() < 0 || run.yawResponseTicks() > MAX_YAW_RESPONSE_TICKS)) {
            reasons.add("yaw_unresponsive");
        }
        if (run.recovered() && (run.yawAlignmentTicks() < 0 || run.yawAlignmentTicks() > MAX_YAW_ALIGNMENT_TICKS)) {
            reasons.add("yaw_unaligned");
        }
        if (run.collisionTicks() > 0) {
            reasons.add("collision");
        }
        if (run.longestPostRecoveryBlockedTicks() > MAX_POST_RECOVERY_BLOCKED_TICKS) {
            reasons.add("los_reocclusion");
        }
        if (run.targetLost()) {
            reasons.add("target_lost");
        }
        if (run.dismounted()) {
            reasons.add("dismounted");
        }
        if (run.wrecked()) {
            reasons.add("wrecked");
        }
        if (run.maxPitch() > MAX_ATTITUDE || run.maxRoll() > MAX_ATTITUDE
                || run.unstableAttitudeRatio() > MAX_UNSTABLE_ATTITUDE_RATIO) {
            reasons.add("unstable");
        }
        if (run.altitudeRunaway()) {
            reasons.add("altitude_runaway");
        }
        if (run.maxTargetDistance() > run.scene().allowedTargetDistance()) {
            reasons.add("range_runaway");
        }
        return String.join(",", reasons);
    }

    private boolean hasCriticalFailure(HelicopterScenarioRun run) {
        return !run.valid()
                || run.collisionTicks() > 0
                || run.longestPostRecoveryBlockedTicks() > MAX_POST_RECOVERY_BLOCKED_TICKS
                || run.targetLost()
                || run.dismounted()
                || run.wrecked()
                || run.maxPitch() > MAX_ATTITUDE
                || run.maxRoll() > MAX_ATTITUDE
                || run.unstableAttitudeRatio() > MAX_UNSTABLE_ATTITUDE_RATIO
                || run.altitudeRunaway()
                || run.maxTargetDistance() > run.scene().allowedTargetDistance();
    }

    private int severity(HelicopterScenarioRun run) {
        String reasons = this.failureReasons(run);
        int score = 0;
        if (reasons.contains("invalid")) score += 100;
        if (reasons.contains("wrecked")) score += 90;
        if (reasons.contains("collision")) score += 80;
        if (reasons.contains("dismounted")) score += 70;
        if (reasons.contains("target_lost")) score += 60;
        if (reasons.contains("los_reocclusion")) score += 55;
        if (reasons.contains("no_los_recovery")) score += 50;
        if (reasons.contains("yaw_unresponsive")) score += 45;
        if (reasons.contains("yaw_unaligned")) score += 40;
        if (reasons.contains("unstable")) score += 40;
        if (reasons.contains("range_runaway")) score += 30;
        if (reasons.contains("altitude_runaway")) score += 30;
        if (reasons.contains("long_stall")) score += 20;
        if (reasons.contains("slow")) score += 10;
        return score;
    }
}
