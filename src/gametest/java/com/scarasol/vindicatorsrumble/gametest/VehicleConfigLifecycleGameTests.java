package com.scarasol.vindicatorsrumble.gametest;

import com.scarasol.vindicatorsrumble.compat.sbw.SbwGameTestVehicle;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.common.VehicleAiGoal;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.weapon.VehicleWeaponGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

@GameTestHolder("vindicators_rumble")
@PrefixGameTestTemplate(false)
public final class VehicleConfigLifecycleGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();

    @GameTest(batch = "config_lifecycle_disable", template = "empty", timeoutTicks = 140)
    public static void hotDisableKeepsExistingCombatAiButExcludesNewMobs(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, true);
        Mob target = fixture.enemy();
        fixture.driver.setNoAi(false);
        boolean[] ready = {false};
        helper.runAtTickTime(100, () -> {
            if (!ready[0]) {
                fixture.close();
                helper.fail("Combat fixture failed natural target acquisition and weapon Goal startup");
            }
        });
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                fixture.driver.tickCount >= 4 && fixture.driver.getTarget() == target && weaponRunning(fixture.driver),
                "Waiting for the exact natural target and injected weapon Goal")).thenExecute(() -> {
            ready[0] = true;
            CommonConfig.ENABLE_VEHICLE_AI.set(false);
            CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(false);
            Entity newVehicle = fixture.vehicle(12.0D);
            Mob newMob = fixture.mob(12.0D);
            helper.assertTrue(newMob.startRiding(newVehicle, true), "New disabled mob could not mount");
            helper.runAfterDelay(20, () -> {
                try {
                    helper.assertTrue(fixture.driver.getTarget() == target && target.isAlive(),
                            "The fixture lost its surviving naturally acquired target");
                    helper.assertTrue(weaponRunning(fixture.driver), "Hot disable interrupted historical weapon AI");
                    helper.assertTrue(SbwGameTestVehicle.hasCreativeVehicleAmmo(fixture.vehicle),
                            "Historical AI lost its ammunition policy after hot disable");
                    helper.assertTrue(vehicleGoals(newMob) == 0, "A new mob received AI while the switch was disabled");
                    helper.assertTrue(!SbwGameTestVehicle.hasCreativeVehicleAmmo(newVehicle),
                            "A new disabled mob granted creative ammunition");
                    helper.assertTrue(Math.abs(newMob.getAttributeValue(Attributes.FOLLOW_RANGE) - 32.0D) < 0.001D,
                            "A new disabled mob received the mounted target-range bonus");
                    helper.succeed();
                } finally {
                    fixture.close();
                }
            });
        });
    }

    @GameTest(batch = "config_lifecycle_enable", template = "empty", timeoutTicks = 40)
    public static void hotEnableLeavesHistoricalDisabledMobsUnchanged(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, false);
        CommonConfig.ENABLE_VEHICLE_AI.set(true);
        helper.runAfterDelay(8, () -> {
            try {
                helper.assertTrue(vehicleGoals(fixture.driver) == 0, "Hot enable injected AI into a historical mob");
                helper.assertTrue(!SbwGameTestVehicle.hasCreativeVehicleAmmo(fixture.vehicle),
                        "Hot enable granted historical disabled mobs creative ammunition");
                fixture.driver.stopRiding();
                helper.assertTrue(fixture.driver.startRiding(fixture.vehicle, true), "Historical mob could not remount");
                helper.assertTrue(Math.abs(fixture.driver.getAttributeValue(Attributes.FOLLOW_RANGE) - 32.0D) < 0.001D,
                        "Remounting activated a historical disabled mob");
                Entity newVehicle = fixture.vehicle(12.0D);
                Mob newMob = fixture.mob(12.0D);
                helper.assertTrue(newMob.startRiding(newVehicle, true), "New enabled mob could not mount");
                helper.assertTrue(vehicleGoals(newMob) > 0, "New mob did not receive AI after hot enable");
                helper.assertTrue(SbwGameTestVehicle.hasCreativeVehicleAmmo(newVehicle),
                        "New enabled mob did not receive its ammunition policy");
                helper.assertTrue(Math.abs(newMob.getAttributeValue(Attributes.FOLLOW_RANGE) - 80.0D) < 0.001D,
                        "New enabled mob did not receive the mounted range");
                helper.succeed();
            } finally {
                fixture.close();
            }
        });
    }

    @GameTest(batch = "config_lifecycle_range", template = "empty", timeoutTicks = 40)
    public static void rangeSnapshotRefreshesOnlyOnRemountEvenAfterHotDisable(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, true);
        CommonConfig.VEHICLE_TARGET_RANGE.set(112.0D);
        CommonConfig.ENABLE_VEHICLE_AI.set(false);
        helper.runAfterDelay(8, () -> {
            try {
                helper.assertTrue(Math.abs(fixture.driver.getAttributeValue(Attributes.FOLLOW_RANGE) - 80.0D) < 0.001D,
                        "A mounted range snapshot changed without remounting");
                fixture.driver.stopRiding();
                helper.assertTrue(Math.abs(fixture.driver.getAttributeValue(Attributes.FOLLOW_RANGE) - 32.0D) < 0.001D,
                        "Dismount failed to remove the historical mounted bonus");
                helper.assertTrue(fixture.driver.startRiding(fixture.vehicle, true), "Historical AI could not remount");
                helper.assertTrue(Math.abs(fixture.driver.getAttributeValue(Attributes.FOLLOW_RANGE) - 112.0D) < 0.001D,
                        "Remount did not refresh the range snapshot while the global switch was disabled");
                helper.succeed();
            } finally {
                fixture.close();
            }
        });
    }

    @GameTest(batch = "config_lifecycle_dismount_enabled", template = "empty", timeoutTicks = 40)
    public static void enabledDismountRemovesOnlyTheVehicleRangeBonus(GameTestHelper helper) {
        verifyDismount(helper, true);
    }

    @GameTest(batch = "config_lifecycle_dismount_disabled", template = "empty", timeoutTicks = 40)
    public static void disabledDismountRemovesOnlyTheVehicleRangeBonus(GameTestHelper helper) {
        verifyDismount(helper, false);
    }

    @GameTest(batch = "config_lifecycle_cancelled_dismount", template = "empty", timeoutTicks = 40)
    public static void cancelledDismountPreservesTheMountedSnapshot(GameTestHelper helper) {
        for (boolean enabled : new boolean[]{true, false}) {
            try (Fixture fixture = new Fixture(helper, true)) {
                CommonConfig.ENABLE_VEHICLE_AI.set(enabled);
                Consumer<EntityMountEvent> cancel = event -> {
                    if (event.isDismounting() && event.getEntityMounting() == fixture.driver) event.setCanceled(true);
                };
                MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, cancel);
                try {
                    fixture.driver.stopRiding();
                    helper.assertTrue(fixture.driver.getVehicle() == fixture.vehicle,
                            "Canceled dismount unexpectedly detached the passenger");
                    helper.assertTrue(Math.abs(fixture.driver.getAttributeValue(Attributes.FOLLOW_RANGE) - 80.0D) < 0.001D,
                            "Canceled dismount removed the active range snapshot");
                } finally {
                    MinecraftForge.EVENT_BUS.unregister(cancel);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(batch = "config_lifecycle_navigation", template = "empty", timeoutTicks = 40)
    public static void navigationKeepsTheAdmissionSnapshotAcrossHotSwitches(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, true)) {
            CommonConfig.ENABLE_VEHICLE_AI.set(false);
            Path historicalPath = path(fixture.driver);
            helper.assertTrue(fixture.driver.getNavigation().moveTo(historicalPath, 1.0D),
                    "Historical AI rejected a valid non-combat vehicle path");
            helper.assertTrue(fixture.driver.getNavigation().getPath() != historicalPath,
                    "Historical AI stopped capturing vehicle navigation after hot disable");
            Entity newVehicle = fixture.vehicle(12.0D);
            Mob disabled = fixture.mob(12.0D);
            helper.assertTrue(disabled.startRiding(newVehicle, true), "Disabled navigation fixture could not mount");
            CommonConfig.ENABLE_VEHICLE_AI.set(true);
            Path nativePath = path(disabled);
            helper.assertTrue(disabled.getNavigation().moveTo(nativePath, 1.0D),
                    "Historical disabled mob rejected native navigation");
            helper.assertTrue(disabled.getNavigation().getPath() == nativePath,
                    "Hot enable intercepted a historical disabled mob's native path");
            helper.succeed();
        }
    }

    @GameTest(batch = "config_lifecycle_reload", template = "empty", timeoutTicks = 40)
    public static void savedMobsUseTheCurrentSwitchWhenLoadedAgain(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, true)) {
            CompoundTag saved = save(fixture.driver);
            fixture.driver.stopRiding();
            fixture.driver.discard();
            CommonConfig.ENABLE_VEHICLE_AI.set(false);
            Mob disabled = fixture.loadMob(saved);
            helper.assertTrue(vehicleGoals(disabled) == 0, "Saved AI admission survived loading with AI disabled");
            helper.assertTrue(disabled.startRiding(fixture.vehicle, true), "Reloaded disabled mob could not mount");
            helper.assertTrue(!SbwGameTestVehicle.hasCreativeVehicleAmmo(fixture.vehicle),
                    "Reloaded disabled mob retained creative ammunition");
            CompoundTag disabledSave = save(disabled);
            disabled.stopRiding();
            disabled.discard();
            CommonConfig.ENABLE_VEHICLE_AI.set(true);
            Mob enabled = fixture.loadMob(disabledSave);
            helper.assertTrue(vehicleGoals(enabled) > 0, "Saved disabled mob did not receive AI when reloaded");
            helper.assertTrue(enabled.startRiding(fixture.vehicle, true), "Reloaded enabled mob could not mount");
            helper.assertTrue(Math.abs(enabled.getAttributeValue(Attributes.FOLLOW_RANGE) - 80.0D) < 0.001D,
                    "Reloaded enabled mob did not acquire a fresh range snapshot");
            helper.succeed();
        }
    }

    @GameTest(batch = "config_lifecycle_loaded_passenger", template = "empty", timeoutTicks = 40)
    public static void restoredPassengersReceiveAFreshRangeSnapshot(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, true)) {
            CompoundTag saved = save(fixture.vehicle);
            fixture.driver.stopRiding();
            fixture.driver.discard();
            fixture.vehicle.discard();
            CommonConfig.VEHICLE_TARGET_RANGE.set(112.0D);
            Entity restored = EntityType.loadEntityRecursive(saved, fixture.level, entity -> entity);
            helper.assertTrue(restored != null && restored.getPassengers().size() == 1,
                    "Vehicle NBT did not restore its passenger before level admission");
            fixture.entities.add(restored);
            fixture.entities.addAll(restored.getPassengers());
            fixture.level.addFreshEntityWithPassengers(restored);
            Mob passenger = (Mob) restored.getPassengers().get(0);
            helper.assertTrue(vehicleGoals(passenger) > 0, "Restored passenger did not receive vehicle AI");
            helper.assertTrue(Math.abs(passenger.getAttributeValue(Attributes.FOLLOW_RANGE) - 112.0D) < 0.001D,
                    "Restored passenger retained or missed its range snapshot");
            helper.succeed();
        }
    }

    private static void verifyDismount(GameTestHelper helper, boolean enabled) {
        try (Fixture fixture = new Fixture(helper, true)) {
            AttributeModifier external = new AttributeModifier(UUID.randomUUID(), "External range", 5.0D,
                    AttributeModifier.Operation.ADDITION);
            fixture.driver.getAttribute(Attributes.FOLLOW_RANGE).addTransientModifier(external);
            CommonConfig.ENABLE_VEHICLE_AI.set(enabled);
            fixture.driver.stopRiding();
            helper.assertTrue(!fixture.driver.isPassenger(), "Dismount did not complete");
            helper.assertTrue(Math.abs(fixture.driver.getAttributeValue(Attributes.FOLLOW_RANGE) - 37.0D) < 0.001D,
                    "Dismount retained the vehicle bonus or removed another mod's range modifier");
            helper.assertTrue(fixture.driver.getAttribute(Attributes.FOLLOW_RANGE).getModifier(external.getId()) != null,
                    "Dismount removed an unrelated modifier");
            helper.succeed();
        }
    }

    private static Path path(Mob mob) {
        BlockPos origin = mob.blockPosition();
        return new Path(List.of(new Node(origin.getX(), origin.getY(), origin.getZ()),
                new Node(origin.getX(), origin.getY(), origin.getZ() + 24)), origin.offset(0, 0, 24), true);
    }

    private static long vehicleGoals(Mob mob) {
        return mob.goalSelector.getAvailableGoals().stream()
                .filter(goal -> goal.getGoal() instanceof VehicleAiGoal).count();
    }

    private static boolean weaponRunning(Mob mob) {
        return mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(goal -> goal.isRunning() && goal.getGoal() instanceof VehicleWeaponGoal);
    }

    private static CompoundTag save(Entity entity) {
        CompoundTag saved = entity.saveWithoutId(new CompoundTag());
        saved.putString("id", ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString());
        return saved;
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos origin;
        private final List<Entity> entities = new ArrayList<>();
        private final List<BlockPos> chunks = new ArrayList<>();
        private final boolean previousEnabled = CommonConfig.ENABLE_VEHICLE_AI.get();
        private final boolean previousAmmo = CommonConfig.VEHICLE_AI_NEEDS_AMMO.get();
        private final double previousRange = CommonConfig.VEHICLE_TARGET_RANGE.get();
        private final double previousNatural = CommonConfig.NATURAL_VEHICLE_CHANCE.get();
        private final Entity vehicle;
        private final Mob driver;
        private boolean closed;

        private Fixture(GameTestHelper helper, boolean enabled) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.origin = new BlockPos(20000 + ARENA.getAndIncrement() * 160, 5, 20000);
            CommonConfig.ENABLE_VEHICLE_AI.set(enabled);
            CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(false);
            CommonConfig.VEHICLE_TARGET_RANGE.set(80.0D);
            CommonConfig.NATURAL_VEHICLE_CHANCE.set(0.0D);
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos chunk = new BlockPos((this.origin.getX() >> 4) + x, 0, (this.origin.getZ() >> 4) + z);
                    this.level.setChunkForced(chunk.getX(), chunk.getZ(), true);
                    this.chunks.add(chunk);
                }
            }
            for (int x = -20; x <= 28; x++) {
                for (int z = -20; z <= 28; z++) {
                    this.level.setBlock(this.origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
                }
            }
            this.vehicle = vehicle(0.0D);
            this.driver = mob(0.0D);
            helper.assertTrue(this.driver.startRiding(this.vehicle, true), "Lifecycle driver could not mount");
            helper.assertTrue((vehicleGoals(this.driver) > 0) == enabled, "Initial AI admission does not match the switch");
            double expectedRange = enabled ? 80.0D : 32.0D;
            helper.assertTrue(Math.abs(this.driver.getAttributeValue(Attributes.FOLLOW_RANGE) - expectedRange) < 0.001D,
                    "Initial mounted range does not match admission");
        }

        private Entity vehicle(double offset) {
            Entity vehicle = ForgeRegistries.ENTITY_TYPES.getValue(
                    new ResourceLocation("superbwarfare", "m_1a_2")).create(this.level);
            helper.assertTrue(vehicle != null, "Missing lifecycle vehicle");
            vehicle.setPos(Vec3.atBottomCenterOf(this.origin).add(offset, 0.0D, 0.0D));
            helper.assertTrue(this.level.addFreshEntity(vehicle), "Vehicle fixture was rejected");
            SbwGameTestVehicle.prepareSmokeFixture(vehicle, 1, 0);
            this.entities.add(vehicle);
            return vehicle;
        }

        private Mob mob(double offset) {
            Mob mob = EntityType.VINDICATOR.create(this.level);
            mob.setPos(Vec3.atBottomCenterOf(this.origin).add(offset, 0.0D, 0.0D));
            mob.setPersistenceRequired();
            mob.setInvulnerable(true);
            mob.setNoAi(true);
            mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(32.0D);
            helper.assertTrue(this.level.addFreshEntity(mob), "Mob fixture was rejected");
            this.entities.add(mob);
            return mob;
        }

        private Mob enemy() {
            Mob enemy = EntityType.VILLAGER.create(this.level);
            enemy.setPos(this.vehicle.position().add(0.0D, 0.0D, 18.0D));
            enemy.setNoAi(true);
            enemy.setPersistenceRequired();
            enemy.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10000, 255, false, false));
            helper.assertTrue(this.level.addFreshEntity(enemy), "Enemy fixture was rejected");
            this.entities.add(enemy);
            return enemy;
        }

        private Mob loadMob(CompoundTag saved) {
            Entity entity = EntityType.loadEntityRecursive(saved, this.level, loaded -> loaded);
            helper.assertTrue(entity instanceof Mob, "Could not restore mob NBT");
            this.entities.add(entity);
            this.level.addLegacyChunkEntities(Stream.of(entity));
            return (Mob) entity;
        }

        @Override
        public void close() {
            if (this.closed) return;
            this.closed = true;
            this.entities.forEach(Entity::discard);
            this.chunks.forEach(chunk -> this.level.setChunkForced(chunk.getX(), chunk.getZ(), false));
            CommonConfig.ENABLE_VEHICLE_AI.set(this.previousEnabled);
            CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(this.previousAmmo);
            CommonConfig.VEHICLE_TARGET_RANGE.set(this.previousRange);
            CommonConfig.NATURAL_VEHICLE_CHANCE.set(this.previousNatural);
        }
    }
}
