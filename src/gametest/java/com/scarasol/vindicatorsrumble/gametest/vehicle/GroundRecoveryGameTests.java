package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.mojang.logging.LogUtils;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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
public final class GroundRecoveryGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();
    private static boolean previousDebug;
    private static boolean previousMobGriefing;

    @BeforeBatch(batch = "ground_recovery")
    public static void prepareRecoveryBatch(ServerLevel level) {
        previousDebug = CommonConfig.VEHICLE_AI_DEBUG.get();
        previousMobGriefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        CommonConfig.VEHICLE_AI_DEBUG.set(true);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
    }

    @AfterBatch(batch = "ground_recovery")
    public static void restoreRecoveryBatch(ServerLevel level) {
        CommonConfig.VEHICLE_AI_DEBUG.set(previousDebug);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(previousMobGriefing, level.getServer());
    }

    @GameTest(batch = "ground_recovery", template = "empty", timeoutTicks = 650)
    public static void pickupBacksOutOfTreeFacingSouth(GameTestHelper helper) {
        verifyTreeRecovery(helper, "sodayo_pick_up_hmg", 0.0F, 0.0D);
    }

    @GameTest(batch = "ground_recovery", template = "empty", timeoutTicks = 650)
    public static void pickupBacksOutOfTreeFacingWest(GameTestHelper helper) {
        verifyTreeRecovery(helper, "sodayo_pick_up_hmg", 90.0F, 0.0D);
    }

    @GameTest(batch = "ground_recovery", template = "empty", timeoutTicks = 650)
    public static void pickupBacksOutOfTreeDiagonally(GameTestHelper helper) {
        verifyTreeRecovery(helper, "sodayo_pick_up_hmg", 45.0F, 0.0D);
    }

    @GameTest(batch = "ground_recovery", template = "empty", timeoutTicks = 650)
    public static void pickupBacksOutOfTreeAtLoggedHeading(GameTestHelper helper) {
        verifyTreeRecovery(helper, "sodayo_pick_up_hmg", -127.0F, 0.0D);
    }

    @GameTest(batch = "ground_recovery", template = "empty", timeoutTicks = 650)
    public static void pickupBacksOutOfLeftCornerTree(GameTestHelper helper) {
        verifyTreeRecovery(helper, "sodayo_pick_up_hmg", 0.0F, 0.8D);
    }

    @GameTest(batch = "ground_recovery", template = "empty", timeoutTicks = 650)
    public static void pickupBacksOutOfRightCornerTree(GameTestHelper helper) {
        verifyTreeRecovery(helper, "sodayo_pick_up_hmg", 0.0F, -0.8D);
    }

    @GameTest(batch = "ground_recovery", template = "empty", timeoutTicks = 650)
    public static void pickupBacksOutOfDiagonalLeftCornerTree(GameTestHelper helper) {
        verifyTreeRecovery(helper, "sodayo_pick_up_hmg", -127.0F, 0.8D);
    }

    @GameTest(batch = "ground_recovery", template = "empty", timeoutTicks = 650)
    public static void pickupBacksOutOfDiagonalRightCornerTree(GameTestHelper helper) {
        verifyTreeRecovery(helper, "sodayo_pick_up_hmg", -127.0F, -0.8D);
    }

    private static void verifyTreeRecovery(GameTestHelper helper, String vehicleId, float yaw, double side) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(512 + ARENA.getAndIncrement() * 160, 5, 512);
        List<BlockPos> chunks = new ArrayList<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                BlockPos chunk = new BlockPos((origin.getX() >> 4) + x, 0, (origin.getZ() >> 4) + z);
                level.setChunkForced(chunk.getX(), chunk.getZ(), true);
                chunks.add(chunk);
            }
        }
        for (int x = -32; x <= 32; x++) {
            for (int z = -32; z <= 32; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
            }
        }
        Vec3 start = Vec3.atBottomCenterOf(origin);
        double radians = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        Vec3 lateral = new Vec3(forward.z, 0.0D, -forward.x);
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("superbwarfare", vehicleId));
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
        helper.assertTrue(VehicleControllers.create(vehicle).orElseThrow().mount(driver), "Could not mount tree fixture");
        Mob target = EntityType.VILLAGER.create(level);
        Vec3 targetPosition = start.add(forward.scale(11.0D));
        target.setPos(targetPosition);
        target.setNoAi(true);
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255, false, false));
        target.setPersistenceRequired();
        level.addFreshEntity(target);
        BlockPos trunk = BlockPos.containing(start.add(forward.scale(5.5D)).add(lateral.scale(side)));
        long[] insertedAt = {-1};
        long[] collisionAt = {-1};
        Vec3[] impact = {null};
        double[] maxBack = {0.0D};
        int[] collisionTicks = {0};
        int[] longestCollision = {0};
        int[] currentCollision = {0};
        double[] traveled = {0.0D};
        Vec3[] previous = {vehicle.position()};
        Runnable close = () -> {
            driver.discard();
            target.discard();
            vehicle.discard();
            chunks.forEach(chunk -> level.setChunkForced(chunk.getX(), chunk.getZ(), false));
        };
        helper.onEachTick(() -> {
            try {
                helper.assertTrue(vehicle.isAlive() && driver.getVehicle() == vehicle, "Tree recovery lost its driver or vehicle");
                if (insertedAt[0] < 0) {
                    helper.assertTrue(helper.getTick() < 120, "Invalid fixture: no natural target acquisition");
                    if (driver.getTarget() != target) return;
                    helper.assertTrue(vehicle.position().distanceTo(start) < 1.0D, "Invalid fixture: driver moved before tree insertion");
                    for (int y = 0; y < 6; y++) {
                        level.setBlock(trunk.above(y), Blocks.OAK_LOG.defaultBlockState(), 2);
                    }
                    insertedAt[0] = helper.getTick();
                }
                traveled[0] += vehicle.position().subtract(previous[0]).horizontalDistance();
                previous[0] = vehicle.position();
                if (vehicle.horizontalCollision) {
                    if (collisionAt[0] < 0) {
                        collisionAt[0] = helper.getTick();
                        impact[0] = vehicle.position();
                    }
                    collisionTicks[0]++;
                    longestCollision[0] = Math.max(longestCollision[0], ++currentCollision[0]);
                } else {
                    currentCollision[0] = 0;
                }
                if (collisionAt[0] < 0) {
                    helper.assertTrue(helper.getTick() - insertedAt[0] < 160, "Invalid fixture: vehicle never struck the tree");
                    return;
                }
                long elapsed = helper.getTick() - collisionAt[0];
                maxBack[0] = Math.max(maxBack[0], impact[0].subtract(vehicle.position()).dot(forward));
                if (elapsed % 20 == 0) {
                    LogUtils.getLogger().info(
                            "Ground tree recovery vehicle={} yaw={} side={} elapsed={} pos={} speed={} collision={} maxBack={} traveled={} target={}",
                            vehicleId, yaw, side, elapsed, vehicle.position().subtract(start),
                            vehicle.getDeltaMovement().horizontalDistance(), vehicle.horizontalCollision,
                            maxBack[0], traveled[0], driver.getTarget() == target);
                }
                if (elapsed < 300) return;
                LogUtils.getLogger().info(
                        "Ground tree result vehicle={} yaw={} side={} maxBack={} traveled={} collisionTicks={} longestCollision={} finalYaw={}",
                        vehicleId, yaw, side, maxBack[0], traveled[0], collisionTicks[0], longestCollision[0],
                        Mth.wrapDegrees(vehicle.getYRot() - yaw));
                helper.assertTrue(maxBack[0] >= 1.5D, "Vehicle did not reverse at least 1.5 blocks away from the tree");
                helper.assertTrue(longestCollision[0] < 100, "Vehicle remained pressed against the tree for five seconds");
                close.run();
                helper.succeed();
            } catch (RuntimeException | Error failure) {
                close.run();
                throw failure;
            }
        });
    }
}
