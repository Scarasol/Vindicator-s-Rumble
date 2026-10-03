package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwGameTestVehicle;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.talhanation.recruits.CommandEvents;
import com.talhanation.recruits.RecruitEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder("vindicators_rumble_recruits")
@PrefixGameTestTemplate(false)
public final class RecruitsMovementGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();
    private static final Logger LOGGER = LogUtils.getLogger();

    private RecruitsMovementGameTests() {
    }

    @GameTest(batch = "recruits_movement", template = "empty", timeoutTicks = 600)
    public static void ah6MovesAboveCommandPointAndHovers(GameTestHelper helper) {
        verifyPointMove(helper, "ah_6", false);
    }

    @GameTest(batch = "recruits_movement", template = "empty", timeoutTicks = 600)
    public static void mi28MovesAboveCommandPointAndHovers(GameTestHelper helper) {
        verifyPointMove(helper, "mi_28", false);
    }

    @GameTest(batch = "recruits_movement", template = "empty", timeoutTicks = 600)
    public static void helicopterClimbsBeforeBlockedCommandCorridor(GameTestHelper helper) {
        verifyPointMove(helper, "ah_6", true);
    }

    @GameTest(batch = "recruits_movement", template = "empty", timeoutTicks = 400)
    public static void groundVehicleUsesNativePointCommand(GameTestHelper helper) {
        Fixture fixture = fixture(helper, "sodayo_pick_up_hmg", false);
        fixture.pointCommand(28);
        helper.onEachTick(() -> {
            fixture.observe(helper);
            if (!fixture.driver.getShouldMovePos() && fixture.driver.getFollowState() == 2) {
                helper.assertTrue(horizontalDistance(fixture.vehicle.position(), fixture.destination(28)) <= 6.0D,
                        "Ground command completed away from its destination");
                fixture.close();
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "recruits_movement", template = "empty", timeoutTicks = 600)
    public static void followingReadsMovingOwnerAndCancelStopsFlight(GameTestHelper helper) {
        Fixture fixture = fixture(helper, "ah_6", true);
        fixture.player.setPos(fixture.destination(26));
        fixture.level.addNewPlayer(fixture.player);
        CommandEvents.onMovementCommand(fixture.player, List.of(fixture.driver), 1, 0);
        helper.assertTrue(fixture.driver.getOwner() == fixture.player, "Owner fixture was not registered");
        boolean[] moved = {false};
        Vec3[] canceledAt = {null};
        long[] cancelTick = {-1};
        helper.onEachTick(() -> {
            fixture.observe(helper);
            double distance = horizontalDistance(fixture.vehicle.position(), fixture.player.position());
            boolean settled = fixture.vehicle.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).length() < 0.08D;
            if (!moved[0] && distance <= 6.0D && settled) {
                moved[0] = true;
                fixture.player.setPos(fixture.destination(52));
            } else if (moved[0] && canceledAt[0] == null && distance <= 6.0D && settled) {
                CommandEvents.onMovementCommand(fixture.player, List.of(fixture.driver), 0, 0);
                canceledAt[0] = fixture.vehicle.position();
                cancelTick[0] = helper.getTick();
            } else if (canceledAt[0] != null && helper.getTick() - cancelTick[0] >= 60) {
                helper.assertTrue(horizontalDistance(fixture.vehicle.position(), canceledAt[0]) < 8.0D,
                        "Canceled follow command continued flying");
                helper.assertTrue(fixture.vehicle.getY() > fixture.origin.getY() + 10,
                        "Canceling a command caused a landing or fall");
                fixture.close();
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "recruits_movement", template = "empty", timeoutTicks = 90)
    public static void gunnerCommandCannotDriveAndInfantryGoalsResumeOnFoot(GameTestHelper helper) {
        Fixture fixture = fixture(helper, "ah_6", true);
        AbstractRecruitEntity gunner = fixture.recruit();
        helper.assertTrue(gunner.startRiding(fixture.vehicle, true), "Gunner could not mount");
        VehicleController controller = VehicleControllers.create(fixture.vehicle).orElseThrow();
        helper.assertFalse(controller.canDrive(gunner), "Gunner fixture is in the driver seat");
        fixture.pointCommand(gunner, 52);
        Vec3 start = fixture.vehicle.position();
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(horizontalDistance(fixture.vehicle.position(), start) < 6.0D,
                    "Non-driver movement command moved the helicopter");
            helper.assertTrue(gunner.getShouldMovePos(), "Gunner consumed an unexecuted command");
            gunner.stopRiding();
            gunner.setPos(fixture.destination(48));
            helper.runAfterDelay(15, () -> {
                helper.assertTrue(gunner.goalSelector.getRunningGoals()
                                .anyMatch(goal -> goal.getGoal() instanceof com.talhanation.recruits.entities.ai.RecruitMoveToPosGoal)
                                || gunner.reachedMovePos,
                        "Vehicle integration disabled infantry movement after dismount");
                fixture.close();
                helper.succeed();
            });
        });
    }

    @GameTest(batch = "recruits_movement", template = "empty", timeoutTicks = 90)
    public static void holdUsesNativePositionAndSurvivesSave(GameTestHelper helper) {
        Fixture fixture = fixture(helper, "ah_6", true);
        fixture.level.addNewPlayer(fixture.player);
        CommandEvents.onMovementCommand(fixture.player, List.of(fixture.driver), 2, 0);
        Vec3 hold = fixture.driver.getHoldPos();
        helper.assertTrue(hold != null && fixture.driver.getShouldHoldPos(), "Native hold command missing");
        net.minecraft.nbt.CompoundTag saved = fixture.driver.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        AbstractRecruitEntity restored = (AbstractRecruitEntity) create(fixture.level, "recruits", "recruit");
        restored.load(saved);
        helper.assertTrue(restored.getShouldHoldPos() && restored.getHoldPos() != null,
                "Native hold command did not persist");
        restored.discard();
        Vec3 start = fixture.vehicle.position();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(horizontalDistance(fixture.vehicle.position(), start) < 6.0D,
                    "Hold command produced horizontal travel");
            helper.assertTrue(fixture.driver.getTarget() == null, "Movement manufactured a combat target");
            fixture.close();
            helper.succeed();
        });
    }

    @GameTest(batch = "recruits_movement", template = "empty", timeoutTicks = 90)
    public static void combatOwnsControlsThenNavigationResumes(GameTestHelper helper) {
        Fixture fixture = fixture(helper, "ah_6", true);
        fixture.pointCommand(28);
        CommandEvents.onStrategicFireCommand(fixture.player, fixture.player.getUUID(),
                fixture.driver, fixture.group.getUUID(), true);
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(fixture.driver.getTarget() != null, "Combat fixture lost its strategic target");
            List<net.minecraft.world.entity.ai.goal.WrappedGoal> driveGoals = fixture.driver.goalSelector
                    .getRunningGoals().filter(goal -> goal.getGoal()
                            instanceof com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.DriveVehicleGoal).toList();
            helper.assertTrue(driveGoals.size() == 1 && driveGoals.get(0).getFlags()
                            .contains(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE),
                    "Combat and navigation were both executing controls");
            CommandEvents.onStrategicFireCommand(fixture.player, fixture.player.getUUID(),
                    fixture.driver, fixture.group.getUUID(), false);
            helper.runAfterDelay(15, () -> {
                helper.assertTrue(fixture.driver.goalSelector.getRunningGoals()
                                .filter(goal -> goal.getGoal()
                                        instanceof com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.DriveVehicleGoal)
                                .allMatch(goal -> goal.getFlags().isEmpty()),
                        "Idle combat stabilization competed with resumed navigation");
                helper.assertTrue(fixture.driver.getShouldMovePos(), "Combat destroyed the native movement command");
                fixture.close();
                helper.succeed();
            });
        });
    }

    private static void verifyPointMove(GameTestHelper helper, String type, boolean wall) {
        Fixture fixture = fixture(helper, type, true);
        if (wall) {
            for (int x = -10; x <= 10; x++) {
                for (int y = 0; y <= 28; y++) {
                    fixture.level.setBlock(fixture.origin.offset(x, y, 26), Blocks.BEDROCK.defaultBlockState(), 2);
                }
            }
        }
        fixture.pointCommand(52);
        long[] arrival = {-1};
        helper.onEachTick(() -> {
            fixture.observe(helper);
            if (!fixture.driver.getShouldMovePos() && fixture.driver.getFollowState() == 2 && arrival[0] < 0) {
                arrival[0] = helper.getTick();
            }
            if (arrival[0] >= 0 && helper.getTick() - arrival[0] >= 40) {
                helper.assertTrue(horizontalDistance(fixture.vehicle.position(), fixture.destination(52)) <= 6.0D,
                        "Helicopter did not hold above the command point");
                helper.assertTrue(fixture.vehicle.getY() > fixture.origin.getY() + 10,
                        "Helicopter tried to reach the ground point vertically");
                helper.assertTrue(fixture.vehicle.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).length() < 0.25D,
                        "Helicopter failed to settle on arrival");
                helper.assertTrue(fixture.driver.getTarget() == null, "Point command manufactured a target");
                fixture.close();
                helper.succeed();
            }
        });
    }

    private static Fixture fixture(GameTestHelper helper, String type, boolean airborne) {
        CommonConfig.VEHICLE_AI_DEBUG.set(true);
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(512 + ARENA.getAndIncrement() * 256, 5, 512);
        List<BlockPos> chunks = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 5; z++) {
                BlockPos chunk = new BlockPos((origin.getX() >> 4) + x, 0, (origin.getZ() >> 4) + z);
                level.setChunkForced(chunk.getX(), chunk.getZ(), true);
                chunks.add(chunk);
            }
        }
        for (int x = -12; x <= 12; x++) {
            for (int z = -8; z <= 80; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
            }
        }
        Entity vehicle = create(level, "superbwarfare", type);
        vehicle.setPos(Vec3.atBottomCenterOf(origin).add(0.0D, airborne ? 18.0D : 0.0D, 0.0D));
        vehicle.setInvulnerable(true);
        level.addFreshEntity(vehicle);
        if (airborne) SbwGameTestVehicle.prepareUnarmedAirborne(vehicle);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "vr_move"));
        player.setPos(Vec3.atBottomCenterOf(origin.offset(0, 6, 52)));
        RecruitsGroup group = new RecruitsGroup("movement_test", player, 0);
        RecruitEvents.recruitsGroupsManager.addOrUpdateGroup(level, player, group);
        Fixture fixture = new Fixture(level, origin, vehicle, player, group, chunks);
        fixture.driver = fixture.recruit();
        helper.assertTrue(fixture.driver.startRiding(vehicle, true), "Movement driver could not mount");
        helper.assertTrue(VehicleControllers.create(vehicle).orElseThrow().canDrive(fixture.driver),
                "Movement fixture did not mount the driver seat");
        return fixture;
    }

    private static Entity create(ServerLevel level, String namespace, String id) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(namespace, id));
        if (type == null) throw new AssertionError("Missing movement fixture " + namespace + ":" + id);
        Entity entity = type.create(level);
        if (entity == null) throw new AssertionError("Could not create movement fixture");
        return entity;
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        return Math.hypot(first.x - second.x, first.z - second.z);
    }

    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;
        final BlockPos origin;
        final Entity vehicle;
        final FakePlayer player;
        final RecruitsGroup group;
        final List<BlockPos> chunks;
        final List<AbstractRecruitEntity> recruits = new ArrayList<>();
        AbstractRecruitEntity driver;

        Fixture(ServerLevel level, BlockPos origin, Entity vehicle, FakePlayer player,
                RecruitsGroup group, List<BlockPos> chunks) {
            this.level = level;
            this.origin = origin;
            this.vehicle = vehicle;
            this.player = player;
            this.group = group;
            this.chunks = chunks;
        }

        AbstractRecruitEntity recruit() {
            AbstractRecruitEntity recruit = (AbstractRecruitEntity) create(level, "recruits", "recruit");
            recruit.setIsOwned(true);
            recruit.setOwnerUUID(Optional.of(player.getUUID()));
            group.addMember(recruit.getUUID());
            recruit.setGroupUUID(group.getUUID());
            recruit.setListen(true);
            recruit.setHunger(100.0F);
            recruit.setMoral(100.0F);
            recruit.setAggroState(0);
            recruit.setPersistenceRequired();
            recruit.setInvulnerable(true);
            recruit.setPos(vehicle.position());
            level.addFreshEntity(recruit);
            recruits.add(recruit);
            return recruit;
        }

        Vec3 destination(int z) {
            return Vec3.atBottomCenterOf(origin.offset(0, 0, z));
        }

        void pointCommand(int z) {
            pointCommand(driver, z);
        }

        void pointCommand(AbstractRecruitEntity recruit, int z) {
            Vec3 point = destination(z).add(0.0D, 6.0D, 0.0D);
            player.moveTo(point.x, point.y, point.z, 0.0F, 90.0F);
            player.setYHeadRot(0.0F);
            player.yHeadRotO = 0.0F;
            player.xRotO = 90.0F;
            if (player.pick(100.0D, 1.0F, true).getType() != HitResult.Type.BLOCK) {
                throw new AssertionError("Movement fixture command has no block hit");
            }
            CommandEvents.onMovementCommand(player, List.of(recruit), 6, 0);
            if (!recruit.getShouldMovePos()) throw new AssertionError("Native point command was not accepted");
        }

        void observe(GameTestHelper helper) {
            helper.assertTrue(vehicle.isAlive() && driver.isPassenger(), "Movement lost the driver or vehicle");
            helper.assertFalse(vehicle.horizontalCollision, "Command flight collided with a wall: pos="
                    + vehicle.position().subtract(Vec3.atLowerCornerOf(origin)) + " velocity=" + vehicle.getDeltaMovement());
            if (helper.getTick() % 20 == 0) {
                LOGGER.info("Recruit movement tick={} vehicle={} pos={} velocity={} move={} reached={} follow={} target={} goals={}",
                        helper.getTick(), vehicle.getType(), vehicle.position().subtract(Vec3.atLowerCornerOf(origin)),
                        vehicle.getDeltaMovement(), driver.getMovePos(), driver.reachedMovePos, driver.getFollowState(),
                        driver.getTarget(), driver.goalSelector.getRunningGoals()
                                .map(goal -> goal.getGoal().getClass().getSimpleName()).toList());
            }
        }

        @Override
        public void close() {
            recruits.forEach(Entity::discard);
            vehicle.discard();
            player.discard();
            RecruitEvents.recruitsGroupsManager.removeGroup(group.getUUID());
            chunks.forEach(chunk -> level.setChunkForced(chunk.getX(), chunk.getZ(), false));
        }
    }
}
