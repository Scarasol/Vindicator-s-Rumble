package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.mojang.logging.LogUtils;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwGameTestVehicle;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder("vindicators_rumble")
@PrefixGameTestTemplate(false)
public final class GroundReverseRecoveryGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();
    private static final List<Runnable> CLEANUP = new ArrayList<>();
    private static boolean previousDebug;
    private static boolean previousEnabled;
    private static boolean previousGriefing;
    private static double previousNatural;

    @BeforeBatch(batch = "ground_reverse_recovery")
    public static void prepare(ServerLevel level) {
        previousDebug = CommonConfig.VEHICLE_AI_DEBUG.get();
        previousEnabled = CommonConfig.ENABLE_VEHICLE_AI.get();
        previousNatural = CommonConfig.NATURAL_VEHICLE_CHANCE.get();
        previousGriefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        CommonConfig.VEHICLE_AI_DEBUG.set(true);
        CommonConfig.ENABLE_VEHICLE_AI.set(true);
        CommonConfig.NATURAL_VEHICLE_CHANCE.set(0.0D);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
    }

    @AfterBatch(batch = "ground_reverse_recovery")
    public static void restore(ServerLevel level) {
        List.copyOf(CLEANUP).forEach(Runnable::run);
        CLEANUP.clear();
        CommonConfig.VEHICLE_AI_DEBUG.set(previousDebug);
        CommonConfig.ENABLE_VEHICLE_AI.set(previousEnabled);
        CommonConfig.NATURAL_VEHICLE_CHANCE.set(previousNatural);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(previousGriefing, level.getServer());
    }

    @GameTest(batch = "ground_reverse_recovery", template = "empty", timeoutTicks = 850)
    public static void tankResumesNavigationAfterLoggedReverseTurn(GameTestHelper helper) {
        verify(helper, Rotation.NONE, 0.0F);
    }

    @GameTest(batch = "ground_reverse_recovery", template = "empty", timeoutTicks = 850)
    public static void tankResumesNavigationAfterWestReverseTurn(GameTestHelper helper) {
        verify(helper, Rotation.CLOCKWISE_90, 90.0F);
    }

    @GameTest(batch = "ground_reverse_recovery", template = "empty", timeoutTicks = 850)
    public static void tankResumesNavigationAfterNorthReverseTurn(GameTestHelper helper) {
        verify(helper, Rotation.CLOCKWISE_180, 180.0F);
    }

    @GameTest(batch = "ground_reverse_recovery", template = "empty", timeoutTicks = 850)
    public static void tankResumesNavigationAfterEastReverseTurn(GameTestHelper helper) {
        verify(helper, Rotation.COUNTERCLOCKWISE_90, -90.0F);
    }

    private static void verify(GameTestHelper helper, Rotation rotation, float yawOffset) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(512 + ARENA.getAndIncrement() * 384, 5, 16128);
        List<BlockPos> chunks = new ArrayList<>();
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                BlockPos chunk = new BlockPos((origin.getX() >> 4) + x, 0, (origin.getZ() >> 4) + z);
                level.setChunkForced(chunk.getX(), chunk.getZ(), true);
                chunks.add(chunk);
            }
        }
        CLEANUP.add(() -> chunks.forEach(chunk -> level.setChunkForced(chunk.getX(), chunk.getZ(), false)));
        for (int x = -144; x <= 144; x++) {
            for (int z = -144; z <= 144; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
            }
        }
        try (var stream = GroundReverseRecoveryGameTests.class.getResourceAsStream(
                "/data/vindicators_rumble/fixtures/logged_pickup_terrain.nbt")) {
            if (stream == null) throw new IOException("Missing village terrain snapshot");
            var blocks = NbtIo.readCompressed(stream).getList("blocks", 10);
            var lookup = level.holderLookup(Registries.BLOCK);
            for (var value : blocks) {
                CompoundTag block = (CompoundTag) value;
                int[] pos = block.getIntArray("pos");
                BlockPos transformed = new BlockPos(pos[0], pos[1], pos[2]).rotate(rotation);
                level.setBlock(origin.offset(transformed),
                        NbtUtils.readBlockState(lookup, block.getCompound("state")).rotate(rotation), 2);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Invalid fixture: could not load village terrain", failure);
        }

        Vec3 start = transform(origin, new Vec3(-8.7373202643009D, 0.01888773264895D, 23.45301235709456D), rotation);
        Vec3 destination = transform(origin, new Vec3(58.5D, 0.0D, 3.5D), rotation);
        Entity vehicle = ForgeRegistries.ENTITY_TYPES.getValue(
                new ResourceLocation("superbwarfare", "yx_100")).create(level);
        vehicle.moveTo(start.x, start.y, start.z, 138.04657F + yawOffset, 0.0F);
        vehicle.setInvulnerable(true);
        level.addFreshEntity(vehicle);
        Mob driver = EntityType.VINDICATOR.create(level);
        driver.moveTo(start);
        driver.setInvulnerable(true);
        driver.setPersistenceRequired();
        level.addFreshEntity(driver);
        var controller = VehicleControllers.create(vehicle).orElseThrow();
        helper.assertTrue(controller.mount(driver), "Invalid fixture: tank driver could not mount");
        boolean[] requested = {false};
        driver.goalSelector.addGoal(0, new Goal() {
            { this.setFlags(EnumSet.of(Flag.MOVE)); }
            @Override public boolean canUse() { return vehicle.tickCount >= 2; }
            @Override public boolean canContinueToUse() { return true; }
            @Override public void start() {
                requested[0] = driver.getNavigation().moveTo(destination.x, destination.y, destination.z, 0.5D);
            }
        });
        Runnable close = () -> {
            driver.discard();
            vehicle.discard();
        };
        CLEANUP.add(close);
        long[] readyAt = {-1};
        int[] reverse = {0};
        int[] longestReverse = {0};
        int[] reverseTurns = {0};
        int[] contact = {0};
        int[] longestContact = {0};
        int[] stopped = {0};
        double initialDistance = start.subtract(destination).horizontalDistance();
        double[] farthest = {initialDistance};
        double[] closest = {initialDistance};
        helper.onEachTick(() -> {
            helper.assertTrue(vehicle.isAlive() && driver.getVehicle() == vehicle, "Lost driver or tank");
            helper.assertTrue(driver.getTarget() == null, "Invalid fixture: navigation acquired a combat target");
            if (readyAt[0] < 0) {
                helper.assertTrue(helper.getTick() < 100, "Invalid fixture: navigation request was not accepted");
                if (!requested[0]) return;
                AABB destinationBounds = vehicle.getBoundingBox().move(destination.subtract(vehicle.position()));
                AABB destinationSpace = new AABB(destinationBounds.minX, destination.y + 0.25D, destinationBounds.minZ,
                        destinationBounds.maxX, destinationBounds.maxY, destinationBounds.maxZ);
                helper.assertTrue(level.noCollision(vehicle, destinationSpace),
                        "Invalid fixture: navigation destination intersects village blocks");
                readyAt[0] = helper.getTick();
            }
            long elapsed = helper.getTick() - readyAt[0];
            var input = SbwGameTestVehicle.flightControl(vehicle);
            longestReverse[0] = Math.max(longestReverse[0], reverse[0] = input.back() ? reverse[0] + 1 : 0);
            if (input.back() && (input.left() || input.right())) reverseTurns[0]++;
            longestContact[0] = Math.max(longestContact[0],
                    contact[0] = vehicle.horizontalCollision ? contact[0] + 1 : 0);
            double distance = vehicle.position().subtract(destination).horizontalDistance();
            farthest[0] = Math.max(farthest[0], distance);
            closest[0] = Math.min(closest[0], distance);
            stopped[0] = distance <= 8.0D && vehicle.getDeltaMovement().horizontalDistance() <= 0.03D
                    ? stopped[0] + 1 : 0;
            if (elapsed % 40 == 0) {
                LogUtils.getLogger().info(
                        "Ground reverse sample rotation={} elapsed={} delta={} yaw={} distance={} reverse={} stopped={}",
                        rotation, elapsed, vehicle.position().subtract(start), vehicle.getYRot(),
                        distance, longestReverse[0], stopped[0]);
            }
            if (elapsed < 700) return;
            LogUtils.getLogger().info(
                    "Ground reverse result rotation={} reverse={} reverseTurns={} contact={} farthest={} closest={} stopped={}",
                    rotation, longestReverse[0], reverseTurns[0], longestContact[0], farthest[0], closest[0], stopped[0]);
            helper.assertTrue(longestReverse[0] <= 120, "Tank kept reversing after recovery: " + longestReverse[0]);
            helper.assertTrue(farthest[0] <= initialDistance + 24.0D,
                    "Recovery drove the tank away from its navigation destination: " + farthest[0]);
            helper.assertTrue(longestContact[0] <= 40, "Tank remained wedged against village terrain");
            helper.assertTrue(stopped[0] >= 80, "Tank failed to resume and complete navigation: closest=" + closest[0]);
            close.run();
            CLEANUP.remove(close);
            helper.succeed();
        });
    }

    private static Vec3 transform(BlockPos origin, Vec3 position, Rotation rotation) {
        Vec3 offset = position.add(-0.5D, 0.0D, -0.5D);
        Vec3 rotated = switch (rotation) {
            case NONE -> offset;
            case CLOCKWISE_90 -> new Vec3(-offset.z, offset.y, offset.x);
            case CLOCKWISE_180 -> new Vec3(-offset.x, offset.y, -offset.z);
            case COUNTERCLOCKWISE_90 -> new Vec3(offset.z, offset.y, -offset.x);
        };
        return Vec3.atLowerCornerOf(origin).add(rotated).add(0.5D, 0.0D, 0.5D);
    }
}
