package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.mojang.logging.LogUtils;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder("vindicators_rumble")
@PrefixGameTestTemplate(false)
public final class GroundConvergenceGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();
    private static boolean previousDebug;
    private static boolean previousMobGriefing;

    @BeforeBatch(batch = "ground_convergence")
    public static void prepareBatch(ServerLevel level) {
        previousDebug = CommonConfig.VEHICLE_AI_DEBUG.get();
        previousMobGriefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        CommonConfig.VEHICLE_AI_DEBUG.set(Boolean.getBoolean("vindicatorsrumble.groundTrace"));
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
    }

    @AfterBatch(batch = "ground_convergence")
    public static void restoreBatch(ServerLevel level) {
        CommonConfig.VEHICLE_AI_DEBUG.set(previousDebug);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(previousMobGriefing, level.getServer());
    }

    @GameTest(batch = "ground_convergence", template = "empty", timeoutTicks = 1000)
    public static void southLeft(GameTestHelper helper) { verify(helper, 0, -1); }
    @GameTest(batch = "ground_convergence", template = "empty", timeoutTicks = 1000)
    public static void southRight(GameTestHelper helper) { verify(helper, 0, 1); }
    @GameTest(batch = "ground_convergence", template = "empty", timeoutTicks = 1000)
    public static void westLeft(GameTestHelper helper) { verify(helper, 90, -1); }
    @GameTest(batch = "ground_convergence", template = "empty", timeoutTicks = 1000)
    public static void westRight(GameTestHelper helper) { verify(helper, 90, 1); }
    @GameTest(batch = "ground_convergence", template = "empty", timeoutTicks = 1000)
    public static void northLeft(GameTestHelper helper) { verify(helper, 180, -1); }
    @GameTest(batch = "ground_convergence", template = "empty", timeoutTicks = 1000)
    public static void northRight(GameTestHelper helper) { verify(helper, 180, 1); }
    @GameTest(batch = "ground_convergence", template = "empty", timeoutTicks = 1000)
    public static void eastLeft(GameTestHelper helper) { verify(helper, -90, -1); }
    @GameTest(batch = "ground_convergence", template = "empty", timeoutTicks = 1000)
    public static void eastRight(GameTestHelper helper) { verify(helper, -90, 1); }

    private static void verify(GameTestHelper helper, float yaw, int side) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(512 + ARENA.getAndIncrement() * 192, 5, 6528);
        List<BlockPos> chunks = new ArrayList<>();
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                BlockPos chunk = new BlockPos((origin.getX() >> 4) + x, 0, (origin.getZ() >> 4) + z);
                level.setChunkForced(chunk.getX(), chunk.getZ(), true);
                chunks.add(chunk);
            }
        }
        for (int x = -72; x <= 72; x++) {
            for (int z = -72; z <= 72; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
            }
        }
        Vec3 start = Vec3.atBottomCenterOf(origin);
        double radians = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(radians), 0, Math.cos(radians));
        Vec3 lateral = new Vec3(forward.z, 0, -forward.x);
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(
                new ResourceLocation("superbwarfare", "sodayo_pick_up_hmg"));
        Entity vehicle = type.create(level);
        vehicle.moveTo(start.x, start.y, start.z, yaw, 0);
        vehicle.setInvulnerable(true);
        level.addFreshEntity(vehicle);
        Mob driver = EntityType.VINDICATOR.create(level);
        driver.moveTo(start.x, start.y, start.z, yaw, 0);
        driver.setPersistenceRequired();
        driver.setInvulnerable(true);
        driver.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64);
        level.addFreshEntity(driver);
        helper.assertTrue(VehicleControllers.create(vehicle).orElseThrow().mount(driver),
                "Invalid fixture: could not mount");
        Mob target = EntityType.VILLAGER.create(level);
        Vec3 acquisition = start.add(forward.scale(11)).add(0, 4, 0);
        Vec3 destination = start.add(forward.scale(28)).add(lateral.scale(side * 2)).add(0, 4, 0);
        level.setBlock(BlockPos.containing(acquisition).below(), Blocks.BEDROCK.defaultBlockState(), 2);
        level.setBlock(BlockPos.containing(destination).below(), Blocks.BEDROCK.defaultBlockState(), 2);
        target.setPos(acquisition);
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255, false, false));
        level.addFreshEntity(target);
        long[] readyAt = {-1};
        long[] impactAt = {-1};
        long[] approachedAt = {-1};
        double[] closest = {Double.POSITIVE_INFINITY};
        double[] lateClosest = {Double.POSITIVE_INFINITY};
        double[] farthest = {0};
        int[] contacts = {0};
        int[] lostTarget = {0};
        Runnable close = () -> {
            driver.discard();
            target.discard();
            vehicle.discard();
            chunks.forEach(chunk -> level.setChunkForced(chunk.getX(), chunk.getZ(), false));
        };
        helper.onEachTick(() -> {
            try {
                helper.assertTrue(vehicle.isAlive() && driver.getVehicle() == vehicle, "Lost driver or vehicle");
                if (readyAt[0] < 0) {
                    helper.assertTrue(helper.getTick() < 120, "Invalid fixture: no natural target acquisition");
                    if (driver.getTarget() != target) return;
                    // Chunk preparation in another fixture can run the server before this callback.
                    vehicle.moveTo(start.x, start.y, start.z, yaw, 0);
                    vehicle.setDeltaMovement(Vec3.ZERO);
                    for (int z = -16; z <= 40; z++) {
                        for (int x : new int[]{-9, 9}) placeWall(level, start, forward, lateral, x, z);
                    }
                    for (int x = -3; x <= 3; x++) {
                        placeWall(level, start, forward, lateral, x, 6);
                        placeWall(level, start, forward, lateral, x, -5);
                    }
                    readyAt[0] = helper.getTick();
                }
                if (impactAt[0] < 0) {
                    helper.assertTrue(helper.getTick() - readyAt[0] < 160, "Invalid fixture: never contacted barrier");
                    if (!vehicle.horizontalCollision) return;
                    impactAt[0] = helper.getTick();
                    target.setPos(destination);
                    level.setBlock(BlockPos.containing(acquisition).below(), Blocks.AIR.defaultBlockState(), 2);
                }
                long elapsed = helper.getTick() - impactAt[0];
                double distance = vehicle.position().subtract(destination).horizontalDistance();
                closest[0] = Math.min(closest[0], distance);
                farthest[0] = Math.max(farthest[0], distance);
                if (distance <= 10 && approachedAt[0] < 0) approachedAt[0] = elapsed;
                if (elapsed >= 500) lateClosest[0] = Math.min(lateClosest[0], distance);
                if (vehicle.horizontalCollision) contacts[0]++;
                if (driver.getTarget() != target) lostTarget[0]++;
                if (elapsed % 100 == 0) {
                    LogUtils.getLogger().info("Ground convergence sample yaw={} side={} tick={} delta={} distance={} speed={} contacts={}",
                            yaw, side, elapsed, vehicle.position().subtract(start), distance,
                            vehicle.getDeltaMovement().horizontalDistance(), contacts[0]);
                }
                if (elapsed < 700) return;
                LogUtils.getLogger().info("Ground convergence result yaw={} side={} approachedAt={} closest={} lateClosest={} farthest={} endDistance={} contacts={} lostTarget={}",
                        yaw, side, approachedAt[0], closest[0], lateClosest[0], farthest[0], distance, contacts[0], lostTarget[0]);
                helper.assertTrue(approachedAt[0] >= 0 && approachedAt[0] <= 450,
                        "Did not converge after escape: closest=" + closest[0] + " approach=" + approachedAt[0]);
                helper.assertTrue(lateClosest[0] <= 10, "Pursuit drifted away after initial approach: " + lateClosest[0]);
                helper.assertTrue(farthest[0] <= 48, "Unbounded detour from stationary target: " + farthest[0]);
                helper.assertTrue(lostTarget[0] == 0, "Lost naturally acquired target during detour: " + lostTarget[0]);
                close.run();
                helper.succeed();
            } catch (RuntimeException | Error failure) {
                close.run();
                throw failure;
            }
        });
    }

    private static void placeWall(ServerLevel level, Vec3 start, Vec3 forward, Vec3 lateral, int x, int z) {
        BlockPos pos = BlockPos.containing(start.add(forward.scale(z)).add(lateral.scale(x)));
        for (int y = 0; y < 2; y++) level.setBlock(pos.above(y), Blocks.STONE_BRICKS.defaultBlockState(), 2);
    }
}
