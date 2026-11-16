package com.scarasol.vindicatorsrumble.gametest.accuracy;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.scarasol.pillagers_gun.config.CommonConfig;
import com.scarasol.pillagers_gun.event.server.InaccuracyEvent;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@GameTestHolder("pillagers_gun")
@PrefixGameTestTemplate(false)
public final class VehicleAccuracyGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();
    private static final List<Fixture> LIVE = new ArrayList<>();

    @AfterBatch(batch = "vehicle_accuracy")
    public static void cleanFixtures(ServerLevel level) {
        List.copyOf(LIVE).forEach(Fixture::close);
    }

    @GameTest(batch = "vehicle_accuracy", templateNamespace = "pillagers_gun", template = "empty", timeoutTicks = 40)
    public static void eventResultChangesOnlyTheCurrentShot(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "sodayo_pick_up_hmg", false);
        helper.runAfterDelay(1, () -> {
            try (fixture) {
                fixture.override = 0.375F;
                double originalBase = fixture.data.get(GunProp.SPREAD);
                ShootParameters input = fixture.parameters(fixture.shooter, fixture.vehicle, 1.25D, true);
                ShootParameters actual = fixture.shoot(input);
                helper.assertTrue(fixture.events == 1 && actual.spread == 0.375D,
                        "The existing accuracy event must modify the native shot exactly once");
                helper.assertTrue(actual.ammoSupplier == input.ammoSupplier && actual.shooter == input.shooter
                                && actual.level == input.level && actual.data == input.data
                                && actual.shootPosition == input.shootPosition
                                && actual.shootDirection == input.shootDirection && actual.zoom == input.zoom
                                && actual.targetEntityUUID == input.targetEntityUUID && actual.targetPos == input.targetPos,
                        "Accuracy compatibility must preserve every other native shooting parameter");
                helper.assertTrue(fixture.data.get(GunProp.SPREAD) == originalBase,
                        "Per-shot accuracy must not mutate persistent weapon spread");
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "vehicle_accuracy", templateNamespace = "pillagers_gun", template = "empty", timeoutTicks = 40)
    public static void dynamicSettingReachesVehicleWeapons(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "sodayo_pick_up_hmg", false);
        helper.runAfterDelay(1, () -> {
            try (fixture) {
                CommonConfig.DYNAMIC_INACCURACY.set(true);
                ShootParameters actual = fixture.shoot(fixture.parameters(fixture.shooter, fixture.vehicle, 1.25D, true));
                helper.assertTrue(fixture.events == 1 && fixture.eventSpread != 1.25F
                                && actual.spread == fixture.eventSpread,
                        "Enabled dynamic accuracy must reach vehicle weapons through the existing event");
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "vehicle_accuracy", templateNamespace = "pillagers_gun", template = "empty", timeoutTicks = 40)
    public static void disabledDynamicSettingPreservesNativePrecision(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "sodayo_pick_up_hmg", false)) {
            CommonConfig.DYNAMIC_INACCURACY.set(false);
            ShootParameters input = fixture.parameters(fixture.shooter, fixture.vehicle, 1.234567890123D, false);
            helper.assertTrue(fixture.shoot(input) == input && fixture.events == 1,
                    "Disabled dynamic accuracy must preserve SBW's original double spread and parameter object");
            helper.assertTrue(fixture.eventTarget == fixture.target,
                    "Position-only vehicle shots must resolve the mob's current target");
        }
        helper.succeed();
    }

    @GameTest(batch = "vehicle_accuracy", templateNamespace = "pillagers_gun", template = "empty", timeoutTicks = 40)
    public static void lockedShotUsesItsOwnTarget(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "sodayo_pick_up_hmg", false);
        helper.runAfterDelay(1, () -> {
            try (fixture) {
                fixture.shooter.setTarget(null);
                fixture.shoot(fixture.parameters(fixture.shooter, fixture.vehicle, 1.25D, true));
                helper.assertTrue(fixture.events == 1 && fixture.eventTarget == fixture.target,
                        "A delayed locked shot must use its locked UUID, not a later mob target");
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "vehicle_accuracy", templateNamespace = "pillagers_gun", template = "empty", timeoutTicks = 40)
    public static void playersHandheldAndUntaggedMobsBypassVehiclePolicy(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "sodayo_pick_up_hmg", false)) {
            ShootParameters handheld = fixture.parameters(fixture.shooter, fixture.shooter, 1.25D, true);
            helper.assertTrue(fixture.shoot(handheld) == handheld,
                    "Mounted handheld fire must not receive a second accuracy event");
            var player = FakePlayerFactory.getMinecraft(helper.getLevel());
            ShootParameters playerShot = fixture.parameters(player, fixture.vehicle, 1.25D, true);
            helper.assertTrue(fixture.shoot(playerShot) == playerShot, "Player vehicle spread must remain native");
            Mob pillager = EntityType.PILLAGER.create(helper.getLevel());
            helper.assertTrue(pillager != null, "Missing untagged mob fixture");
            pillager.setNoAi(true);
            pillager.setPos(fixture.shooter.position());
            pillager.getPersistentData().putBoolean("pillagers_gun:born_with_gun_checked", true);
            helper.getLevel().addFreshEntity(pillager);
            fixture.shooter.stopRiding();
            try {
                helper.assertTrue(VehicleControllers.create(fixture.vehicle).orElseThrow().mount(pillager),
                        "Untagged fixture must be mounted");
                ShootParameters untagged = fixture.parameters(pillager, fixture.vehicle, 1.25D, true);
                helper.assertTrue(fixture.shoot(untagged) == untagged && fixture.events == 0,
                        "Untagged mobs must retain native vehicle spread");
            } finally {
                pillager.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(batch = "vehicle_accuracy", templateNamespace = "pillagers_gun", template = "empty", timeoutTicks = 40)
    public static void targetlessShotsKeepNativeSpread(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "sodayo_pick_up_hmg", false)) {
            fixture.shooter.setTarget(null);
            ShootParameters input = fixture.parameters(fixture.shooter, fixture.vehicle, 1.25D, false);
            helper.assertTrue(fixture.shoot(input) == input && fixture.events == 0,
                    "A shot without an entity target must not acquire an unrelated target for accuracy");
        }
        helper.succeed();
    }

    @GameTest(batch = "vehicle_accuracy", templateNamespace = "pillagers_gun", template = "empty", timeoutTicks = 40)
    public static void nativeSpreadIsMultipliedBeforeDynamicAccuracy(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "sodayo_pick_up_hmg", false)) {
            com.scarasol.vindicatorsrumble.config.CommonConfig.VEHICLE_WEAPON_INACCURACY_MULTIPLIER.set(0.75D);
            CommonConfig.DYNAMIC_INACCURACY.set(true);
            ShootParameters actual = fixture.shoot(fixture.parameters(fixture.shooter, fixture.vehicle, 4.0D, false));
            helper.assertTrue(fixture.events == 1 && fixture.eventBase == 3.0F
                            && fixture.eventSpread != 3.0F && actual.spread == fixture.eventSpread,
                    "Native spread multiplied by the coefficient must enter the dynamic event exactly once");
        }
        helper.succeed();
    }

    @GameTest(batch = "vehicle_accuracy", templateNamespace = "pillagers_gun", template = "empty", timeoutTicks = 40)
    public static void disabledDynamicAccuracyStillUsesSpreadMultiplier(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "sodayo_pick_up_hmg", false)) {
            com.scarasol.vindicatorsrumble.config.CommonConfig.VEHICLE_WEAPON_INACCURACY_MULTIPLIER.set(1.234567890123D);
            CommonConfig.DYNAMIC_INACCURACY.set(false);
            ShootParameters actual = fixture.shoot(fixture.parameters(fixture.shooter, fixture.vehicle, 4.0D, false));
            helper.assertTrue(fixture.events == 1 && actual.spread == 4.938271560492D,
                    "Disabling dynamic accuracy must preserve the scaled spread at its original double precision");
        }
        helper.succeed();
    }

    @GameTest(batch = "vehicle_accuracy", templateNamespace = "pillagers_gun", template = "empty", timeoutTicks = 600)
    public static void tankNaturalAiFireUsesAccuracyEvent(GameTestHelper helper) {
        verifyNaturalFire(helper, "yx_100");
    }

    @GameTest(batch = "vehicle_accuracy", templateNamespace = "pillagers_gun", template = "empty", timeoutTicks = 600)
    public static void pickupNaturalAiFireUsesAccuracyEvent(GameTestHelper helper) {
        verifyNaturalFire(helper, "sodayo_pick_up_hmg");
    }

    private static void verifyNaturalFire(GameTestHelper helper, String vehicle) {
        Fixture fixture = new Fixture(helper, vehicle, true);
        helper.onEachTick(() -> {
            if (helper.getTick() == 580 && fixture.shots().isEmpty()) {
                var controller = VehicleControllers.create(fixture.vehicle).orElseThrow();
                com.mojang.logging.LogUtils.getLogger().info(
                        "Accuracy fixture {} target={} mounted={} seat={} weapons={} running={}",
                        vehicle, fixture.shooter.getTarget(), fixture.shooter.isPassenger(),
                        controller.seatOf(fixture.shooter), controller.weapons(fixture.shooter),
                        fixture.shooter.goalSelector.getAvailableGoals().stream()
                                .filter(goal -> goal.isRunning()).map(goal -> goal.getGoal().getClass().getSimpleName()).toList());
            }
            if (fixture.shots().isEmpty()) return;
            try (fixture) {
                helper.assertTrue(fixture.shooter.getTarget() == fixture.target,
                        "The vehicle shooter must naturally acquire its target");
                helper.assertTrue(fixture.events == fixture.shots().size()
                                && fixture.shots().get(0).spread == 0.375D,
                        "Normal vehicle AI must send each shot through the optional accuracy event exactly once");
                helper.succeed();
            }
        });
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final Mob shooter;
        private final Villager target;
        private final VehicleEntity vehicle;
        private final GunData data;
        private Mob driver;
        private final List<ChunkPos> chunks = new ArrayList<>();
        private final List<Entity> observed = new ArrayList<>();
        private final boolean oldDynamic = CommonConfig.DYNAMIC_INACCURACY.get();
        private final double oldMultiplier = com.scarasol.vindicatorsrumble.config.CommonConfig.VEHICLE_WEAPON_INACCURACY_MULTIPLIER.get();
        private final Consumer<InaccuracyEvent> listener;
        private int events;
        private float eventSpread;
        private float eventBase;
        private Entity eventTarget;
        private Float override;

        private Fixture(GameTestHelper helper, String vehicleId, boolean natural) {
            this.helper = helper;
            com.scarasol.vindicatorsrumble.config.CommonConfig.VEHICLE_WEAPON_INACCURACY_MULTIPLIER.set(1.0D);
            BlockPos anchor = helper.absolutePos(new BlockPos(256 + ARENA.getAndIncrement() * 128, 0, 256));
            ChunkPos center = new ChunkPos(anchor);
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    ChunkPos chunk = new ChunkPos(center.x + x, center.z + z);
                    helper.getLevel().setChunkForced(chunk.x, chunk.z, true);
                    this.chunks.add(chunk);
                }
            }
            int floor = helper.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    anchor.getX(), anchor.getZ());
            this.shooter = EntityType.VINDICATOR.create(helper.getLevel());
            this.target = EntityType.VILLAGER.create(helper.getLevel());
            this.vehicle = (VehicleEntity) ForgeRegistries.ENTITY_TYPES.getValue(
                    new ResourceLocation("superbwarfare", vehicleId)).create(helper.getLevel());
            helper.assertTrue(this.shooter != null && this.target != null && this.vehicle != null,
                    "Missing accuracy fixtures");
            this.vehicle.moveTo(anchor.getX() + 0.5D, floor + 0.1D, anchor.getZ() + 0.5D, 0, 0);
            this.shooter.moveTo(this.vehicle.getX(), this.vehicle.getY(), this.vehicle.getZ(), 0, 0);
            this.shooter.setNoAi(!natural);
            this.shooter.setInvulnerable(true);
            this.shooter.getPersistentData().putBoolean("pillagers_gun:born_with_gun_checked", true);
            this.shooter.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            this.shooter.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64);
            this.target.moveTo(this.vehicle.getX(), floor + 0.1D, this.vehicle.getZ() + 32, 0, 0);
            this.target.setNoAi(true);
            this.target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(4096);
            this.target.setHealth(4096);
            this.vehicle.setInvulnerable(true);
            helper.getLevel().addFreshEntity(this.vehicle);
            helper.getLevel().addFreshEntity(this.target);
            helper.getLevel().addFreshEntity(this.shooter);
            var controller = VehicleControllers.create(this.vehicle).orElseThrow();
            helper.assertTrue(controller.mount(this.shooter), "Could not mount accuracy fixture");
            var seat = controller.seats().stream().filter(candidate -> candidate.hasWeapon())
                    .findFirst().orElseThrow(() -> new AssertionError("Fixture has no mounted weapon seat"));
            helper.assertTrue(controller.moveToSeat(this.shooter, seat), "Could not occupy mounted weapon seat");
            if (natural && !seat.canDrive()) {
                this.driver = EntityType.VINDICATOR.create(helper.getLevel());
                helper.assertTrue(this.driver != null, "Missing passive driver fixture");
                this.driver.setPos(this.vehicle.position());
                this.driver.setNoAi(true);
                this.driver.setInvulnerable(true);
                this.driver.getPersistentData().putBoolean("pillagers_gun:born_with_gun_checked", true);
                helper.getLevel().addFreshEntity(this.driver);
                helper.assertTrue(controller.mount(this.driver), "Could not fill the driver seat");
            }
            if (!natural) this.shooter.setTarget(this.target);
            this.data = this.vehicle.getGunData(seat.id());
            helper.assertTrue(this.data != null, "Missing vehicle weapon data");
            this.data.ammo.set(100);
            this.override = natural ? 0.375F : null;
            observe(this.shooter);
            this.listener = event -> {
                if (!this.observed.contains(event.getShooter())) return;
                this.events++;
                this.eventBase = event.getOldInaccuracy();
                this.eventSpread = event.getNewInaccuracy();
                this.eventTarget = event.getTarget();
                if (this.override != null) event.setNewInaccuracy(this.override);
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, InaccuracyEvent.class, this.listener);
            LIVE.add(this);
        }

        private void observe(Entity entity) {
            if (this.observed.contains(entity)) return;
            this.observed.add(entity);
            ShotCapture.observe(entity);
        }

        private List<ShootParameters> shots() {
            return ShotCapture.observe(this.shooter);
        }

        private ShootParameters parameters(Entity shooter, Entity supplier, double spread, boolean locked) {
            return new ShootParameters(supplier, shooter, this.helper.getLevel(), this.vehicle.position().add(0, 3, 0),
                    new Vec3(0, 0, 1), this.data, spread, true, locked ? this.target.getUUID() : null,
                    locked ? null : this.target.position());
        }

        private ShootParameters shoot(ShootParameters parameters) {
            observe(parameters.shooter);
            List<ShootParameters> shots = ShotCapture.observe(parameters.shooter);
            int before = shots.size();
            this.data.shoot(parameters);
            this.helper.assertTrue(shots.size() == before + 1, "Native SBW shooting must receive one parameter object");
            return shots.get(before);
        }

        @Override
        public void close() {
            if (!LIVE.remove(this)) return;
            MinecraftForge.EVENT_BUS.unregister(this.listener);
            CommonConfig.DYNAMIC_INACCURACY.set(this.oldDynamic);
            com.scarasol.vindicatorsrumble.config.CommonConfig.VEHICLE_WEAPON_INACCURACY_MULTIPLIER.set(this.oldMultiplier);
            this.observed.forEach(ShotCapture::forget);
            this.shooter.discard();
            if (this.driver != null) this.driver.discard();
            this.target.discard();
            this.vehicle.discard();
            for (ChunkPos chunk : this.chunks) this.helper.getLevel().setChunkForced(chunk.x, chunk.z, false);
        }
    }
}
