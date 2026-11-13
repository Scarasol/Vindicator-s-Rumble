package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.init.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

@GameTestHolder("vindicators_rumble_isolation")
@PrefixGameTestTemplate(false)
public final class VehicleNavigationIsolationGameTests {
    private VehicleNavigationIsolationGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void untaggedGroundPassengerKeepsVanillaPath(GameTestHelper helper) {
        verifyNavigationOwnership(helper, "sodayo_pick_up_hmg", false, true);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void untaggedHelicopterPassengerKeepsVanillaPath(GameTestHelper helper) {
        verifyNavigationOwnership(helper, "ah_6", false, true);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void taggedGroundDriverStillDelegatesNavigation(GameTestHelper helper) {
        verifyNavigationOwnership(helper, "sodayo_pick_up_hmg", true, false);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void taggedHelicopterPassengerStillRejectsGroundPath(GameTestHelper helper) {
        verifyNavigationOwnership(helper, "ah_6", true, false);
    }

    private static void verifyNavigationOwnership(GameTestHelper helper, String vehicleName,
                                                   boolean tagged, boolean vanillaOwnsPath) {
        helper.assertTrue(CommonConfig.ENABLE_VEHICLE_AI.get(), "Fixture requires vehicle AI enabled");
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));
        EntityType<?> vehicleType = ForgeRegistries.ENTITY_TYPES.getValue(
                new ResourceLocation("superbwarfare", vehicleName));
        helper.assertTrue(vehicleType != null, "Missing fixture vehicle");
        Entity vehicle = vehicleType.create(helper.getLevel());
        Mob passenger = (tagged ? EntityType.VINDICATOR : EntityType.COW).create(helper.getLevel());
        helper.assertTrue(vehicle != null && passenger != null, "Could not create navigation fixtures");
        try {
            vehicle.moveTo(origin.getX(), origin.getY(), origin.getZ(), 0.0F, 0.0F);
            passenger.moveTo(origin.getX(), origin.getY(), origin.getZ(), 0.0F, 0.0F);
            helper.getLevel().addFreshEntity(vehicle);
            helper.getLevel().addFreshEntity(passenger);
            helper.assertTrue(passenger.getType().is(ModTags.EntityTypes.VEHICLE_AI_USERS) == tagged,
                    "Fixture tag membership does not match its ownership case");
            var controller = VehicleControllers.create(vehicle).orElseThrow();
            helper.assertTrue(controller.mount(passenger), "Passenger could not enter fixture vehicle");
            Path supplied = new Path(List.of(
                    new Node(origin.getX(), origin.getY(), origin.getZ()),
                    new Node(origin.getX() + 24, origin.getY(), origin.getZ())),
                    origin.offset(24, 0, 0), true);
            boolean accepted = passenger.getNavigation().moveTo(supplied, 1.0D);
            helper.assertTrue((passenger.getNavigation().getPath() == supplied) == vanillaOwnsPath,
                    "Vehicle integration took the wrong ownership of the vanilla navigation path");
            helper.assertTrue(accepted == (vanillaOwnsPath || !controller.supportsFlightControl()),
                    "Navigation acceptance did not match the owning movement family");
        } finally {
            passenger.discard();
            vehicle.discard();
        }
        helper.succeed();
    }
}
