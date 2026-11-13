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
public final class GroundContinuityGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();
    private static boolean previousDebug;
    private static boolean previousMobGriefing;

    @BeforeBatch(batch = "ground_continuity")
    public static void prepareBatch(ServerLevel level) {
        previousDebug = CommonConfig.VEHICLE_AI_DEBUG.get();
        previousMobGriefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        CommonConfig.VEHICLE_AI_DEBUG.set(true);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
    }

    @AfterBatch(batch = "ground_continuity")
    public static void restoreBatch(ServerLevel level) {
        CommonConfig.VEHICLE_AI_DEBUG.set(previousDebug);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(previousMobGriefing, level.getServer());
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 550)
    public static void pickupCruisesThroughSouthLane(GameTestHelper helper) {
        verifyContinuity(helper, 0.0F, true, 0.0D);
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 550)
    public static void pickupCruisesThroughWestLane(GameTestHelper helper) {
        verifyContinuity(helper, 90.0F, true, 0.0D);
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 550)
    public static void pickupCruisesThroughNorthLane(GameTestHelper helper) {
        verifyContinuity(helper, 180.0F, true, 0.0D);
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 550)
    public static void pickupCruisesThroughEastLane(GameTestHelper helper) {
        verifyContinuity(helper, -90.0F, true, 0.0D);
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 550)
    public static void pickupCruisesTowardLeftOffsetTarget(GameTestHelper helper) {
        verifyContinuity(helper, 0.0F, true, 1.0D);
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 550)
    public static void pickupCruisesTowardRightOffsetTarget(GameTestHelper helper) {
        verifyContinuity(helper, 90.0F, true, -1.0D);
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 550)
    public static void pickupCruisesTowardNorthOffsetTarget(GameTestHelper helper) {
        verifyContinuity(helper, 180.0F, true, -1.0D);
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 550)
    public static void pickupCruisesTowardEastOffsetTarget(GameTestHelper helper) {
        verifyContinuity(helper, -90.0F, true, 1.0D);
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 550)
    public static void pickupContinuesAfterSouthTree(GameTestHelper helper) {
        verifyContinuity(helper, 0.0F, false, 0.0D);
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 550)
    public static void pickupContinuesAfterWestTree(GameTestHelper helper) {
        verifyContinuity(helper, 90.0F, false, 0.8D);
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 550)
    public static void pickupContinuesAfterDiagonalTree(GameTestHelper helper) {
        verifyContinuity(helper, 45.0F, false, -0.8D);
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 550)
    public static void pickupContinuesAfterLoggedTree(GameTestHelper helper) {
        verifyContinuity(helper, -127.0F, false, 0.0D);
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 700)
    public static void pickupPursuesLeftAfterSouthImpact(GameTestHelper helper) {
        verifyContinuity(helper, 0.0F, false, 1.0D, true);
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 700)
    public static void pickupPursuesRightAfterSouthImpact(GameTestHelper helper) {
        verifyContinuity(helper, 0.0F, false, -1.0D, true);
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 700)
    public static void pickupPursuesLeftAfterWestImpact(GameTestHelper helper) {
        verifyContinuity(helper, 90.0F, false, 1.0D, true);
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 700)
    public static void pickupPursuesRightAfterWestImpact(GameTestHelper helper) {
        verifyContinuity(helper, 90.0F, false, -1.0D, true);
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 700)
    public static void pickupPursuesLeftAfterDiagonalImpact(GameTestHelper helper) {
        verifyContinuity(helper, 45.0F, false, 1.0D, true);
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 700)
    public static void pickupPursuesRightAfterDiagonalImpact(GameTestHelper helper) {
        verifyContinuity(helper, 45.0F, false, -1.0D, true);
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 700)
    public static void pickupPursuesLeftAfterLoggedImpact(GameTestHelper helper) {
        verifyContinuity(helper, -127.0F, false, 1.0D, true);
    }

    @GameTest(batch = "ground_continuity", template = "empty", timeoutTicks = 700)
    public static void pickupPursuesRightAfterLoggedImpact(GameTestHelper helper) {
        verifyContinuity(helper, -127.0F, false, -1.0D, true);
    }

    private static void verifyContinuity(GameTestHelper helper, float yaw, boolean lane, double side) {
        verifyContinuity(helper, yaw, lane, side, false);
    }

    private static void verifyContinuity(GameTestHelper helper, float yaw, boolean lane, double side, boolean redirect) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(512 + ARENA.getAndIncrement() * 192, 5, 1536);
        List<BlockPos> chunks = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                BlockPos chunk = new BlockPos((origin.getX() >> 4) + x, 0, (origin.getZ() >> 4) + z);
                level.setChunkForced(chunk.getX(), chunk.getZ(), true);
                chunks.add(chunk);
            }
        }
        for (int x = -48; x <= 48; x++) {
            for (int z = -48; z <= 48; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
            }
        }
        Vec3 start = Vec3.atBottomCenterOf(origin);
        double radians = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        Vec3 lateral = new Vec3(forward.z, 0.0D, -forward.x);
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(
                new ResourceLocation("superbwarfare", "sodayo_pick_up_hmg"));
        Entity vehicle = type.create(level);
        vehicle.moveTo(start.x, start.y, start.z, yaw, 0.0F);
        vehicle.setInvulnerable(true);
        level.addFreshEntity(vehicle);
        Mob driver = EntityType.VINDICATOR.create(level);
        driver.moveTo(start.x, start.y, start.z, yaw, 0.0F);
        driver.setPersistenceRequired();
        driver.setInvulnerable(true);
        driver.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64.0D);
        level.addFreshEntity(driver);
        helper.assertTrue(VehicleControllers.create(vehicle).orElseThrow().mount(driver), "Invalid fixture: could not mount");
        Mob target = EntityType.VILLAGER.create(level);
        Vec3 destination = redirect
                ? start.add(forward.scale(14.0D)).add(lateral.scale(24.0D * side))
                : start.add(forward.scale(36.0D)).add(lateral.scale(lane ? side : 0.0D));
        target.setPos(redirect ? start.add(forward.scale(11.0D)) : destination);
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255, false, false));
        level.addFreshEntity(target);
        long[] readyAt = {-1};
        long[] collisionAt = {-1};
        long[] reachedAt = {-1};
        int[] collisions = {0};
        int[] pause = {0};
        int[] longestPause = {0};
        int[] slowdowns = {0};
        boolean[] cruising = {false};
        double[] minDistance = {36.0D};
        double[] speedSum = {0.0D};
        int[] speedSamples = {0};
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
                    helper.assertTrue(vehicle.position().distanceTo(start) < 1.0D, "Invalid fixture: moved before setup");
                    if (lane) {
                        for (int z = -8; z <= 44; z++) {
                            for (int x : new int[]{-4, 4}) {
                                BlockPos wall = BlockPos.containing(start.add(forward.scale(z)).add(lateral.scale(x)));
                                for (int y = 0; y < 4; y++) {
                                    level.setBlock(wall.above(y), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                                }
                            }
                        }
                    } else {
                        BlockPos trunk = BlockPos.containing(start.add(forward.scale(5.5D)).add(lateral.scale(redirect ? 0.0D : side)));
                        for (int y = 0; y < 6; y++) {
                            level.setBlock(trunk.above(y), Blocks.OAK_LOG.defaultBlockState(), 2);
                        }
                    }
                    readyAt[0] = helper.getTick();
                }
                long elapsed = helper.getTick() - readyAt[0];
                if (vehicle.horizontalCollision) {
                    collisions[0]++;
                    if (collisionAt[0] < 0) {
                        collisionAt[0] = helper.getTick();
                        if (redirect) target.setPos(destination);
                    }
                }
                if (!lane && collisionAt[0] < 0) {
                    helper.assertTrue(elapsed < 160, "Invalid fixture: never hit the tree");
                    return;
                }
                if (redirect) elapsed = helper.getTick() - collisionAt[0];
                double distance = vehicle.position().subtract(destination).horizontalDistance();
                minDistance[0] = Math.min(minDistance[0], distance);
                if (distance <= 8.0D && reachedAt[0] < 0) reachedAt[0] = elapsed;
                double speed = vehicle.getDeltaMovement().horizontalDistance();
                if (reachedAt[0] < 0 && elapsed >= 40) {
                    speedSum[0] += speed;
                    speedSamples[0]++;
                    if (speed >= 0.25D) cruising[0] = true;
                    if (speed < 0.08D) {
                        if (cruising[0]) {
                            slowdowns[0]++;
                            cruising[0] = false;
                        }
                        longestPause[0] = Math.max(longestPause[0], ++pause[0]);
                    } else {
                        pause[0] = 0;
                    }
                }
                if (elapsed < (lane ? 240 : 360)) return;
                double averageSpeed = speedSum[0] / Math.max(1, speedSamples[0]);
                LogUtils.getLogger().info(
                        "Ground continuity result yaw={} lane={} side={} redirect={} reachedAt={} minDistance={} averageSpeed={} slowdowns={} longestPause={} collisions={} targetRetained={}",
                        yaw, lane, side, redirect, reachedAt[0], minDistance[0], averageSpeed, slowdowns[0], longestPause[0],
                        collisions[0], driver.getTarget() == target);
                helper.assertTrue(reachedAt[0] >= 0, "Did not resume pursuit: closest distance=" + minDistance[0]);
                helper.assertTrue(slowdowns[0] <= (lane ? 1 : 3), "Repeated unnecessary pauses: " + slowdowns[0]);
                helper.assertTrue(longestPause[0] <= (lane ? 20 : 40), "Prolonged hesitation: " + longestPause[0]);
                if (lane) {
                    helper.assertTrue(averageSpeed >= 0.25D, "Clear lane crawled: speed=" + averageSpeed);
                    helper.assertTrue(collisions[0] == 0, "Vehicle hit an unobstructed lane wall");
                }
                close.run();
                helper.succeed();
            } catch (RuntimeException | Error failure) {
                close.run();
                throw failure;
            }
        });
    }
}
