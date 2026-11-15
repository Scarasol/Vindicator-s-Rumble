package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.mojang.logging.LogUtils;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
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
public final class GroundFireWindowGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();
    private static final List<Runnable> CLEANUP = new ArrayList<>();
    private static boolean previousEnabled;
    private static boolean previousAmmo;
    private static boolean previousDebug;
    private static boolean previousGriefing;
    private static double previousNaturalChance;

    @BeforeBatch(batch = "ground_fire_windows")
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

    @AfterBatch(batch = "ground_fire_windows")
    public static void restore(ServerLevel level) {
        List.copyOf(CLEANUP).forEach(Runnable::run);
        CLEANUP.clear();
        CommonConfig.ENABLE_VEHICLE_AI.set(previousEnabled);
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(previousAmmo);
        CommonConfig.VEHICLE_AI_DEBUG.set(previousDebug);
        CommonConfig.NATURAL_VEHICLE_CHANCE.set(previousNaturalChance);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(previousGriefing, level.getServer());
    }

    @GameTest(batch = "ground_fire_windows", template = "empty", timeoutTicks = 320)
    public static void tankRamsUnshootableCloseInfantrySouth(GameTestHelper helper) {
        verify(helper, 0.0F, false, false, false);
    }

    @GameTest(batch = "ground_fire_windows", template = "empty", timeoutTicks = 320)
    public static void tankRamsUnshootableCloseInfantryWest(GameTestHelper helper) {
        verify(helper, 90.0F, false, false, false);
    }

    @GameTest(batch = "ground_fire_windows", template = "empty", timeoutTicks = 320)
    public static void tankRamsUnshootableCloseInfantryDiagonal(GameTestHelper helper) {
        verify(helper, 45.0F, false, false, false);
    }

    @GameTest(batch = "ground_fire_windows", template = "empty", timeoutTicks = 320)
    public static void tankAndGunnerDoNotWaitAtBlockedRetreatSouth(GameTestHelper helper) {
        verify(helper, 0.0F, true, true, false);
    }

    @GameTest(batch = "ground_fire_windows", template = "empty", timeoutTicks = 320)
    public static void tankAndGunnerDoNotWaitAtBlockedRetreatEast(GameTestHelper helper) {
        verify(helper, -90.0F, true, true, false);
    }

    @GameTest(batch = "ground_fire_windows", template = "empty", timeoutTicks = 320)
    public static void tankHoldsForReachableElevatedTargetSouth(GameTestHelper helper) {
        verify(helper, 0.0F, true, false, true);
    }

    @GameTest(batch = "ground_fire_windows", template = "empty", timeoutTicks = 320)
    public static void tankHoldsForReachableElevatedTargetWest(GameTestHelper helper) {
        verify(helper, 90.0F, true, false, true);
    }

    @GameTest(batch = "ground_fire_windows", template = "empty", timeoutTicks = 320)
    public static void tankAndGunnerHoldForReachableElevatedTarget(GameTestHelper helper) {
        verify(helper, 0.0F, true, true, true);
    }

    private static void verify(GameTestHelper helper, float yaw, boolean rearWall, boolean gunner, boolean elevated) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(512 + ARENA.getAndIncrement() * 192, 5, 2304);
        Vec3 start = Vec3.atBottomCenterOf(origin);
        double radians = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        Vec3 lateral = new Vec3(forward.z, 0.0D, -forward.x);
        List<BlockPos> chunks = new ArrayList<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                int chunkX = (origin.getX() >> 4) + x;
                int chunkZ = (origin.getZ() >> 4) + z;
                level.setChunkForced(chunkX, chunkZ, true);
                chunks.add(new BlockPos(chunkX, 0, chunkZ));
            }
        }
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
            }
        }
        Entity vehicle = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("superbwarfare", "yx_100")).create(level);
        vehicle.moveTo(start.x, start.y + 0.05D, start.z, yaw, 0.0F);
        vehicle.setInvulnerable(true);
        Mob driver = EntityType.VINDICATOR.create(level);
        driver.moveTo(start.x, start.y, start.z, yaw, 0.0F);
        driver.setPersistenceRequired();
        driver.setInvulnerable(true);
        driver.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64.0D);
        level.addFreshEntity(driver);
        Mob target = EntityType.VILLAGER.create(level);
        target.moveTo(start.add(forward.scale(30.0D)));
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255, false, false));
        level.addFreshEntity(target);
        List<Mob> crew = new ArrayList<>(List.of(driver));
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
            vehicle.discard();
            chunks.forEach(chunk -> level.setChunkForced(chunk.getX(), chunk.getZ(), false));
        };
        CLEANUP.add(close);
        long[] readyAt = {-1};
        double[] maxReverse = {0.0D};
        boolean[] contacted = {false};
        helper.onEachTick(() -> {
            if (readyAt[0] < 0) {
                helper.assertTrue(helper.getTick() < 120, "Invalid fixture: no natural target acquisition");
                if (driver.getTarget() != target) return;
                if (rearWall) {
                    for (int side = -12; side <= 12; side++) {
                        BlockPos block = BlockPos.containing(start.add(forward.scale(-8.0D)).add(lateral.scale(side)));
                        for (int y = 0; y < 6; y++) level.setBlock(block.above(y), Blocks.STONE.defaultBlockState(), 2);
                    }
                }
                if (elevated) {
                    for (int distance = 16; distance <= 20; distance++) {
                        for (int side = -2; side <= 2; side++) {
                            BlockPos block = BlockPos.containing(start.add(forward.scale(distance)).add(lateral.scale(side)));
                            for (int y = 0; y < 5; y++) level.setBlock(block.above(y), Blocks.STONE.defaultBlockState(), 2);
                        }
                    }
                }
                target.moveTo(start.add(forward.scale(elevated ? 18.0D : 8.0D)).add(0.0D, elevated ? 5.0D : 0.0D, 0.0D));
                target.setDeltaMovement(Vec3.ZERO);
                level.addFreshEntity(vehicle);
                VehicleController controller = VehicleControllers.create(vehicle).orElseThrow();
                helper.assertTrue(controller.mount(driver), "Invalid fixture: could not mount driver");
                if (gunner) {
                    Mob passenger = EntityType.PILLAGER.create(level);
                    passenger.moveTo(vehicle.position());
                    passenger.setPersistenceRequired();
                    passenger.setInvulnerable(true);
                    level.addFreshEntity(passenger);
                    crew.add(passenger);
                    helper.assertTrue(controller.mount(passenger), "Invalid fixture: could not mount gunner");
                    helper.assertTrue(controller.seatOf(passenger).orElseThrow().hasWeapon(),
                            "Invalid fixture: passenger is not in an armed seat");
                }
                readyAt[0] = helper.getTick();
            }
            helper.assertTrue(vehicle.isAlive() && driver.getVehicle() == vehicle, "Lost driver or vehicle");
            long elapsed = helper.getTick() - readyAt[0];
            double progress = vehicle.position().subtract(start).dot(forward);
            maxReverse[0] = Math.max(maxReverse[0], -progress);
            contacted[0] |= vehicle.getBoundingBox().intersects(target.getBoundingBox());
            if (elapsed % 20 == 0) {
                LogUtils.getLogger().info(
                        "Ground fire window yaw={} wall={} gunner={} elevated={} tick={} progress={} maxReverse={} contact={} shots={} target={} speed={}",
                        yaw, rearWall, gunner, elevated, elapsed, progress, maxReverse[0], contacted[0],
                        shots.size(), driver.getTarget() == target, vehicle.getDeltaMovement().horizontalDistance());
            }
            if (elevated) {
                helper.assertTrue(vehicle.position().subtract(start).horizontalDistance() <= 3.0D,
                        "Tank moved out of its firing position instead of waiting for a reachable target");
                if (elapsed < 120) return;
                helper.assertTrue(!shots.isEmpty(), "Tank never fired at a target within its elevation capability");
            } else {
                helper.assertTrue(maxReverse[0] <= 3.0D,
                        "Tank kept retreating from close infantry it could not shoot");
                if (progress < 6.0D || !contacted[0]) {
                    helper.assertTrue(elapsed < 180, "Tank neither advanced into the close target nor escaped the false firing hold");
                    return;
                }
            }
            LogUtils.getLogger().info("Ground fire result yaw={} wall={} gunner={} elevated={} ticks={} progress={} maxReverse={} shots={}",
                    yaw, rearWall, gunner, elevated, elapsed, progress, maxReverse[0], shots.size());
            close.run();
            CLEANUP.remove(close);
            helper.succeed();
        });
    }
}
