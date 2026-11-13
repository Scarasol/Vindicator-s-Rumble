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
public final class GroundShuntRecoveryGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();
    private static boolean previousDebug;
    private static boolean previousMobGriefing;

    @BeforeBatch(batch = "ground_shunt_recovery")
    public static void prepareBatch(ServerLevel level) {
        previousDebug = CommonConfig.VEHICLE_AI_DEBUG.get();
        previousMobGriefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        CommonConfig.VEHICLE_AI_DEBUG.set(true);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
    }

    @AfterBatch(batch = "ground_shunt_recovery")
    public static void restoreBatch(ServerLevel level) {
        CommonConfig.VEHICLE_AI_DEBUG.set(previousDebug);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(previousMobGriefing, level.getServer());
    }

    @GameTest(batch = "ground_shunt_recovery", template = "empty", timeoutTicks = 800)
    public static void southLeftEscape(GameTestHelper helper) { verify(helper, 0.0F, -1); }

    @GameTest(batch = "ground_shunt_recovery", template = "empty", timeoutTicks = 800)
    public static void southRightEscape(GameTestHelper helper) { verify(helper, 0.0F, 1); }

    @GameTest(batch = "ground_shunt_recovery", template = "empty", timeoutTicks = 800)
    public static void westLeftEscape(GameTestHelper helper) { verify(helper, 90.0F, -1); }

    @GameTest(batch = "ground_shunt_recovery", template = "empty", timeoutTicks = 800)
    public static void westRightEscape(GameTestHelper helper) { verify(helper, 90.0F, 1); }

    @GameTest(batch = "ground_shunt_recovery", template = "empty", timeoutTicks = 800)
    public static void diagonalLeftEscape(GameTestHelper helper) { verify(helper, 45.0F, -1); }

    @GameTest(batch = "ground_shunt_recovery", template = "empty", timeoutTicks = 800)
    public static void diagonalRightEscape(GameTestHelper helper) { verify(helper, 45.0F, 1); }

    @GameTest(batch = "ground_shunt_recovery", template = "empty", timeoutTicks = 800)
    public static void obliqueLeftEscape(GameTestHelper helper) { verify(helper, -32.9F, -1); }

    @GameTest(batch = "ground_shunt_recovery", template = "empty", timeoutTicks = 800)
    public static void obliqueRightEscape(GameTestHelper helper) { verify(helper, -32.9F, 1); }

    private static void verify(GameTestHelper helper, float yaw, int side) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(512 + ARENA.getAndIncrement() * 192, 5, 3456);
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
        var observation = VehicleControllers.create(vehicle).orElseThrow();
        helper.assertTrue(observation.mount(driver), "Invalid fixture: could not mount");
        Mob target = EntityType.VILLAGER.create(level);
        Vec3 initialTarget = start.add(forward.scale(11.0D)).add(0, 4.0D, 0);
        level.setBlock(BlockPos.containing(initialTarget).below(), Blocks.BEDROCK.defaultBlockState(), 2);
        target.setPos(initialTarget);
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255, false, false));
        level.addFreshEntity(target);
        Vec3 destination = start.add(forward.scale(28.0D)).add(lateral.scale(side * 2.0D)).add(0, 4.0D, 0);
        level.setBlock(BlockPos.containing(destination).below(), Blocks.BEDROCK.defaultBlockState(), 2);
        long[] readyAt = {-1};
        long[] impactAt = {-1};
        long[] escapedAt = {-1};
        long[] approachedAt = {-1};
        int[] direction = {0};
        int[] reversals = {0};
        int[] contacts = {0};
        int[] rawContacts = {0};
        int[] contactTicks = {0};
        long[] lastContactAt = {-100};
        Vec3[] lastContact = {null};
        boolean[] contactCleared = {false};
        boolean[] wasColliding = {false};
        int[] lostTargetTicks = {0};
        double[] minDistance = {Double.POSITIVE_INFINITY};
        double[] footprint = {0.0D, 0.0D};
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
                    // Two-block barriers cannot be stepped over; the elevated target stays visible.
                    for (int x = -12; x <= 12; x++) {
                        for (int z = -12; z <= 12; z++) {
                            Vec3 delta = new Vec3(x, 0, z);
                            double v = delta.dot(forward);
                            double u = delta.dot(lateral);
                            if (Math.abs(u) <= 2.5D && ((v >= 5.0D && v < 6.0D) || (v >= -6.0D && v < -5.0D))) {
                                level.setBlock(origin.offset(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                                level.setBlock(origin.offset(x, 1, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                            }
                        }
                    }
                    footprint[0] = observation.pathingDimensions().width();
                    footprint[1] = observation.pathingDimensions().length();
                    readyAt[0] = helper.getTick();
                }
                var dimensions = observation.pathingDimensions();
                helper.assertTrue(Math.abs(dimensions.width() - footprint[0]) < 0.01D
                                && Math.abs(dimensions.length() - footprint[1]) < 0.01D,
                        "Turning changed the unchanged vehicle's navigation footprint");
                if (impactAt[0] < 0) {
                    helper.assertTrue(helper.getTick() - readyAt[0] < 160, "Invalid fixture: never contacted obstacle");
                    if (!vehicle.horizontalCollision) return;
                    impactAt[0] = helper.getTick();
                    target.setPos(destination);
                    level.setBlock(BlockPos.containing(initialTarget).below(), Blocks.AIR.defaultBlockState(), 2);
                }
                long elapsed = helper.getTick() - impactAt[0];
                Vec3 delta = vehicle.position().subtract(start);
                if ((Math.abs(delta.dot(lateral)) >= 6.0D || delta.dot(forward) >= 12.0D) && escapedAt[0] < 0) {
                    escapedAt[0] = elapsed;
                }
                double distance = vehicle.position().subtract(destination).horizontalDistance();
                minDistance[0] = Math.min(minDistance[0], distance);
                if (distance <= 8.0D && approachedAt[0] < 0) approachedAt[0] = elapsed;
                if (approachedAt[0] < 0) {
                    double heading = Math.toRadians(vehicle.getYRot());
                    double speed = vehicle.getDeltaMovement().dot(new Vec3(-Math.sin(heading), 0, Math.cos(heading)));
                    int moving = speed < -0.04D ? -1 : speed > 0.04D ? 1 : 0;
                    if (moving != 0) {
                        if (direction[0] != 0 && direction[0] != moving) reversals[0]++;
                        direction[0] = moving;
                    }
                    if (vehicle.horizontalCollision) {
                        contactTicks[0]++;
                        if (!wasColliding[0]) {
                            rawContacts[0]++;
                            if (lastContact[0] == null || contactCleared[0]) {
                                contacts[0]++;
                            }
                            LogUtils.getLogger().info("Ground shunt contact yaw={} side={} elapsed={} position={} heading={} speed={}",
                                    yaw, side, elapsed, delta, vehicle.getYRot(), vehicle.getDeltaMovement().horizontalDistance());
                        }
                        lastContactAt[0] = elapsed;
                        lastContact[0] = delta;
                        contactCleared[0] = false;
                    } else if (lastContact[0] != null && elapsed - lastContactAt[0] >= 5
                            && delta.subtract(lastContact[0]).horizontalDistanceSqr() >= 1.0D) {
                        // Retain departure evidence even when the vehicle later returns to exactly the same point.
                        contactCleared[0] = true;
                    }
                    wasColliding[0] = vehicle.horizontalCollision;
                    if (driver.getTarget() != target) lostTargetTicks[0]++;
                }
                if (elapsed % 40 == 0) {
                    LogUtils.getLogger().info("Ground shunt sample yaw={} side={} elapsed={} position={} reversals={} contacts={} target={}",
                            yaw, side, elapsed, delta, reversals[0], contacts[0], driver.getTarget() == target);
                }
                if (elapsed < 420) return;
                LogUtils.getLogger().info(
                        "Ground shunt result yaw={} side={} escapedAt={} approachedAt={} reversals={} contacts={} rawContacts={} contactTicks={} lostTargetTicks={} minDistance={}",
                        yaw, side, escapedAt[0], approachedAt[0], reversals[0], contacts[0], rawContacts[0], contactTicks[0],
                        lostTargetTicks[0], minDistance[0]);
                helper.assertTrue(lostTargetTicks[0] == 0, "Lost naturally acquired visible target: " + lostTargetTicks[0]);
                helper.assertTrue(escapedAt[0] >= 0 && escapedAt[0] <= 200, "Failed to leave the pocket promptly: " + escapedAt[0]);
                helper.assertTrue(reversals[0] <= 4, "Repeated reverse/forward grinding across recovery: " + reversals[0]);
                helper.assertTrue(contacts[0] <= 4 && contactTicks[0] <= 40,
                        "Repeated contact across recovery: episodes=" + contacts[0] + " ticks=" + contactTicks[0]);
                helper.assertTrue(approachedAt[0] >= 0, "Failed to resume pursuit: closest=" + minDistance[0]);
                close.run();
                helper.succeed();
            } catch (RuntimeException | Error failure) {
                close.run();
                throw failure;
            }
        });
    }
}
