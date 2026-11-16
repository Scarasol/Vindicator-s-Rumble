package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.mojang.logging.LogUtils;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwGameTestVehicle;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.gametest.fixture.VehicleTrackingFixture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@GameTestHolder("vindicators_rumble")
@PrefixGameTestTemplate(false)
public final class GroundMotionRegressionGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();
    private static final List<Runnable> CLEANUP = new ArrayList<>();
    private static boolean previousDebug;
    private static boolean previousEnabled;
    private static boolean previousAmmo;
    private static boolean previousGriefing;
    private static double previousNatural;

    @BeforeBatch(batch = "ground_motion_regression")
    public static void prepare(ServerLevel level) {
        previousDebug = CommonConfig.VEHICLE_AI_DEBUG.get();
        previousEnabled = CommonConfig.ENABLE_VEHICLE_AI.get();
        previousAmmo = CommonConfig.VEHICLE_AI_NEEDS_AMMO.get();
        previousNatural = CommonConfig.NATURAL_VEHICLE_CHANCE.get();
        previousGriefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        CommonConfig.VEHICLE_AI_DEBUG.set(true);
        CommonConfig.ENABLE_VEHICLE_AI.set(true);
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(false);
        CommonConfig.NATURAL_VEHICLE_CHANCE.set(0.0D);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
    }

    @AfterBatch(batch = "ground_motion_regression")
    public static void restore(ServerLevel level) {
        List.copyOf(CLEANUP).forEach(Runnable::run);
        CLEANUP.clear();
        CommonConfig.VEHICLE_AI_DEBUG.set(previousDebug);
        CommonConfig.ENABLE_VEHICLE_AI.set(previousEnabled);
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(previousAmmo);
        CommonConfig.NATURAL_VEHICLE_CHANCE.set(previousNatural);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(previousGriefing, level.getServer());
    }

    @GameTest(batch = "ground_motion_regression", template = "empty", timeoutTicks = 420)
    public static void tankEngagesGroundLevelLeftFlank(GameTestHelper helper) {
        verifyTank(helper, 0.0F, -1, false);
    }

    @GameTest(batch = "ground_motion_regression", template = "empty", timeoutTicks = 420)
    public static void tankEngagesGroundLevelRightFlank(GameTestHelper helper) {
        verifyTank(helper, 90.0F, 1, false);
    }

    @GameTest(batch = "ground_motion_regression", template = "empty", timeoutTicks = 420)
    public static void tankEngagesMovingGroundLevelLeftFlank(GameTestHelper helper) {
        verifyTank(helper, -127.0F, -1, true);
    }

    @GameTest(batch = "ground_motion_regression", template = "empty", timeoutTicks = 420)
    public static void tankEngagesMovingGroundLevelRightFlank(GameTestHelper helper) {
        verifyTank(helper, 45.0F, 1, true);
    }

    @GameTest(batch = "ground_motion_regression", template = "empty", timeoutTicks = 420)
    public static void tankEngagesGroundLevelRearSouth(GameTestHelper helper) {
        verifyTank(helper, 0.0F, 0, false);
    }

    @GameTest(batch = "ground_motion_regression", template = "empty", timeoutTicks = 420)
    public static void tankEngagesGroundLevelRearOblique(GameTestHelper helper) {
        verifyTank(helper, -127.0F, 0, false);
    }

    @GameTest(batch = "ground_motion_regression", template = "empty", timeoutTicks = 420)
    public static void tankEngagesCloseInfantryAfterTrackingRecovery(GameTestHelper helper) {
        verifyTank(helper, 0.0F, -1, false, true);
    }

    @GameTest(batch = "ground_motion_regression", template = "empty", timeoutTicks = 420)
    public static void tankEngagesMovingInfantryAfterTrackingRecovery(GameTestHelper helper) {
        verifyTank(helper, 90.0F, 1, true, true);
    }

    private static void verifyTank(GameTestHelper helper, float yaw, int side, boolean moving) {
        verifyTank(helper, yaw, side, moving, false);
    }

    private static void verifyTank(GameTestHelper helper, float yaw, int side, boolean moving, boolean trackingRoundTrip) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = arena(level);
        Vec3 start = Vec3.atBottomCenterOf(origin);
        double radians = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        Vec3 lateral = new Vec3(forward.z, 0.0D, -forward.x);
        Entity vehicle = vehicle(level, "yx_100", start, yaw);
        Mob driver = crew(level, start);
        Mob target = EntityType.VILLAGER.create(level);
        target.moveTo(start.add(forward.scale(30.0D)));
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                Integer.MAX_VALUE, 255, false, false));
        level.addFreshEntity(target);
        List<Entity> shots = new ArrayList<>();
        Consumer<EntityJoinLevelEvent> observer = event -> {
            if (event.getEntity() instanceof Projectile shot && shot.getOwner() == driver) shots.add(shot);
        };
        MinecraftForge.EVENT_BUS.addListener(observer);
        Runnable close = () -> {
            MinecraftForge.EVENT_BUS.unregister(observer);
            shots.forEach(Entity::discard);
            target.discard();
            driver.discard();
            vehicle.discard();
        };
        CLEANUP.add(close);
        long[] readyAt = {-1};
        int[] reverse = {0};
        int[] longestReverse = {0};
        double[] farthest = {0.0D};
        double[] farthestTarget = {0.0D};
        boolean[] engaged = {false};
        helper.onEachTick(() -> {
            if (readyAt[0] < 0) {
                helper.assertTrue(helper.getTick() < 120, "Invalid fixture: no natural target acquisition");
                if (driver.getTarget() != target) return;
                target.moveTo(start.add(side == 0 ? forward.scale(-5.5D) : lateral.scale(side * 5.5D)));
                level.addFreshEntity(vehicle);
                helper.assertTrue(VehicleControllers.create(vehicle).orElseThrow().mount(driver),
                        "Invalid fixture: driver failed to mount");
                if (trackingRoundTrip) {
                    VehicleTrackingFixture.roundTrip(level, driver, () -> {
                        helper.assertTrue(!driver.isRemoved(), "Tracking fixture removed the driver");
                    });
                }
                readyAt[0] = helper.getTick();
            }
            long elapsed = helper.getTick() - readyAt[0];
            if (moving && elapsed < 100) {
                target.moveTo(start.add(lateral.scale(side * 5.5D))
                        .add(forward.scale(-0.07D * elapsed)));
                target.setDeltaMovement(Vec3.ZERO);
            }
            helper.assertTrue(vehicle.isAlive() && driver.getVehicle() == vehicle, "Lost driver or vehicle");
            helper.assertTrue(driver.getTarget() == target, "Lost naturally acquired target");
            var input = SbwGameTestVehicle.flightControl(vehicle);
            longestReverse[0] = Math.max(longestReverse[0], reverse[0] = input.back() ? reverse[0] + 1 : 0);
            farthest[0] = Math.max(farthest[0], vehicle.position().subtract(start).horizontalDistance());
            farthestTarget[0] = Math.max(farthestTarget[0],
                    vehicle.position().subtract(target.position()).horizontalDistance());
            engaged[0] |= vehicle.getBoundingBox().intersects(target.getBoundingBox()) || !shots.isEmpty();
            if (elapsed < 140) return;
            LogUtils.getLogger().info(
                    "Ground motion tank result vehicle={} yaw={} side={} moving={} reverse={} travel={} separation={} engaged={}",
                    vehicle.getId(), yaw, side, moving, longestReverse[0], farthest[0], farthestTarget[0], engaged[0]);
            helper.assertTrue(longestReverse[0] <= 40,
                    "Tank kept backing instead of engaging: reverseTicks=" + longestReverse[0]);
            helper.assertTrue(engaged[0], "Tank never engaged close ground-level infantry");
            helper.assertTrue(farthestTarget[0] <= 16.0D, "Tank drove away from its close infantry target");
            close.run();
            CLEANUP.remove(close);
            helper.succeed();
        });
    }

    @GameTest(batch = "ground_motion_regression", template = "empty", timeoutTicks = 650)
    public static void pickupCompletesLoggedVillageNavigationWithoutShunting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = arena(level);
        try (var stream = GroundMotionRegressionGameTests.class.getResourceAsStream(
                "/data/vindicators_rumble/fixtures/logged_pickup_terrain.nbt")) {
            if (stream == null) throw new IOException("Missing logged terrain");
            var blocks = NbtIo.readCompressed(stream).getList("blocks", 10);
            var lookup = level.holderLookup(Registries.BLOCK);
            for (var value : blocks) {
                CompoundTag block = (CompoundTag) value;
                int[] pos = block.getIntArray("pos");
                level.setBlock(origin.offset(pos[0], pos[1], pos[2]),
                        NbtUtils.readBlockState(lookup, block.getCompound("state")), 2);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Invalid fixture: could not load logged terrain", failure);
        }
        Vec3 start = Vec3.atLowerCornerOf(origin).add(0.7445271205702D, 0.0451400140769D, 0.9143167362503D);
        Vec3 destination = Vec3.atLowerCornerOf(origin).add(5.5D, 0.0D, -7.5D);
        Entity vehicle = vehicle(level, "sodayo_pick_up_hmg", start, 108.0F);
        level.addFreshEntity(vehicle);
        Mob driver = crew(level, start);
        helper.assertTrue(VehicleControllers.create(vehicle).orElseThrow().mount(driver),
                "Invalid fixture: pickup driver failed to mount");
        driver.goalSelector.addGoal(0, new Goal() {
            private boolean requested;
            { this.setFlags(EnumSet.of(Flag.MOVE)); }
            @Override public boolean canUse() { return !this.requested; }
            @Override public void start() {
                this.requested = true;
                driver.getNavigation().moveTo(destination.x, destination.y, destination.z, 0.5D);
            }
            @Override public boolean canContinueToUse() { return true; }
        });
        Runnable close = () -> {
            driver.discard();
            vehicle.discard();
        };
        CLEANUP.add(close);
        int[] lastDirection = {0};
        int[] reversals = {0};
        int[] stopped = {0};
        int[] longestContact = {0};
        int[] contact = {0};
        double[] closest = {Double.POSITIVE_INFINITY};
        helper.onEachTick(() -> {
            helper.assertTrue(driver.getVehicle() == vehicle && vehicle.isAlive(), "Lost driver or vehicle");
            double distance = vehicle.position().subtract(destination).horizontalDistance();
            closest[0] = Math.min(closest[0], distance);
            double heading = Math.toRadians(vehicle.getYRot());
            double speed = vehicle.getDeltaMovement().dot(new Vec3(-Math.sin(heading), 0.0D, Math.cos(heading)));
            int direction = speed > 0.04D ? 1 : speed < -0.04D ? -1 : 0;
            if (direction != 0) {
                if (lastDirection[0] != 0 && direction != lastDirection[0]) reversals[0]++;
                lastDirection[0] = direction;
            }
            longestContact[0] = Math.max(longestContact[0],
                    contact[0] = vehicle.horizontalCollision ? contact[0] + 1 : 0);
            stopped[0] = distance <= 6.0D && vehicle.getDeltaMovement().horizontalDistance() <= 0.03D
                    ? stopped[0] + 1 : 0;
            if (helper.getTick() % 40 == 0) {
                LogUtils.getLogger().info("Ground motion pickup sample tick={} delta={} distance={} reversals={} stopped={}",
                        helper.getTick(), vehicle.position().subtract(start), distance, reversals[0], stopped[0]);
            }
            if (stopped[0] < 80 && helper.getTick() < 500) return;
            LogUtils.getLogger().info(
                    "Ground motion pickup result closest={} reversals={} stopped={} longestContact={}",
                    closest[0], reversals[0], stopped[0], longestContact[0]);
            helper.assertTrue(stopped[0] >= 80, "Pickup did not finish and remain stopped at navigation destination");
            helper.assertTrue(reversals[0] <= 8, "Pickup repeatedly shunted: " + reversals[0]);
            helper.assertTrue(longestContact[0] <= 35, "Pickup stayed wedged against village terrain");
            close.run();
            CLEANUP.remove(close);
            helper.succeed();
        });
    }

    private static BlockPos arena(ServerLevel level) {
        BlockPos origin = new BlockPos(512 + ARENA.getAndIncrement() * 192, 5, 14592);
        List<BlockPos> chunks = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                BlockPos chunk = new BlockPos((origin.getX() >> 4) + x, 0, (origin.getZ() >> 4) + z);
                level.setChunkForced(chunk.getX(), chunk.getZ(), true);
                chunks.add(chunk);
            }
        }
        for (int x = -60; x <= 60; x++) {
            for (int z = -60; z <= 60; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
            }
        }
        CLEANUP.add(() -> chunks.forEach(chunk -> level.setChunkForced(chunk.getX(), chunk.getZ(), false)));
        return origin;
    }

    private static Entity vehicle(ServerLevel level, String type, Vec3 start, float yaw) {
        Entity vehicle = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("superbwarfare", type)).create(level);
        vehicle.moveTo(start.x, start.y, start.z, yaw, 0.0F);
        vehicle.setInvulnerable(true);
        return vehicle;
    }

    private static Mob crew(ServerLevel level, Vec3 start) {
        Mob driver = EntityType.VINDICATOR.create(level);
        driver.moveTo(start);
        driver.setPersistenceRequired();
        driver.setInvulnerable(true);
        driver.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64.0D);
        level.addFreshEntity(driver);
        return driver;
    }
}
