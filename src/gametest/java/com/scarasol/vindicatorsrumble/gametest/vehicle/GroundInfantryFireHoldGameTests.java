package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.mojang.logging.LogUtils;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwGameTestVehicle;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwGameTestVehicle.GroundWeaponCondition;
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
public final class GroundInfantryFireHoldGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();
    private static final List<Runnable> CLEANUP = new ArrayList<>();
    private static boolean previousEnabled;
    private static boolean previousAmmo;
    private static boolean previousDebug;
    private static boolean previousGriefing;
    private static double previousNaturalChance;

    @BeforeBatch(batch = "ground_infantry_fire_hold")
    public static void prepare(ServerLevel level) {
        previousEnabled = CommonConfig.ENABLE_VEHICLE_AI.get();
        previousAmmo = CommonConfig.VEHICLE_AI_NEEDS_AMMO.get();
        previousDebug = CommonConfig.VEHICLE_AI_DEBUG.get();
        previousNaturalChance = CommonConfig.NATURAL_VEHICLE_CHANCE.get();
        previousGriefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        CommonConfig.ENABLE_VEHICLE_AI.set(true);
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(true);
        CommonConfig.VEHICLE_AI_DEBUG.set(true);
        CommonConfig.NATURAL_VEHICLE_CHANCE.set(0.0D);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
    }

    @AfterBatch(batch = "ground_infantry_fire_hold")
    public static void restore(ServerLevel level) {
        List.copyOf(CLEANUP).forEach(Runnable::run);
        CLEANUP.clear();
        CommonConfig.ENABLE_VEHICLE_AI.set(previousEnabled);
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(previousAmmo);
        CommonConfig.VEHICLE_AI_DEBUG.set(previousDebug);
        CommonConfig.NATURAL_VEHICLE_CHANCE.set(previousNaturalChance);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(previousGriefing, level.getServer());
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankHoldsOpenInfantrySouth(GameTestHelper helper) {
        verify(helper, 0.0F, Encounter.INFANTRY);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankHoldsOpenInfantryWest(GameTestHelper helper) {
        verify(helper, 90.0F, Encounter.INFANTRY);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankHoldsOpenInfantryNorth(GameTestHelper helper) {
        verify(helper, 180.0F, Encounter.INFANTRY);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankHoldsOpenInfantryEast(GameTestHelper helper) {
        verify(helper, -90.0F, Encounter.INFANTRY);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankHoldsOpenInfantryDiagonal(GameTestHelper helper) {
        verify(helper, 45.0F, Encounter.INFANTRY);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankHoldsOpenInfantryLoggedHeading(GameTestHelper helper) {
        verify(helper, -127.0F, Encounter.INFANTRY);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void unarmedDriverHoldsForGunnerSouth(GameTestHelper helper) {
        verify(helper, 0.0F, Encounter.INFANTRY_WITH_GUNNER);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void unarmedDriverHoldsForGunnerWest(GameTestHelper helper) {
        verify(helper, 90.0F, Encounter.INFANTRY_WITH_GUNNER);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankStillRetreatsFromMountedTargetSouth(GameTestHelper helper) {
        verify(helper, 0.0F, Encounter.MOUNTED_TARGET);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankStillRetreatsFromMountedTargetWest(GameTestHelper helper) {
        verify(helper, 90.0F, Encounter.MOUNTED_TARGET);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankStopsRetreatAfterTargetDismountsSouth(GameTestHelper helper) {
        verify(helper, 0.0F, Encounter.DISMOUNTING_TARGET);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankStopsRetreatAfterTargetDismountsWest(GameTestHelper helper) {
        verify(helper, 90.0F, Encounter.DISMOUNTING_TARGET);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankRamsWhileAllWeaponsReloadSouth(GameTestHelper helper) {
        verify(helper, 0.0F, Encounter.RELOADING);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankRamsWhileAllWeaponsReloadWest(GameTestHelper helper) {
        verify(helper, 90.0F, Encounter.RELOADING);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankRamsWhileAllWeaponsOverheatSouth(GameTestHelper helper) {
        verify(helper, 0.0F, Encounter.OVERHEATED);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankRamsWhileAllWeaponsOverheatWest(GameTestHelper helper) {
        verify(helper, 90.0F, Encounter.OVERHEATED);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankRamsWhenAllWeaponsEmptySouth(GameTestHelper helper) {
        verify(helper, 0.0F, Encounter.EMPTY);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankRamsWhenAllWeaponsEmptyWest(GameTestHelper helper) {
        verify(helper, 90.0F, Encounter.EMPTY);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankHoldsThroughSlowCadenceSouth(GameTestHelper helper) {
        verify(helper, 0.0F, Encounter.SLOW_CADENCE);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankHoldsThroughSlowCadenceWest(GameTestHelper helper) {
        verify(helper, 90.0F, Encounter.SLOW_CADENCE);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankSwitchesFromReloadingGunSouth(GameTestHelper helper) {
        verify(helper, 0.0F, Encounter.ALTERNATE_WEAPON);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void tankSwitchesFromOverheatedGunWest(GameTestHelper helper) {
        verify(helper, 90.0F, Encounter.ALTERNATE_COOLING_WEAPON);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void emptyDriverHoldsForArmedGunnerSouth(GameTestHelper helper) {
        verify(helper, 0.0F, Encounter.ARMED_GUNNER);
    }

    @GameTest(batch = "ground_infantry_fire_hold", template = "empty", timeoutTicks = 1000)
    public static void emptyDriverHoldsForArmedGunnerWest(GameTestHelper helper) {
        verify(helper, 90.0F, Encounter.ARMED_GUNNER);
    }

    private static void verify(GameTestHelper helper, float yaw, Encounter encounter) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(512 + ARENA.getAndIncrement() * 192, 5, 8448);
        Vec3 start = Vec3.atBottomCenterOf(origin);
        double radians = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
        boolean withGunner = encounter == Encounter.INFANTRY_WITH_GUNNER;
        boolean armedGunner = encounter == Encounter.ARMED_GUNNER;
        boolean ramOutage = encounter == Encounter.RELOADING || encounter == Encounter.OVERHEATED
                || encounter == Encounter.EMPTY;
        boolean mountedTarget = encounter == Encounter.MOUNTED_TARGET || encounter == Encounter.DISMOUNTING_TARGET;
        Vec3 destination = start.add(forward.scale(18.0D)).add(0.0D, withGunner ? 0.0D : 5.0D, 0.0D);
        List<BlockPos> chunks = new ArrayList<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                BlockPos chunk = new BlockPos((origin.getX() >> 4) + x, 0, (origin.getZ() >> 4) + z);
                level.setChunkForced(chunk.getX(), chunk.getZ(), true);
                chunks.add(chunk);
            }
        }
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
            }
        }
        String vehicleId = withGunner ? "sodayo_pick_up_hmg" : "yx_100";
        Entity vehicle = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("superbwarfare", vehicleId)).create(level);
        vehicle.moveTo(start.x, start.y + 0.05D, start.z, yaw, 0.0F);
        vehicle.setInvulnerable(true);
        Entity targetVehicle = EntityType.BOAT.create(level);
        targetVehicle.setInvulnerable(true);
        List<Mob> crew = new ArrayList<>();
        Mob driver = addCrew(level, EntityType.VINDICATOR, start, yaw);
        crew.add(driver);
        if (withGunner || armedGunner) crew.add(addCrew(level, EntityType.PILLAGER, start, yaw));
        Mob target = EntityType.VILLAGER.create(level);
        target.moveTo(start.add(forward.scale(30.0D)));
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255, false, false));
        level.addFreshEntity(target);
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
            targetVehicle.discard();
            vehicle.discard();
            chunks.forEach(chunk -> level.setChunkForced(chunk.getX(), chunk.getZ(), false));
        };
        CLEANUP.add(close);
        long[] readyAt = {-1};
        long[] holdAt = {-1};
        Vec3[] holdPosition = {start};
        int[] holdShots = {0};
        int[] earlyShots = {0};
        int[] outageShots = {0};
        Vec3[] outagePosition = {start};
        double[] maxRetreat = {0.0D};
        double[] maxDisplacement = {0.0D};
        helper.onEachTick(() -> {
            if (readyAt[0] < 0) {
                helper.assertTrue(helper.getTick() < 120, "Invalid fixture: crew did not acquire the target naturally");
                if (crew.stream().anyMatch(member -> member.getTarget() != target)) return;
                if (!withGunner) {
                    BlockPos platform = BlockPos.containing(destination);
                    for (int x = -3; x <= 3; x++) {
                        for (int z = -3; z <= 3; z++) {
                            for (int y = 1; y <= 5; y++) {
                                level.setBlock(platform.offset(x, -y, z), Blocks.BEDROCK.defaultBlockState(), 2);
                            }
                        }
                    }
                }
                target.moveTo(destination);
                target.setDeltaMovement(Vec3.ZERO);
                if (mountedTarget) {
                    targetVehicle.moveTo(destination.x, destination.y, destination.z, yaw, 0.0F);
                    level.addFreshEntity(targetVehicle);
                    helper.assertTrue(target.startRiding(targetVehicle, true), "Invalid fixture: target could not board");
                }
                level.addFreshEntity(vehicle);
                SbwGameTestVehicle.prepareContinuousGroundFire(vehicle, encounter == Encounter.SLOW_CADENCE);
                VehicleController controller = VehicleControllers.create(vehicle).orElseThrow();
                for (Mob member : crew) {
                    helper.assertTrue(controller.mount(member), "Invalid fixture: crew could not board");
                }
                if (withGunner) {
                    var armedSeat = controller.seats().stream()
                            .filter(seat -> seat.hasWeapon() && !seat.occupied()).findFirst().orElseThrow();
                    helper.assertTrue(controller.moveToSeat(crew.get(1), armedSeat),
                            "Invalid fixture: gunner could not move to an armed seat");
                    helper.assertTrue(!controller.seatOf(driver).orElseThrow().hasWeapon(),
                            "Invalid fixture: driver must be unarmed");
                    helper.assertTrue(controller.seatOf(crew.get(1)).orElseThrow().hasWeapon(),
                            "Invalid fixture: gunner must occupy an armed seat");
                }
                readyAt[0] = helper.getTick();
                if (!mountedTarget) holdAt[0] = readyAt[0];
            }
            helper.assertTrue(vehicle.isAlive() && driver.getVehicle() == vehicle, "Lost driver or vehicle");
            helper.assertTrue(driver.getTarget() == target, "Lost the naturally acquired target");
            double retreat = -vehicle.position().subtract(start).dot(forward);
            maxRetreat[0] = Math.max(maxRetreat[0], retreat);
            long elapsed = helper.getTick() - readyAt[0];
            if (encounter == Encounter.MOUNTED_TARGET) {
                helper.assertTrue(target.getVehicle() == targetVehicle, "Lost mounted-target fixture");
                if (elapsed < 180) return;
                helper.assertTrue(maxRetreat[0] >= 6.0D, "Vehicle-target retreat was disabled: " + maxRetreat[0]);
            } else {
                if (holdAt[0] < 0) {
                    helper.assertTrue(elapsed < 120, "Tank never began retreating from a mounted target");
                    if (retreat < 2.0D) return;
                    target.stopRiding();
                    targetVehicle.discard();
                    target.moveTo(destination);
                    target.setDeltaMovement(Vec3.ZERO);
                    holdPosition[0] = vehicle.position();
                    holdShots[0] = shots.size();
                    holdAt[0] = helper.getTick();
                }
                long holding = helper.getTick() - holdAt[0];
                double displacement = vehicle.position().subtract(holdPosition[0]).horizontalDistance();
                maxDisplacement[0] = Math.max(maxDisplacement[0], displacement);
                if (holding % 100 == 0) {
                    LogUtils.getLogger().info(
                            "Ground infantry hold yaw={} encounter={} tick={} displacement={} maxDisplacement={} retreat={} shots={} speed={}",
                            yaw, encounter, holding, displacement, maxDisplacement[0], retreat,
                            shots.size() - holdShots[0], vehicle.getDeltaMovement().horizontalDistance());
                }
                if (!ramOutage || holding <= 120) {
                    helper.assertTrue(displacement <= 3.0D,
                            "Vehicle left its firing position against infantry: " + displacement);
                }
                if (holding == 120) {
                    earlyShots[0] = shots.size();
                    helper.assertTrue(earlyShots[0] > holdShots[0], "Vehicle held position but never fired at infantry");
                    VehicleController controller = VehicleControllers.create(vehicle).orElseThrow();
                    if (ramOutage || armedGunner) {
                        GroundWeaponCondition condition = encounter == Encounter.RELOADING ? GroundWeaponCondition.RELOADING
                                : encounter == Encounter.OVERHEATED ? GroundWeaponCondition.OVERHEATED : GroundWeaponCondition.EMPTY;
                        for (var weapon : controller.weapons(driver)) {
                            SbwGameTestVehicle.setGroundWeaponCondition(vehicle, driver, weapon.id(), condition);
                        }
                        outageShots[0] = shots.size();
                        outagePosition[0] = vehicle.position();
                    } else if (encounter == Encounter.ALTERNATE_WEAPON
                            || encounter == Encounter.ALTERNATE_COOLING_WEAPON) {
                        var machineGun = controller.weapons(driver).stream()
                                .filter(weapon -> weapon.payloadType().isSoftTargetPreferred()).findFirst().orElseThrow();
                        SbwGameTestVehicle.setGroundWeaponCondition(vehicle, driver, machineGun.id(),
                                encounter == Encounter.ALTERNATE_WEAPON ? GroundWeaponCondition.RELOADING
                                        : GroundWeaponCondition.OVERHEATED);
                    }
                }
                if (ramOutage && holding > 120) {
                    double progress = vehicle.position().subtract(outagePosition[0]).dot(forward);
                    helper.assertTrue(maxRetreat[0] <= 3.0D, "Weapon outage caused retreat instead of ramming");
                    helper.assertTrue(shots.size() == outageShots[0], "Invalid fixture: disabled weapons still fired");
                    if (progress < 4.0D) {
                        helper.assertTrue(holding < 260, "No ramming advance during weapon outage: " + progress);
                        return;
                    }
                } else {
                    if (holding < 600) return;
                    helper.assertTrue(shots.size() > earlyShots[0], "Vehicle did not sustain fire during the hold");
                }
            }
            LogUtils.getLogger().info(
                    "Ground infantry hold result yaw={} encounter={} elapsed={} maxRetreat={} maxDisplacement={} shots={}",
                    yaw, encounter, elapsed, maxRetreat[0], maxDisplacement[0], shots.size() - holdShots[0]);
            close.run();
            CLEANUP.remove(close);
            helper.succeed();
        });
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

    private enum Encounter {
        INFANTRY,
        INFANTRY_WITH_GUNNER,
        MOUNTED_TARGET,
        DISMOUNTING_TARGET,
        RELOADING,
        OVERHEATED,
        EMPTY,
        SLOW_CADENCE,
        ALTERNATE_WEAPON,
        ALTERNATE_COOLING_WEAPON,
        ARMED_GUNNER
    }
}
