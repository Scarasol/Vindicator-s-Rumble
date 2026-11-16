package com.scarasol.vindicatorsrumble.gametest.accuracy;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder("vindicators_rumble")
@PrefixGameTestTemplate(false)
public final class VehicleBaseAccuracyGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void differentVehicleWeaponsScaleTheirNativeSpread(GameTestHelper helper) {
        try (Fixture pickup = new Fixture(helper, "sodayo_pick_up_hmg");
             Fixture tank = new Fixture(helper, "yx_100")) {
            CommonConfig.VEHICLE_WEAPON_INACCURACY_MULTIPLIER.set(0.75D);
            helper.assertTrue(pickup.shoot(pickup.parameters(pickup.shooter, pickup.vehicle, 0.25D)).spread == 0.1875D
                            && tank.shoot(tank.parameters(tank.shooter, tank.vehicle, 4.0D)).spread == 3.0D,
                    "The shared multiplier must preserve the native spread differences between vehicle weapons");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void zeroAndLiveConfigChangesApplyWithoutAnEntityTarget(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "sodayo_pick_up_hmg")) {
            CommonConfig.VEHICLE_WEAPON_INACCURACY_MULTIPLIER.set(0.0D);
            helper.assertTrue(fixture.shoot(fixture.parameters(fixture.shooter, fixture.vehicle, 2.0D)).spread == 0.0D,
                    "Zero must remove random spread even for a position-only shot without an entity target");
            CommonConfig.VEHICLE_WEAPON_INACCURACY_MULTIPLIER.set(1.125D);
            helper.assertTrue(fixture.shoot(fixture.parameters(fixture.shooter, fixture.vehicle, 2.0D)).spread == 2.25D,
                    "The next shot must use a changed setting without remounting or rebuilding gun data");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void identityMultiplierPreservesNativeSpreadWithoutChangingGunData(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "sodayo_pick_up_hmg")) {
            double originalBase = fixture.data.get(GunProp.SPREAD);
            CommonConfig.VEHICLE_WEAPON_INACCURACY_MULTIPLIER.set(0.75D);
            ShootParameters input = fixture.parameters(fixture.shooter, fixture.vehicle, 1.234567890123D);
            ShootParameters actual = fixture.shoot(input);
            helper.assertTrue(actual.ammoSupplier == input.ammoSupplier && actual.shooter == input.shooter
                            && actual.level == input.level && actual.data == input.data && actual.zoom == input.zoom
                            && actual.shootPosition == input.shootPosition && actual.shootDirection == input.shootDirection
                            && actual.targetEntityUUID == input.targetEntityUUID && actual.targetPos == input.targetPos,
                    "The spread multiplier must preserve every other shooting parameter");
            CommonConfig.VEHICLE_WEAPON_INACCURACY_MULTIPLIER.set(1.0D);
            helper.assertTrue(fixture.shoot(input) == input && fixture.data.get(GunProp.SPREAD) == originalBase,
                    "An identity multiplier must preserve native precision and must not leave persistent gun changes");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void playersHandheldAndUntaggedMobsKeepTheirOriginalSpread(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, "sodayo_pick_up_hmg")) {
            CommonConfig.VEHICLE_WEAPON_INACCURACY_MULTIPLIER.set(0.75D);
            ShootParameters handheld = fixture.parameters(fixture.shooter, fixture.shooter, 2.0D);
            ShootParameters player = fixture.parameters(FakePlayerFactory.getMinecraft(helper.getLevel()), fixture.vehicle, 2.0D);
            helper.assertTrue(fixture.shoot(handheld) == handheld && fixture.shoot(player) == player,
                    "The vehicle spread multiplier must not alter handheld guns or player shots");
            Mob untagged = EntityType.COW.create(helper.getLevel());
            helper.assertTrue(untagged != null, "Missing untagged fixture");
            untagged.setNoAi(true);
            untagged.setPos(fixture.vehicle.position());
            helper.getLevel().addFreshEntity(untagged);
            fixture.shooter.stopRiding();
            try {
                helper.assertTrue(VehicleControllers.create(fixture.vehicle).orElseThrow().mount(untagged),
                        "Could not mount untagged fixture");
                ShootParameters input = fixture.parameters(untagged, fixture.vehicle, 2.0D);
                helper.assertTrue(fixture.shoot(input) == input, "Untagged vehicle passengers must retain native spread");
            } finally {
                untagged.discard();
            }
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final Mob shooter;
        private final VehicleEntity vehicle;
        private final GunData data;
        private final List<Entity> observed = new ArrayList<>();
        private final double oldMultiplier = CommonConfig.VEHICLE_WEAPON_INACCURACY_MULTIPLIER.get();

        private Fixture(GameTestHelper helper, String vehicleId) {
            this.helper = helper;
            BlockPos origin = helper.absolutePos(new BlockPos(32 + ARENA.getAndIncrement() * 16, 4, 32));
            this.vehicle = (VehicleEntity) ForgeRegistries.ENTITY_TYPES.getValue(
                    new ResourceLocation("superbwarfare", vehicleId)).create(helper.getLevel());
            this.shooter = EntityType.VINDICATOR.create(helper.getLevel());
            helper.assertTrue(this.vehicle != null && this.shooter != null, "Missing vehicle accuracy fixtures");
            this.vehicle.moveTo(origin.getX(), origin.getY(), origin.getZ(), 0, 0);
            this.shooter.setPos(this.vehicle.position());
            this.shooter.setNoAi(true);
            this.shooter.getPersistentData().putBoolean("pillagers_gun:born_with_gun_checked", true);
            helper.getLevel().addFreshEntity(this.vehicle);
            helper.getLevel().addFreshEntity(this.shooter);
            var controller = VehicleControllers.create(this.vehicle).orElseThrow();
            helper.assertTrue(controller.mount(this.shooter), "Could not mount shooter");
            var seat = controller.seats().stream().filter(candidate -> candidate.hasWeapon()).findFirst().orElseThrow();
            helper.assertTrue(controller.moveToSeat(this.shooter, seat), "Could not occupy a vehicle weapon seat");
            this.data = this.vehicle.getGunData(seat.id());
            helper.assertTrue(this.data != null, "Missing mounted weapon data");
            this.data.ammo.set(100);
        }

        private ShootParameters parameters(Entity shooter, Entity supplier, double spread) {
            return new ShootParameters(supplier, shooter, this.helper.getLevel(), this.vehicle.position().add(0, 3, 0),
                    new Vec3(0, 0, 1), this.data, spread, true, null, this.vehicle.position().add(0, 3, 32));
        }

        private ShootParameters shoot(ShootParameters parameters) {
            if (!this.observed.contains(parameters.shooter)) this.observed.add(parameters.shooter);
            List<ShootParameters> shots = ShotCapture.observe(parameters.shooter);
            int before = shots.size();
            this.data.shoot(parameters);
            this.helper.assertTrue(shots.size() == before + 1, "Native SBW shooting must receive exactly one shot");
            return shots.get(before);
        }

        @Override
        public void close() {
            CommonConfig.VEHICLE_WEAPON_INACCURACY_MULTIPLIER.set(this.oldMultiplier);
            this.observed.forEach(ShotCapture::forget);
            this.shooter.discard();
            this.vehicle.discard();
        }
    }
}
