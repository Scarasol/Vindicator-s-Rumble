package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.mojang.authlib.GameProfile;
import com.scarasol.vindicatorsrumble.VindicatorsRumbleMod;
import com.scarasol.vindicatorsrumble.api.vehicle.SeatHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwGameTestVehicle;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.util.AllegianceUtil;
import com.scarasol.vindicatorsrumble.util.VehicleAiTuning;
import com.scarasol.vindicatorsrumble.util.VehicleClaimManager;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import tallestegg.guardvillagers.entities.Guard;

import java.util.Optional;
import java.util.UUID;

@GameTestHolder(VindicatorsRumbleMod.MODID)
@PrefixGameTestTemplate(false)
public final class PlayerVehicleAllianceGameTests {
    private static final ResourceLocation GUARD = new ResourceLocation("guardvillagers", "guard");
    private static final ResourceLocation RECRUIT = new ResourceLocation("recruits", "recruit");
    private static final ResourceLocation VEHICLE = new ResourceLocation("superbwarfare", "sodayo_pick_up_hmg");
    private static final ResourceLocation HELICOPTER = new ResourceLocation("superbwarfare", "ah_6");
    private static final ResourceLocation VINDICATOR = new ResourceLocation("minecraft", "vindicator");
    private static final ResourceLocation COW = new ResourceLocation("minecraft", "cow");

    private PlayerVehicleAllianceGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void sharedPlayerAllianceContract(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = player(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        Guard guard = spawn(level, GUARD, helper.absolutePos(new BlockPos(3, 2, 2)), Guard.class);
        AbstractRecruitEntity recruit = spawn(level, RECRUIT, helper.absolutePos(new BlockPos(4, 2, 2)), AbstractRecruitEntity.class);
        boolean strict = CommonConfig.STRICT_PLAYER_ALLIANCE.get();

        try {
            CommonConfig.STRICT_PLAYER_ALLIANCE.set(true);
            helper.assertFalse(AllegianceUtil.isSameSide(player, guard), "Strict mode treated a neutral Guard as friendly");
            helper.assertFalse(AllegianceUtil.isSameSide(player, recruit), "Strict mode treated an unowned Recruit as friendly");

            guard.setOwnerId(player.getUUID());
            recruit.setIsOwned(true);
            recruit.setOwnerUUID(Optional.of(player.getUUID()));
            helper.assertTrue(AllegianceUtil.isSameSide(player, guard), "Strict mode rejected the player's Guard");
            helper.assertTrue(AllegianceUtil.isSameSide(player, recruit), "Strict mode rejected the player's Recruit");
            helper.assertTrue(AllegianceUtil.shouldProjectileIgnoreTarget(player, recruit),
                    "Friendly fire did not use the shared player alliance");

            guard.setOwnerId(null);
            recruit.setIsOwned(false);
            recruit.setOwnerUUID(Optional.empty());
            CommonConfig.STRICT_PLAYER_ALLIANCE.set(false);
            helper.assertTrue(AllegianceUtil.isSameSide(player, guard), "Lenient mode rejected a neutral Guard");
            helper.assertTrue(AllegianceUtil.isSameSide(player, recruit), "Lenient mode rejected a neutral Recruit");

            guard.setTarget(player);
            recruit.setTarget(player);
            helper.assertFalse(AllegianceUtil.isSameSide(player, guard), "Lenient mode ignored an actively hostile Guard");
            helper.assertFalse(AllegianceUtil.isSameSide(player, recruit), "Lenient mode ignored an actively hostile Recruit");
        } finally {
            CommonConfig.STRICT_PLAYER_ALLIANCE.set(strict);
            player.stopRiding();
            guard.discard();
            recruit.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void neutralPassengerBlocksPlayerMount(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = player(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        Guard guard = spawn(level, GUARD, helper.absolutePos(new BlockPos(3, 2, 2)), Guard.class);
        Entity vehicle = spawn(level, VEHICLE, helper.absolutePos(new BlockPos(4, 2, 2)), Entity.class);
        boolean strict = CommonConfig.STRICT_PLAYER_ALLIANCE.get();

        try {
            CommonConfig.STRICT_PLAYER_ALLIANCE.set(true);
            helper.assertTrue(guard.startRiding(vehicle), "Guard fixture could not enter the vehicle");
            helper.assertFalse(player.startRiding(vehicle), "Player mounted a vehicle containing a neutral passenger");
        } finally {
            CommonConfig.STRICT_PLAYER_ALLIANCE.set(strict);
            player.stopRiding();
            guard.discard();
            vehicle.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void neutralDriverBlocksPlayerInteraction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = player(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        Guard guard = spawn(level, GUARD, helper.absolutePos(new BlockPos(3, 2, 2)), Guard.class);
        Entity vehicle = spawn(level, VEHICLE, helper.absolutePos(new BlockPos(4, 2, 2)), Entity.class);
        boolean strict = CommonConfig.STRICT_PLAYER_ALLIANCE.get();

        try {
            CommonConfig.STRICT_PLAYER_ALLIANCE.set(true);
            helper.assertTrue(guard.startRiding(vehicle), "Guard fixture could not enter the vehicle");
            InteractionResult result = player.interactOn(vehicle, InteractionHand.MAIN_HAND);
            helper.assertTrue(result.consumesAction(), "Vehicle interaction was not canceled before SBW handled it");
            helper.assertFalse(player.getVehicle() == vehicle, "Player mounted a vehicle containing a neutral driver");
            helper.assertTrue(guard.getVehicle() == vehicle, "SBW ejected the neutral driver before mount rejection");
        } finally {
            CommonConfig.STRICT_PLAYER_ALLIANCE.set(strict);
            player.stopRiding();
            guard.discard();
            vehicle.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void groundMobIgnoresAirborneHelicopter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity helicopter = spawn(level, HELICOPTER, helper.absolutePos(new BlockPos(8, 18, 8)), Entity.class);
        Mob candidate = spawn(level, VINDICATOR, helper.absolutePos(new BlockPos(2, 2, 2)), Mob.class);
        Mob observer = spawn(level, COW, helper.absolutePos(new BlockPos(3, 2, 2)), Mob.class);
        VehicleController controller = controller(helicopter);
        int driverSeat = controller.seats().stream()
                .filter(SeatHandle::canDrive)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Helicopter fixture has no driver seat"))
                .id();
        helicopter.setNoGravity(true);
        helicopter.setOnGround(false);

        helper.runAfterDelay(10, () -> {
            try {
                helper.assertFalse(VehicleClaimManager.isClaimedByOther(observer, helicopter, driverSeat),
                        "Ground mob claimed a seat in an airborne helicopter");
                helper.assertFalse(candidate.getVehicle() == helicopter,
                        "Ground mob mounted an airborne helicopter");
                helper.succeed();
            } finally {
                candidate.discard();
                observer.discard();
                helicopter.discard();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void creativePlayerIsNotAValidCombatTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = player(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        Mob attacker = spawn(level, VINDICATOR, helper.absolutePos(new BlockPos(4, 2, 2)), Mob.class);

        try {
            player.setGameMode(GameType.SURVIVAL);
            helper.assertTrue(VehicleAiTuning.isValidCombatTarget(attacker, player),
                    "Nearby survival player was not a valid combat target");

            player.setGameMode(GameType.CREATIVE);
            helper.assertFalse(VehicleAiTuning.isValidCombatTarget(attacker, player),
                    "Creative player remained a valid combat target");
        } finally {
            attacker.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void friendlyRecruitKeepsOriginalSeatPriority(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = player(level, helper.absolutePos(new BlockPos(2, 2, 2)));
        Entity firstVehicle = spawn(level, VEHICLE, helper.absolutePos(new BlockPos(5, 2, 2)), Entity.class);
        VehicleController firstController = controller(firstVehicle);
        AbstractRecruitEntity firstRecruit = ownedRecruit(level, player, helper.absolutePos(new BlockPos(7, 2, 2)));

        helper.assertTrue(player.startRiding(firstVehicle), "Player fixture could not occupy the driver seat");
        helper.runAfterDelay(90, () -> {
            Optional<SeatHandle> firstSeat = firstController.seatOf(firstRecruit);
            helper.assertTrue(firstSeat.isPresent() && firstSeat.get().hasWeapon(),
                    "AI did not prioritize a fire seat when the player occupied the driver seat");

            player.stopRiding();
            firstRecruit.discard();
            firstVehicle.discard();

            Entity secondVehicle = spawn(level, VEHICLE, helper.absolutePos(new BlockPos(5, 2, 2)), Entity.class);
            VehicleController secondController = controller(secondVehicle);
            SeatHandle fireSeat = secondController.seats().stream()
                    .filter(SeatHandle::hasWeapon)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Vehicle fixture has no fire seat"));
            helper.assertTrue(player.startRiding(secondVehicle), "Player fixture could not enter the second vehicle");
            helper.assertTrue(SbwGameTestVehicle.movePassengerToSeat(secondVehicle, player, fireSeat.id()),
                    "Player fixture could not move to the fire seat");
            AbstractRecruitEntity secondRecruit = ownedRecruit(level, player, helper.absolutePos(new BlockPos(7, 2, 2)));

            helper.runAfterDelay(90, () -> {
                try {
                    Optional<SeatHandle> secondSeat = secondController.seatOf(secondRecruit);
                    helper.assertTrue(secondSeat.isPresent() && secondSeat.get().canDrive(),
                            "AI did not keep driver-seat priority when the player occupied a fire seat");
                    helper.succeed();
                } finally {
                    player.stopRiding();
                    secondRecruit.discard();
                    secondVehicle.discard();
                }
            });
        });
    }

    private static AbstractRecruitEntity ownedRecruit(ServerLevel level, FakePlayer player, BlockPos position) {
        AbstractRecruitEntity recruit = spawn(level, RECRUIT, position, AbstractRecruitEntity.class);
        recruit.setIsOwned(true);
        recruit.setOwnerUUID(Optional.of(player.getUUID()));
        return recruit;
    }

    private static FakePlayer player(ServerLevel level, BlockPos position) {
        FakePlayer player = FakePlayerFactory.get(level,
                new GameProfile(UUID.randomUUID(), "vr_alliance"));
        player.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        return player;
    }

    private static VehicleController controller(Entity vehicle) {
        return VehicleControllers.create(vehicle)
                .orElseThrow(() -> new AssertionError("Vehicle fixture is not controllable"));
    }

    private static <T extends Entity> T spawn(ServerLevel level, ResourceLocation id, BlockPos position, Class<T> type) {
        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (entityType == null) {
            throw new AssertionError("Missing GameTest entity type " + id);
        }
        Entity entity = entityType.create(level);
        if (entity == null || !type.isInstance(entity)) {
            throw new AssertionError("Could not create GameTest entity " + id);
        }
        entity.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        level.addFreshEntity(entity);
        return type.cast(entity);
    }
}
