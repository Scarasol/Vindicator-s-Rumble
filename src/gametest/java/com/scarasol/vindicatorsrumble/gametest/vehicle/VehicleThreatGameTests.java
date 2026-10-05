package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleThreats;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwGameTestVehicle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("vindicators_rumble_recruits")
@PrefixGameTestTemplate(false)
public final class VehicleThreatGameTests {
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void neutralReceiverKeepsIndependentSourcesAndPassengerReports(GameTestHelper helper) {
        Entity vehicle = spawn(helper, EntityType.MINECART, 1);
        Entity passenger = spawn(helper, EntityType.VILLAGER, 2);
        Entity first = spawn(helper, EntityType.ARMOR_STAND, 3);
        Entity second = spawn(helper, EntityType.ARMOR_STAND, 4);
        helper.assertTrue(passenger.startRiding(vehicle, true), "Passenger fixture could not mount");
        VehicleThreats.report(first, passenger, VehicleThreats.Stage.LOCKED, 10);
        VehicleThreats.report(second, vehicle, VehicleThreats.Stage.ACQUIRING, 10);
        helper.assertTrue(VehicleThreats.strongest(vehicle).orElseThrow().source() == first,
                "Passenger lock did not reach the neutral root vehicle");
        first.discard();
        helper.assertTrue(VehicleThreats.strongest(vehicle).orElseThrow().source() == second,
                "Removing one attacker cleared another attacker's report");
        passenger.discard();
        helper.assertTrue(VehicleThreats.strongest(vehicle).isPresent(),
                "Passenger removal cleared the vehicle's independent reports");
        vehicle.discard();
        second.discard();
        helper.assertTrue(VehicleThreats.strongest(vehicle).isEmpty(), "Removed receiver retained reports");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void retargetAndUnlockDoNotLeaveACompletedLockBehind(GameTestHelper helper) {
        Entity first = spawn(helper, EntityType.MINECART, 1);
        Entity second = spawn(helper, EntityType.MINECART, 2);
        Entity source = spawn(helper, EntityType.ARMOR_STAND, 3);
        VehicleThreats.report(source, first, VehicleThreats.Stage.LOCKED, 10);
        VehicleThreats.report(source, second, VehicleThreats.Stage.ACQUIRING, 10);
        helper.assertTrue(VehicleThreats.strongest(first).isEmpty(), "Retarget left a stale lock");
        helper.assertTrue(VehicleThreats.strongest(second).orElseThrow().stage() == VehicleThreats.Stage.ACQUIRING,
                "Retarget incorrectly preserved completed lock");
        VehicleThreats.clearSource(source);
        helper.assertTrue(VehicleThreats.strongest(second).isEmpty(), "Explicit unlock retained a report");
        first.discard();
        second.discard();
        source.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void missingRefreshExpiresWithoutAController(GameTestHelper helper) {
        Entity target = spawn(helper, EntityType.MINECART, 1);
        Entity source = spawn(helper, EntityType.ARMOR_STAND, 2);
        VehicleThreats.report(source, target, VehicleThreats.Stage.LOCKED, 3);
        helper.assertTrue(VehicleThreats.strongest(target).isPresent(), "Fresh report disappeared");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(VehicleThreats.strongest(target).isEmpty(), "Unrefreshed lock did not expire");
            target.discard();
            source.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nativeMissileRetargetAndLossUpdateTheGenericReceiver(GameTestHelper helper) {
        Entity first = spawn(helper, EntityType.MINECART, 1);
        Entity second = spawn(helper, EntityType.MINECART, 2);
        Entity owner = spawn(helper, EntityType.ARMOR_STAND, 3);
        Entity missile = SbwGameTestVehicle.trackedMissile(helper.getLevel(), owner, first,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 30, 3))));
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(VehicleThreats.strongest(first).orElseThrow().source() == missile,
                    "Native guided missile did not publish an incoming report");
            SbwGameTestVehicle.retargetMissile(missile, second);
            helper.assertTrue(VehicleThreats.strongest(first).isEmpty(), "Native retarget retained the old receiver");
            helper.assertTrue(VehicleThreats.strongest(second).orElseThrow().stage() == VehicleThreats.Stage.INCOMING,
                    "Native retarget did not report the new receiver");
            SbwGameTestVehicle.loseMissileTarget(missile);
            helper.assertTrue(VehicleThreats.strongest(second).isEmpty(), "Native guidance loss retained the threat");
            first.discard();
            second.discard();
            owner.discard();
            missile.discard();
            helper.succeed();
        });
    }

    private static Entity spawn(GameTestHelper helper, EntityType<?> type, int x) {
        Entity entity = type.create(helper.getLevel());
        entity.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(x, 4, 1))));
        entity.setNoGravity(true);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }
}
