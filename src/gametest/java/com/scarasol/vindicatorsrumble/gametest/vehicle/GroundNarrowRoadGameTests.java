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
public final class GroundNarrowRoadGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();
    private static boolean previousDebug;
    private static boolean previousMobGriefing;

    @BeforeBatch(batch = "ground_narrow_road")
    public static void prepareBatch(ServerLevel level) {
        previousDebug = CommonConfig.VEHICLE_AI_DEBUG.get();
        previousMobGriefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        CommonConfig.VEHICLE_AI_DEBUG.set(true);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
    }

    @AfterBatch(batch = "ground_narrow_road")
    public static void restoreBatch(ServerLevel level) {
        CommonConfig.VEHICLE_AI_DEBUG.set(previousDebug);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(previousMobGriefing, level.getServer());
    }

    @GameTest(batch = "ground_narrow_road", template = "empty", timeoutTicks = 650)
    public static void threeWideSouth(GameTestHelper helper) { verify(helper, 0, 3, false); }
    @GameTest(batch = "ground_narrow_road", template = "empty", timeoutTicks = 650)
    public static void threeWideWest(GameTestHelper helper) { verify(helper, 90, 3, false); }
    @GameTest(batch = "ground_narrow_road", template = "empty", timeoutTicks = 650)
    public static void threeWideNorth(GameTestHelper helper) { verify(helper, 180, 3, false); }
    @GameTest(batch = "ground_narrow_road", template = "empty", timeoutTicks = 650)
    public static void threeWideEast(GameTestHelper helper) { verify(helper, -90, 3, false); }
    @GameTest(batch = "ground_narrow_road", template = "empty", timeoutTicks = 650)
    public static void fiveWideSouth(GameTestHelper helper) { verify(helper, 0, 5, false); }
    @GameTest(batch = "ground_narrow_road", template = "empty", timeoutTicks = 650)
    public static void fiveWideWest(GameTestHelper helper) { verify(helper, 90, 5, false); }
    @GameTest(batch = "ground_narrow_road", template = "empty", timeoutTicks = 650)
    public static void fiveWideNorth(GameTestHelper helper) { verify(helper, 180, 5, false); }
    @GameTest(batch = "ground_narrow_road", template = "empty", timeoutTicks = 650)
    public static void fiveWideEast(GameTestHelper helper) { verify(helper, -90, 5, false); }
    @GameTest(batch = "ground_narrow_road", template = "empty", timeoutTicks = 650)
    public static void rearWallSouth(GameTestHelper helper) { verify(helper, 0, 0, true); }
    @GameTest(batch = "ground_narrow_road", template = "empty", timeoutTicks = 650)
    public static void rearWallWest(GameTestHelper helper) { verify(helper, 90, 0, true); }
    @GameTest(batch = "ground_narrow_road", template = "empty", timeoutTicks = 650)
    public static void rearWallNorth(GameTestHelper helper) { verify(helper, 180, 0, true); }
    @GameTest(batch = "ground_narrow_road", template = "empty", timeoutTicks = 650)
    public static void rearWallEast(GameTestHelper helper) { verify(helper, -90, 0, true); }

    @GameTest(batch = "ground_narrow_road", template = "empty", timeoutTicks = 650)
    public static void narrowExitSouth(GameTestHelper helper) { verify(helper, 0, 3, false, true); }
    @GameTest(batch = "ground_narrow_road", template = "empty", timeoutTicks = 650)
    public static void narrowExitWest(GameTestHelper helper) { verify(helper, 90, 3, false, true); }
    @GameTest(batch = "ground_narrow_road", template = "empty", timeoutTicks = 650)
    public static void narrowExitNorth(GameTestHelper helper) { verify(helper, 180, 3, false, true); }
    @GameTest(batch = "ground_narrow_road", template = "empty", timeoutTicks = 650)
    public static void narrowExitEast(GameTestHelper helper) { verify(helper, -90, 3, false, true); }

    private static void verify(GameTestHelper helper, float yaw, int roadWidth, boolean rearWall) {
        verify(helper, yaw, roadWidth, rearWall, false);
    }

    private static void verify(GameTestHelper helper, float yaw, int roadWidth, boolean rearWall, boolean exit) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(512 + ARENA.getAndIncrement() * 160, 5, 4992);
        List<BlockPos> chunks = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                BlockPos chunk = new BlockPos((origin.getX() >> 4) + x, 0, (origin.getZ() >> 4) + z);
                level.setChunkForced(chunk.getX(), chunk.getZ(), true);
                chunks.add(chunk);
            }
        }
        for (int x = -64; x <= 64; x++) {
            for (int z = -64; z <= 64; z++) {
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
        helper.assertTrue(VehicleControllers.create(vehicle).orElseThrow().mount(driver), "Invalid fixture: could not mount");
        Mob target = EntityType.VILLAGER.create(level);
        Vec3 destination = rearWall ? start.add(forward.scale(3)).add(lateral.scale(5))
                : exit ? start.add(forward.scale(34)).add(lateral.scale(10)).add(0, 4, 0)
                : start.add(forward.scale(36));
        if (exit) level.setBlock(BlockPos.containing(destination).below(), Blocks.BEDROCK.defaultBlockState(), 2);
        target.setPos(rearWall || exit ? start.add(forward.scale(10)) : destination);
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255, false, false));
        level.addFreshEntity(target);
        long[] readyAt = {-1};
        long[] reachedAt = {-1};
        int[] lastDirection = {0};
        int[] reversals = {0};
        int[] contacts = {0};
        int[] contactTicks = {0};
        int[] pause = {0};
        int[] longestPause = {0};
        boolean[] wasColliding = {false};
        double[] closest = {Double.POSITIVE_INFINITY};
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
                    helper.assertTrue(helper.getTick() < 120, "Invalid fixture: no natural hostile target");
                    if (driver.getTarget() != target) return;
                    helper.assertTrue(vehicle.position().distanceTo(start) < 1, "Invalid fixture: moved before setup");
                    if (rearWall) {
                        for (int x = -4; x <= 4; x++) {
                            placeWall(level, BlockPos.containing(start.add(forward.scale(-4)).add(lateral.scale(x))));
                        }
                        target.setPos(destination);
                    } else {
                        int edge = (roadWidth + 1) / 2;
                        for (int z = -10; z <= (exit ? 22 : 44); z++) {
                            for (int x : new int[]{-edge, edge}) {
                                BlockPos wall = BlockPos.containing(start.add(forward.scale(z)).add(lateral.scale(x)));
                                if (exit) {
                                    for (int y = 0; y < 2; y++) level.setBlock(wall.above(y), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                                } else {
                                    placeWall(level, wall);
                                }
                            }
                        }
                        if (exit) target.setPos(destination);
                    }
                    readyAt[0] = helper.getTick();
                }
                long elapsed = helper.getTick() - readyAt[0];
                double distance = vehicle.position().subtract(destination).horizontalDistance();
                closest[0] = Math.min(closest[0], distance);
                boolean reached = rearWall ? distance <= 3 : exit ? distance <= 8
                        : vehicle.position().subtract(start).dot(forward) >= 28;
                if (reached && reachedAt[0] < 0) reachedAt[0] = elapsed;
                if (reachedAt[0] < 0) {
                    if (exit && vehicle.position().subtract(start).dot(forward) < 22) {
                        helper.assertTrue(Math.abs(vehicle.position().subtract(start).dot(lateral)) <= roadWidth * 0.5D
                                        && vehicle.getY() - start.y < 1.0D,
                                "Cut across or climbed over the narrow-road wall");
                    }
                    helper.assertTrue(driver.getTarget() == target, "Lost naturally acquired visible target");
                    double heading = Math.toRadians(vehicle.getYRot());
                    double speed = vehicle.getDeltaMovement().dot(new Vec3(-Math.sin(heading), 0, Math.cos(heading)));
                    int direction = speed > 0.04D ? 1 : speed < -0.04D ? -1 : 0;
                    if (direction != 0) {
                        if (lastDirection[0] != 0 && direction != lastDirection[0]) reversals[0]++;
                        lastDirection[0] = direction;
                    }
                    if (vehicle.horizontalCollision) {
                        contactTicks[0]++;
                        if (!wasColliding[0]) contacts[0]++;
                    }
                    wasColliding[0] = vehicle.horizontalCollision;
                    if (elapsed >= 40 && vehicle.getDeltaMovement().horizontalDistance() < 0.08D) {
                        longestPause[0] = Math.max(longestPause[0], ++pause[0]);
                    } else {
                        pause[0] = 0;
                    }
                }
                if (elapsed % 60 == 0) {
                    LogUtils.getLogger().info("Ground narrow sample yaw={} width={} rearWall={} exit={} tick={} delta={} heading={} speed={} contacts={} reversals={}",
                            yaw, roadWidth, rearWall, exit, elapsed, vehicle.position().subtract(start), vehicle.getYRot(),
                            vehicle.getDeltaMovement().horizontalDistance(), contacts[0], reversals[0]);
                }
                if (elapsed < (rearWall || exit ? 360 : 180)) return;
                LogUtils.getLogger().info("Ground narrow result yaw={} width={} rearWall={} exit={} reachedAt={} closest={} contacts={} contactTicks={} reversals={} longestPause={}",
                        yaw, roadWidth, rearWall, exit, reachedAt[0], closest[0], contacts[0], contactTicks[0], reversals[0], longestPause[0]);
                helper.assertTrue(reachedAt[0] >= 0, "Failed to progress out of local area: closest=" + closest[0]);
                helper.assertTrue(reversals[0] <= (rearWall || exit ? 4 : 1), "Repeated shunting: " + reversals[0]);
                helper.assertTrue(contacts[0] <= (rearWall || exit ? 3 : 0)
                                && contactTicks[0] <= (rearWall || exit ? 30 : 0),
                        "Repeated wall contact: episodes=" + contacts[0] + " ticks=" + contactTicks[0]);
                helper.assertTrue(longestPause[0] <= (rearWall || exit ? 40 : 20), "Prolonged local stall: " + longestPause[0]);
                close.run();
                helper.succeed();
            } catch (RuntimeException | Error failure) {
                close.run();
                throw failure;
            }
        });
    }

    private static void placeWall(ServerLevel level, BlockPos pos) {
        for (int y = 0; y < 3; y++) level.setBlock(pos.above(y), Blocks.STONE_BRICKS.defaultBlockState(), 2);
    }
}
