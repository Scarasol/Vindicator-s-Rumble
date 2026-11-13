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
public final class GroundCornerRecoveryGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();
    private static boolean previousDebug;
    private static boolean previousMobGriefing;

    @BeforeBatch(batch = "ground_corner_recovery")
    public static void prepareBatch(ServerLevel level) {
        previousDebug = CommonConfig.VEHICLE_AI_DEBUG.get();
        previousMobGriefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        CommonConfig.VEHICLE_AI_DEBUG.set(true);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
    }

    @AfterBatch(batch = "ground_corner_recovery")
    public static void restoreBatch(ServerLevel level) {
        CommonConfig.VEHICLE_AI_DEBUG.set(previousDebug);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(previousMobGriefing, level.getServer());
    }

    @GameTest(batch = "ground_corner_recovery", template = "empty", timeoutTicks = 800)
    public static void diagonalLeftPost(GameTestHelper helper) {
        verifyRecovery(helper, 45.0F, -1, false);
    }

    @GameTest(batch = "ground_corner_recovery", template = "empty", timeoutTicks = 800)
    public static void diagonalRightPost(GameTestHelper helper) {
        verifyRecovery(helper, 45.0F, 1, false);
    }

    @GameTest(batch = "ground_corner_recovery", template = "empty", timeoutTicks = 800)
    public static void loggedLeftPost(GameTestHelper helper) {
        verifyRecovery(helper, -32.9F, -1, false);
    }

    @GameTest(batch = "ground_corner_recovery", template = "empty", timeoutTicks = 800)
    public static void loggedRightPost(GameTestHelper helper) {
        verifyRecovery(helper, -32.9F, 1, false);
    }

    @GameTest(batch = "ground_corner_recovery", template = "empty", timeoutTicks = 800)
    public static void obliqueLeftPost(GameTestHelper helper) {
        verifyRecovery(helper, 22.5F, -1, false);
    }

    @GameTest(batch = "ground_corner_recovery", template = "empty", timeoutTicks = 800)
    public static void obliqueRightPost(GameTestHelper helper) {
        verifyRecovery(helper, 22.5F, 1, false);
    }

    @GameTest(batch = "ground_corner_recovery", template = "empty", timeoutTicks = 800)
    public static void northwestLeftPost(GameTestHelper helper) {
        verifyRecovery(helper, -127.0F, -1, false);
    }

    @GameTest(batch = "ground_corner_recovery", template = "empty", timeoutTicks = 800)
    public static void northwestRightPost(GameTestHelper helper) {
        verifyRecovery(helper, -127.0F, 1, false);
    }

    @GameTest(batch = "ground_corner_recovery", template = "empty", timeoutTicks = 800)
    public static void backsOutOfSouthBay(GameTestHelper helper) {
        verifyRecovery(helper, 0.0F, -1, true);
    }

    @GameTest(batch = "ground_corner_recovery", template = "empty", timeoutTicks = 800)
    public static void backsOutOfWestBay(GameTestHelper helper) {
        verifyRecovery(helper, 90.0F, 1, true);
    }

    @GameTest(batch = "ground_corner_recovery", template = "empty", timeoutTicks = 800)
    public static void backsOutOfNorthBay(GameTestHelper helper) {
        verifyRecovery(helper, 180.0F, 1, true);
    }

    @GameTest(batch = "ground_corner_recovery", template = "empty", timeoutTicks = 800)
    public static void backsOutOfEastBay(GameTestHelper helper) {
        verifyRecovery(helper, -90.0F, -1, true);
    }

    @GameTest(batch = "ground_corner_recovery", template = "empty", timeoutTicks = 800)
    public static void backsOutOfDiagonalBay(GameTestHelper helper) {
        verifyRecovery(helper, 45.0F, -1, true);
    }

    @GameTest(batch = "ground_corner_recovery", template = "empty", timeoutTicks = 800)
    public static void backsOutOfLoggedBay(GameTestHelper helper) {
        verifyRecovery(helper, -32.9F, 1, true);
    }

    @GameTest(batch = "ground_corner_recovery", template = "empty", timeoutTicks = 800)
    public static void backsOutOfObliqueBay(GameTestHelper helper) {
        verifyRecovery(helper, 22.5F, -1, true);
    }

    @GameTest(batch = "ground_corner_recovery", template = "empty", timeoutTicks = 800)
    public static void backsOutOfNorthwestBay(GameTestHelper helper) {
        verifyRecovery(helper, -127.0F, 1, true);
    }

    private static void verifyRecovery(GameTestHelper helper, float yaw, int side, boolean bay) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(512 + ARENA.getAndIncrement() * 192, 5, 3072);
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
        target.setPos(start.add(forward.scale(11.0D)));
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255, false, false));
        level.addFreshEntity(target);
        Vec3 destination = bay
                ? start.add(forward.scale(9.0D)).add(lateral.scale(side * 20.0D))
                : start.add(forward.scale(28.0D));
        long[] readyAt = {-1};
        long[] impactAt = {-1};
        long[] backedAt = {-1};
        long[] approachedAt = {-1};
        Vec3[] impact = {null};
        double[] maxBack = {0.0D};
        double[] reverseStart = {0.0D};
        int[] travelDirection = {0};
        int[] tinyShunts = {0};
        int[] collisions = {0};
        int[] contact = {0};
        int[] longestContact = {0};
        double[] minimumDistance = {Double.POSITIVE_INFINITY};
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
                    if (bay) {
                        for (int v = -5; v <= 7; v++) {
                            for (int u : new int[]{-3, 3}) {
                                pillar(level, BlockPos.containing(start.add(forward.scale(v)).add(lateral.scale(u))), false, 4);
                            }
                        }
                        for (int u = -3; u <= 3; u++) {
                            pillar(level, BlockPos.containing(start.add(forward.scale(7)).add(lateral.scale(u))), false, 4);
                        }
                    } else {
                        pillar(level, cornerPost(origin, forward, lateral, side), true, 3);
                    }
                    readyAt[0] = helper.getTick();
                }
                if (vehicle.horizontalCollision) {
                    collisions[0]++;
                    longestContact[0] = Math.max(longestContact[0], ++contact[0]);
                    if (impactAt[0] < 0) {
                        impactAt[0] = helper.getTick();
                        impact[0] = vehicle.position();
                        target.setPos(destination);
                    }
                } else {
                    contact[0] = 0;
                }
                if (impactAt[0] < 0) {
                    helper.assertTrue(helper.getTick() - readyAt[0] < 160, "Invalid fixture: never contacted obstacle");
                    return;
                }
                long elapsed = helper.getTick() - impactAt[0];
                double back = impact[0].subtract(vehicle.position()).dot(forward);
                maxBack[0] = Math.max(maxBack[0], back);
                double requiredBack = bay ? 8.0D : 1.5D;
                if (maxBack[0] >= requiredBack && backedAt[0] < 0) backedAt[0] = elapsed;
                double speed = vehicle.getDeltaMovement().dot(forward);
                int direction = speed < -0.025D ? -1 : speed > 0.025D ? 1 : 0;
                if (backedAt[0] < 0 && direction != 0 && direction != travelDirection[0]) {
                    if (direction < 0) reverseStart[0] = back;
                    else if (travelDirection[0] < 0 && back - reverseStart[0] < 1.0D) tinyShunts[0]++;
                    travelDirection[0] = direction;
                }
                double distance = vehicle.position().subtract(destination).horizontalDistance();
                minimumDistance[0] = Math.min(minimumDistance[0], distance);
                if (distance <= 8.0D && approachedAt[0] < 0) approachedAt[0] = elapsed;
                if (elapsed % 40 == 0) {
                    LogUtils.getLogger().info(
                            "Ground corner sample yaw={} side={} bay={} elapsed={} position={} speed={} collision={} back={} target={}",
                            yaw, side, bay, elapsed, vehicle.position().subtract(start),
                            vehicle.getDeltaMovement().horizontalDistance(), vehicle.horizontalCollision, back, driver.getTarget() == target);
                }
                if (elapsed < 420) return;
                LogUtils.getLogger().info(
                        "Ground corner result yaw={} side={} bay={} backedAt={} maxBack={} tinyShunts={} approachedAt={} minDistance={} collisions={} longestContact={}",
                        yaw, side, bay, backedAt[0], maxBack[0], tinyShunts[0], approachedAt[0], minimumDistance[0],
                        collisions[0], longestContact[0]);
                boolean clearedWithoutReverse = !bay && approachedAt[0] >= 0 && approachedAt[0] <= 100;
                helper.assertTrue(clearedWithoutReverse || (backedAt[0] >= 0 && backedAt[0] <= (bay ? 180 : 100)),
                        "Did not retreat promptly: maxBack=" + maxBack[0] + " backedAt=" + backedAt[0]);
                helper.assertTrue(tinyShunts[0] <= 2, "Repeated short reverse/forward shunts: " + tinyShunts[0]);
                helper.assertTrue(longestContact[0] <= 35, "Prolonged corner contact: " + longestContact[0]);
                helper.assertTrue(approachedAt[0] >= 0, "Did not resume pursuit: closest=" + minimumDistance[0]);
                close.run();
                helper.succeed();
            } catch (RuntimeException | Error failure) {
                close.run();
                throw failure;
            }
        });
    }

    private static BlockPos cornerPost(BlockPos origin, Vec3 forward, Vec3 lateral, int side) {
        BlockPos best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        double postRadius = 0.25D * (Math.abs(lateral.x) + Math.abs(lateral.z));
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                Vec3 delta = new Vec3(x, 0, z);
                double v = delta.dot(forward);
                double innerEdge = delta.dot(lateral) * side - postRadius;
                if (v < 4.5D || v > 10.5D || innerEdge < 0.97D || innerEdge > 1.2D) continue;
                double score = Math.abs(innerEdge - 1.1D) * 5.0D + Math.abs(v - 5.5D);
                if (score < bestScore) {
                    bestScore = score;
                    best = origin.offset(x, 0, z);
                }
            }
        }
        if (best == null) throw new IllegalStateException("Invalid fixture: no outer-corner post position");
        return best;
    }

    private static void pillar(ServerLevel level, BlockPos position, boolean post, int height) {
        for (int y = 0; y < height; y++) {
            level.setBlock(position.above(y),
                    (post ? Blocks.STONE_BRICK_WALL : Blocks.STONE_BRICKS).defaultBlockState(), 2);
        }
    }
}
