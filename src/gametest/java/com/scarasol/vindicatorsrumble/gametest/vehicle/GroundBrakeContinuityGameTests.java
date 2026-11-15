package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.mojang.logging.LogUtils;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwGameTestVehicle;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@GameTestHolder("vindicators_rumble")
@PrefixGameTestTemplate(false)
public final class GroundBrakeContinuityGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();
    private static final List<Runnable> CLEANUP = new ArrayList<>();
    private static boolean previousEnabled;
    private static boolean previousAmmo;
    private static boolean previousDebug;
    private static boolean previousGriefing;
    private static double previousNaturalChance;

    @BeforeBatch(batch = "ground_brake_continuity")
    public static void prepare(ServerLevel level) {
        previousEnabled = CommonConfig.ENABLE_VEHICLE_AI.get();
        previousAmmo = CommonConfig.VEHICLE_AI_NEEDS_AMMO.get();
        previousDebug = CommonConfig.VEHICLE_AI_DEBUG.get();
        previousNaturalChance = CommonConfig.NATURAL_VEHICLE_CHANCE.get();
        previousGriefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        CommonConfig.ENABLE_VEHICLE_AI.set(true);
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(false);
        CommonConfig.VEHICLE_AI_DEBUG.set(true);
        CommonConfig.NATURAL_VEHICLE_CHANCE.set(0.0D);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
    }

    @AfterBatch(batch = "ground_brake_continuity")
    public static void restore(ServerLevel level) {
        List.copyOf(CLEANUP).forEach(Runnable::run);
        CLEANUP.clear();
        CommonConfig.ENABLE_VEHICLE_AI.set(previousEnabled);
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(previousAmmo);
        CommonConfig.VEHICLE_AI_DEBUG.set(previousDebug);
        CommonConfig.NATURAL_VEHICLE_CHANCE.set(previousNaturalChance);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(previousGriefing, level.getServer());
    }

    @GameTest(batch = "ground_brake_continuity", template = "empty", timeoutTicks = 900)
    public static void stoppedTankDoesNotFleeRearInfantrySouth(GameTestHelper helper) {
        verify(helper, 0.0F, false);
    }

    @GameTest(batch = "ground_brake_continuity", template = "empty", timeoutTicks = 900)
    public static void stoppedTankDoesNotFleeRearInfantryWest(GameTestHelper helper) {
        verify(helper, 90.0F, false);
    }

    @GameTest(batch = "ground_brake_continuity", template = "empty", timeoutTicks = 900)
    public static void stoppedTankDoesNotFleeRearInfantryNorth(GameTestHelper helper) {
        verify(helper, 180.0F, false);
    }

    @GameTest(batch = "ground_brake_continuity", template = "empty", timeoutTicks = 900)
    public static void stoppedTankDoesNotFleeRearInfantryEast(GameTestHelper helper) {
        verify(helper, -90.0F, false);
    }

    @GameTest(batch = "ground_brake_continuity", template = "empty", timeoutTicks = 900)
    public static void stoppedTankDoesNotFleeRearInfantryDiagonal(GameTestHelper helper) {
        verify(helper, 45.0F, false);
    }

    @GameTest(batch = "ground_brake_continuity", template = "empty", timeoutTicks = 900)
    public static void stoppedTankDoesNotFleeRearInfantryLoggedHeading(GameTestHelper helper) {
        verify(helper, -127.0F, false);
    }

    @GameTest(batch = "ground_brake_continuity", template = "empty", timeoutTicks = 900)
    public static void stoppedPickupDoesNotFleeRearInfantrySouth(GameTestHelper helper) {
        verify(helper, 0.0F, true);
    }

    @GameTest(batch = "ground_brake_continuity", template = "empty", timeoutTicks = 900)
    public static void stoppedPickupDoesNotFleeRearInfantryWest(GameTestHelper helper) {
        verify(helper, 90.0F, true);
    }

    private static void verify(GameTestHelper helper, float yaw, boolean pickup) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(512 + ARENA.getAndIncrement() * 192, 5, 10368);
        Vec3 start = Vec3.atBottomCenterOf(origin);
        Vec3 forward = direction(yaw);
        List<BlockPos> chunks = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                BlockPos chunk = new BlockPos((origin.getX() >> 4) + x, 0, (origin.getZ() >> 4) + z);
                level.setChunkForced(chunk.getX(), chunk.getZ(), true);
                chunks.add(chunk);
            }
        }
        for (int x = -55; x <= 55; x++) {
            for (int z = -55; z <= 55; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
            }
        }
        Entity vehicle = ForgeRegistries.ENTITY_TYPES.getValue(
                new ResourceLocation("superbwarfare", pickup ? "sodayo_pick_up_hmg" : "yx_100")).create(level);
        vehicle.moveTo(start.x, start.y + 0.05D, start.z, yaw, 0.0F);
        vehicle.setInvulnerable(true);
        List<Mob> crew = new ArrayList<>();
        crew.add(addCrew(level, EntityType.VINDICATOR, start, yaw));
        if (pickup) crew.add(addCrew(level, EntityType.PILLAGER, start, yaw));
        Mob driver = crew.get(0);
        Mob target = EntityType.VILLAGER.create(level);
        target.moveTo(start.add(forward.scale(30.0D)));
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255, false, false));
        level.addFreshEntity(target);
        Entity boat = EntityType.BOAT.create(level);
        boat.setInvulnerable(true);
        List<Entity> shots = new ArrayList<>();
        Consumer<EntityJoinLevelEvent> observer = event -> {
            if (event.getEntity() instanceof Projectile projectile && crew.contains(projectile.getOwner())) {
                shots.add(projectile);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(observer);
        Runnable close = () -> {
            MinecraftForge.EVENT_BUS.unregister(observer);
            shots.forEach(Entity::discard);
            crew.forEach(Entity::discard);
            target.discard();
            boat.discard();
            vehicle.discard();
            chunks.forEach(chunk -> level.setChunkForced(chunk.getX(), chunk.getZ(), false));
        };
        CLEANUP.add(close);
        long[] readyAt = {-1};
        long[] brakeAt = {-1};
        long[] rearAt = {-1};
        Vec3[] brakePosition = {start};
        int[] stoppedTicks = {0};
        int[] rearShots = {0};
        double[] peakSpeed = {0.0D};
        double[] brakeTravel = {0.0D};
        double[] maxRearDistance = {0.0D};
        boolean[] contacted = {false};
        helper.onEachTick(() -> {
            if (readyAt[0] < 0) {
                helper.assertTrue(helper.getTick() < 120, "Invalid fixture: no natural target acquisition");
                if (crew.stream().anyMatch(member -> member.getTarget() != target)) return;
                Vec3 elevated = start.add(forward.scale(18.0D)).add(0.0D, pickup ? 0.0D : 5.0D, 0.0D);
                BlockPos platform = BlockPos.containing(elevated);
                if (!pickup) {
                    for (int x = -3; x <= 3; x++) {
                        for (int z = -3; z <= 3; z++) {
                            for (int y = 1; y <= 5; y++) {
                                level.setBlock(platform.offset(x, -y, z), Blocks.BEDROCK.defaultBlockState(), 2);
                            }
                        }
                    }
                }
                boat.moveTo(elevated);
                target.moveTo(elevated);
                level.addFreshEntity(boat);
                helper.assertTrue(target.startRiding(boat, true), "Invalid fixture: target did not board");
                level.addFreshEntity(vehicle);
                VehicleController controller = VehicleControllers.create(vehicle).orElseThrow();
                for (Mob member : crew) {
                    helper.assertTrue(controller.mount(member), "Invalid fixture: crew could not board");
                }
                if (pickup) {
                    var armedSeat = controller.seats().stream()
                            .filter(seat -> seat.hasWeapon() && !seat.occupied()).findFirst().orElseThrow();
                    helper.assertTrue(controller.moveToSeat(crew.get(1), armedSeat),
                            "Invalid fixture: gunner did not take an armed seat");
                }
                readyAt[0] = helper.getTick();
            }
            helper.assertTrue(vehicle.isAlive() && driver.getVehicle() == vehicle, "Lost driver or vehicle");
            helper.assertTrue(driver.getTarget() == target, "Lost the naturally acquired target");
            double speed = vehicle.getDeltaMovement().horizontalDistance();
            if (brakeAt[0] < 0) {
                peakSpeed[0] = Math.max(peakSpeed[0], speed);
                double retreat = -vehicle.position().subtract(start).dot(forward);
                helper.assertTrue(helper.getTick() - readyAt[0] < 180,
                        "Invalid fixture: vehicle never accelerated away from mounted target");
                if (retreat < 6.0D || speed < 0.3D) return;
                target.stopRiding();
                boat.discard();
                target.moveTo(vehicle.position().add(forward.scale(18.0D))
                        .add(0.0D, pickup ? 0.0D : 5.0D, 0.0D));
                target.setNoGravity(true);
                target.setDeltaMovement(Vec3.ZERO);
                brakeAt[0] = helper.getTick();
                brakePosition[0] = vehicle.position();
                return;
            }
            if (rearAt[0] < 0) {
                brakeTravel[0] = Math.max(brakeTravel[0],
                        vehicle.position().subtract(brakePosition[0]).horizontalDistance());
                helper.assertTrue(brakeTravel[0] <= 6.0D, "Vehicle failed to brake within six blocks");
                helper.assertTrue(helper.getTick() - brakeAt[0] < 100, "Vehicle failed to remain stopped after braking");
                stoppedTicks[0] = speed <= 0.02D ? stoppedTicks[0] + 1 : 0;
                if (stoppedTicks[0] < 10) return;
                target.setNoGravity(false);
                Vec3 rear = vehicle.position().subtract(direction(vehicle.getYRot()).scale(18.0D));
                target.moveTo(rear.x, start.y, rear.z);
                target.setDeltaMovement(Vec3.ZERO);
                if (pickup) {
                    VehicleController controller = VehicleControllers.create(vehicle).orElseThrow();
                    for (var weapon : controller.weapons(crew.get(1))) {
                        SbwGameTestVehicle.setGroundWeaponCondition(vehicle, crew.get(1), weapon.id(),
                                SbwGameTestVehicle.GroundWeaponCondition.OVERHEATED);
                    }
                }
                rearAt[0] = helper.getTick();
                rearShots[0] = shots.size();
                return;
            }
            long elapsed = helper.getTick() - rearAt[0];
            double distance = vehicle.position().subtract(target.position()).horizontalDistance();
            maxRearDistance[0] = Math.max(maxRearDistance[0], distance);
            contacted[0] |= vehicle.getBoundingBox().intersects(target.getBoundingBox());
            helper.assertTrue(distance <= 24.0D,
                    "Stopped vehicle restarted distance retreat from rear infantry: distance=" + distance);
            if (elapsed < 300) return;
            helper.assertTrue(contacted[0] || shots.size() > rearShots[0],
                    "Vehicle neither engaged nor approached rear infantry after stopping");
            LogUtils.getLogger().info(
                    "Ground brake continuity result yaw={} pickup={} peakSpeed={} brakeTravel={} maxRearDistance={} contact={} rearShots={}",
                    yaw, pickup, peakSpeed[0], brakeTravel[0], maxRearDistance[0], contacted[0], shots.size() - rearShots[0]);
            close.run();
            CLEANUP.remove(close);
            helper.succeed();
        });
    }

    private static Vec3 direction(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
    }

    private static Mob addCrew(ServerLevel level, EntityType<? extends Mob> type, Vec3 position, float yaw) {
        Mob member = type.create(level);
        member.moveTo(position.x, position.y, position.z, yaw, 0.0F);
        member.setPersistenceRequired();
        member.setInvulnerable(true);
        member.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64.0D);
        level.addFreshEntity(member);
        return member;
    }
}
