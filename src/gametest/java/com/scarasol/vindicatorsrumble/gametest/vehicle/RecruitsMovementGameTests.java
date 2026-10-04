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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
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
    public static void ah6TurnsFromHoverAndCruisesOnCommand(GameTestHelper helper) {
        verifyFlightCruise(helper, "ah_6", true, 120.0F);
    }

    @GameTest(batch = "recruits_movement", template = "empty", timeoutTicks = 600)
    public static void mi28TurnsFromHoverAndCruisesOnCommand(GameTestHelper helper) {
        verifyFlightCruise(helper, "mi_28", true, 120.0F);
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

    @GameTest(batch = "recruits_movement", template = "empty", timeoutTicks = 400)
    public static void wheelVehicleCruisesThenStopsAtCommandPoint(GameTestHelper helper) {
        verifyGroundCruise(helper, "sodayo_pick_up_hmg");
    }

    @GameTest(batch = "recruits_movement", template = "empty", timeoutTicks = 400)
    public static void trackedVehicleCruisesThenStopsAtCommandPoint(GameTestHelper helper) {
        verifyGroundCruise(helper, "m_1a_2");
    }

    @GameTest(batch = "recruits_movement", template = "empty", timeoutTicks = 450)
    public static void pickupReachesShortSidewaysCommandWithoutCircling(GameTestHelper helper) {
        verifyGroundTurn(helper, "sodayo_pick_up_hmg", 90.0F, false);
    }

    @GameTest(batch = "recruits_movement", template = "empty", timeoutTicks = 450)
    public static void lavReachesShortSidewaysCommandWithoutCircling(GameTestHelper helper) {
        verifyGroundTurn(helper, "lav_150", -90.0F, false);
    }

    @GameTest(batch = "recruits_movement", template = "empty", timeoutTicks = 450)
    public static void pickupReachesCommandBehindItWithoutCircling(GameTestHelper helper) {
        verifyGroundTurn(helper, "sodayo_pick_up_hmg", 180.0F, false);
    }

    @GameTest(batch = "recruits_movement", template = "empty", timeoutTicks = 450)
    public static void trackedVehicleReachesShortSidewaysCommand(GameTestHelper helper) {
        verifyGroundTurn(helper, "m_1a_2", 90.0F, false);
    }

    @GameTest(batch = "recruits_movement", template = "empty", timeoutTicks = 450)
    public static void cruisingPickupReachesRedirectedSidewaysCommand(GameTestHelper helper) {
        verifyGroundTurn(helper, "sodayo_pick_up_hmg", 0.0F, true);
    }

    @GameTest(batch = "recruits_movement", template = "empty", timeoutTicks = 450)
    public static void pickupTurnsWithoutReversingIntoNearbyWall(GameTestHelper helper) {
        verifyGroundTurn(helper, "sodayo_pick_up_hmg", 90.0F, false, true);
    }

    @GameTest(batch = "recruits_movement", template = "empty", timeoutTicks = 110)
    public static void turningVehicleKeepsItsPathCache(GameTestHelper helper) {
        Fixture fixture = fixture(helper, "m_1a_2", false);
        fixture.vehicle.setYRot(75.0F);
        fixture.pointCommand(64);
        AtomicInteger plans = new AtomicInteger();
        var logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(
                "com.scarasol.vindicatorsrumble.VindicatorsRumbleMod");
        String driverPrefix = "[entity.recruits.recruit#" + fixture.driver.getId() + "]";
        AbstractAppender observer = new AbstractAppender("recruit_path_" + fixture.driver.getId(), null, null, false) {
            @Override
            public void append(LogEvent event) {
                String message = event.getMessage().getFormattedMessage();
                if (message.contains(driverPrefix) && message.contains("Planned vehicle path detail")) {
                    plans.incrementAndGet();
                }
            }
        };
        observer.start();
        logger.addAppender(observer);
        helper.runAfterDelay(100, () -> {
            logger.removeAppender(observer);
            observer.stop();
            double traveled = horizontalDistance(fixture.vehicle.position(), Vec3.atBottomCenterOf(fixture.origin));
            LOGGER.info("Recruit turning path cache ticks=100 plans={} traveled={}", plans.get(), traveled);
            fixture.close();
            helper.assertTrue(traveled >= 12.0D, "Turning command failed to make progress");
            helper.assertTrue(plans.get() <= 12,
                    "Turning on open ground rebuilt the path repeatedly: plans=" + plans.get());
            helper.succeed();
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

    @GameTest(batch = "recruits_movement", template = "empty", timeoutTicks = 180)
    public static void gunnerCommandCannotDriveAndInfantryGoalsResumeOnFoot(GameTestHelper helper) {
        Fixture fixture = fixture(helper, "ah_6", true);
        fixture.level.addNewPlayer(fixture.player);
        AbstractRecruitEntity gunner = fixture.recruit();
        helper.assertTrue(gunner.startRiding(fixture.vehicle, true), "Gunner could not mount");
        VehicleController controller = VehicleControllers.create(fixture.vehicle).orElseThrow();
        helper.assertFalse(controller.canDrive(gunner), "Gunner fixture is in the driver seat");
        long[] commandTick = {-1};
        boolean[] infantryResumed = {false};
        helper.onEachTick(() -> {
            helper.assertTrue(commandTick[0] >= 0 || helper.getTick() < 120,
                    "Gunner fixture readiness timed out before observation started");
            long elapsed = helper.getTick() - commandTick[0];
            if (commandTick[0] >= 0 && elapsed >= 25 && elapsed <= 40 && !gunner.isPassenger()) {
                var goals = gunner.goalSelector.getRunningGoals().toList();
                infantryResumed[0] |= goals.stream().anyMatch(goal -> goal.getGoal()
                        instanceof com.talhanation.recruits.entities.ai.RecruitMoveToPosGoal)
                        || gunner.reachedMovePos
                        || !gunner.getShouldMovePos() && gunner.getFollowState() == 2;
                LOGGER.info("Recruit infantry resume tick={} entityTick={} entityTicking={} noAi={} pos={} move={} reached={} follow={} mount={} food={} goals={}",
                        helper.getTick(), gunner.tickCount, fixture.level.isPositionEntityTicking(gunner.blockPosition()),
                        gunner.isNoAi(), gunner.position(), gunner.getShouldMovePos(), gunner.reachedMovePos,
                        gunner.getFollowState(), gunner.getShouldMount(), gunner.needsToGetFood(),
                        goals.stream().map(goal -> goal.getGoal().getClass().getSimpleName()).toList());
            }
        });
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(gunner.tickCount > 0
                            && fixture.level.isPositionEntityTicking(fixture.vehicle.blockPosition())
                            && fixture.level.isPositionEntityTicking(BlockPos.containing(fixture.destination(48))),
                    "Gunner fixture has not entered entity-ticking chunks");
        }).thenExecute(() -> {
            fixture.pointCommand(gunner, 52);
            commandTick[0] = helper.getTick();
            Vec3 start = fixture.vehicle.position();
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(horizontalDistance(fixture.vehicle.position(), start) < 6.0D,
                        "Non-driver movement command moved the helicopter");
                helper.assertTrue(gunner.getShouldMovePos(), "Gunner consumed an unexecuted command");
                gunner.stopRiding();
                gunner.setPos(fixture.destination(48));
                helper.runAfterDelay(15, () -> {
                    helper.assertTrue(infantryResumed[0],
                            "Vehicle integration disabled infantry movement after dismount");
                    fixture.close();
                    helper.succeed();
                });
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

    private static void verifyGroundCruise(GameTestHelper helper, String type) {
        Fixture fixture = fixture(helper, type, false);
        fixture.pointCommand(64);
        double[] cruiseSpeedSum = {0.0D};
        double[] previousSpeed = {0.0D};
        int[] cruiseSamples = {0};
        int[] abruptSlowdowns = {0};
        helper.onEachTick(() -> {
            fixture.observe(helper);
            double traveled = fixture.vehicle.getZ() - fixture.origin.getZ() - 0.5D;
            double speed = fixture.vehicle.getDeltaMovement().horizontalDistance();
            if (traveled >= 12.0D && traveled <= 40.0D) {
                cruiseSpeedSum[0] += speed;
                cruiseSamples[0]++;
                if (previousSpeed[0] - speed > 0.06D) abruptSlowdowns[0]++;
            }
            previousSpeed[0] = speed;
            if (!fixture.driver.getShouldMovePos() && fixture.driver.getFollowState() == 2) {
                double averageSpeed = cruiseSpeedSum[0] / Math.max(1, cruiseSamples[0]);
                LOGGER.info("Recruit ground cruise vehicle={} arrivalTick={} averageSpeed={} samples={} abruptSlowdowns={} distance={} speed={}",
                        type, helper.getTick(), averageSpeed, cruiseSamples[0], abruptSlowdowns[0],
                        horizontalDistance(fixture.vehicle.position(), fixture.destination(64)), speed);
                helper.assertTrue(cruiseSamples[0] >= 5 && averageSpeed >= 0.3D,
                        "Open-ground command crawled instead of cruising: averageSpeed=" + averageSpeed);
                helper.assertTrue(abruptSlowdowns[0] == 0,
                        "Open-ground command repeatedly braked during cruise: slowdowns=" + abruptSlowdowns[0]);
                helper.assertTrue(horizontalDistance(fixture.vehicle.position(), fixture.destination(64)) <= 4.0D,
                        "Ground cruise overshot the command point");
                helper.assertTrue(speed < 0.08D, "Ground command completed without stopping");
                fixture.close();
                helper.succeed();
            }
        });
    }

    private static void verifyGroundTurn(GameTestHelper helper, String type, float yaw, boolean redirect) {
        verifyGroundTurn(helper, type, yaw, redirect, false);
    }

    private static void verifyGroundTurn(GameTestHelper helper, String type, float yaw, boolean redirect, boolean rearWall) {
        Fixture fixture = fixture(helper, type, false, true);
        fixture.vehicle.setYRot(yaw);
        if (rearWall) {
            for (int z = -10; z <= 30; z++) {
                for (int y = 0; y <= 4; y++) {
                    fixture.level.setBlock(fixture.origin.offset(5, y, z), Blocks.BEDROCK.defaultBlockState(), 2);
                }
            }
        }
        fixture.pointCommand(redirect ? 64 : 16);
        Vec3[] destination = {fixture.destination(redirect ? 64 : 16)};
        Vec3[] previous = {fixture.vehicle.position()};
        double[] traveled = {0.0D};
        boolean[] redirected = {!redirect};
        helper.onEachTick(() -> {
            fixture.observe(helper);
            Vec3 position = fixture.vehicle.position();
            traveled[0] += horizontalDistance(position, previous[0]);
            previous[0] = position;
            if (!redirected[0] && position.z >= fixture.origin.getZ() + 24.5D) {
                helper.assertTrue(fixture.vehicle.getDeltaMovement().horizontalDistance() >= 0.3D,
                        "Redirect fixture did not reach cruising speed");
                fixture.pointCommand(-12, 24);
                destination[0] = Vec3.atBottomCenterOf(fixture.origin.offset(-12, 0, 24));
                traveled[0] = 0.0D;
                redirected[0] = true;
            }
            if (!redirected[0]) return;
            double distance = horizontalDistance(position, destination[0]);
            var controls = SbwGameTestVehicle.flightControl(fixture.vehicle);
            if (!type.equals("m_1a_2") && controls.up()) {
                helper.assertFalse(controls.left() || controls.right(), "Wheel braking engaged a handbrake drift");
            }
            LOGGER.info("Recruit ground turn vehicle={} initialYaw={} redirect={} wall={} tick={} pos={} yaw={} distance={} traveled={} speed={} radius={} controls={}",
                    type, yaw, redirect, rearWall, helper.getTick(), position.subtract(Vec3.atBottomCenterOf(fixture.origin)),
                    fixture.vehicle.getYRot(), distance, traveled[0],
                    fixture.vehicle.getDeltaMovement().horizontalDistance(),
                    VehicleControllers.create(fixture.vehicle).orElseThrow().estimatedGroundTurningRadius(),
                    controls);
            helper.assertTrue(traveled[0] <= 80.0D,
                    "Short open-ground command circled instead of arriving: traveled=" + traveled[0]);
            if (!fixture.driver.getShouldMovePos() && fixture.driver.getFollowState() == 2) {
                helper.assertTrue(distance <= 4.0D, "Turning command completed away from its destination");
                helper.assertTrue(fixture.vehicle.getDeltaMovement().horizontalDistance() < 0.08D,
                        "Turning command completed without stopping");
                LOGGER.info("Recruit ground turn result vehicle={} initialYaw={} redirect={} wall={} arrivalTick={} distance={} traveled={}",
                        type, yaw, redirect, rearWall, helper.getTick(), distance, traveled[0]);
                fixture.close();
                helper.succeed();
            }
        });
    }

    private static void verifyFlightCruise(GameTestHelper helper, String type, boolean airborne, float yaw) {
        Fixture fixture = fixture(helper, type, airborne);
        fixture.vehicle.setYRot(yaw);
        VehicleController controller = VehicleControllers.create(fixture.vehicle).orElseThrow();
        if (airborne) {
            helper.assertTrue(controller.initializeAirborneHover(fixture.driver), "Could not initialize hover fixture");
        }
        fixture.pointCommand(64);
        double[] speedSum = {0.0D};
        int[] samples = {0};
        int[] hoverChanges = {0};
        int[] totalHoverChanges = {0};
        boolean[] previousHover = {controller.hoverMode()};
        helper.onEachTick(() -> {
            fixture.observe(helper);
            double traveled = fixture.vehicle.getZ() - fixture.origin.getZ() - 0.5D;
            double speed = fixture.vehicle.getDeltaMovement().horizontalDistance();
            if (previousHover[0] != controller.hoverMode()) totalHoverChanges[0]++;
            if (traveled >= 12.0D && traveled <= 40.0D) {
                speedSum[0] += speed;
                samples[0]++;
                if (previousHover[0] != controller.hoverMode()) hoverChanges[0]++;
            }
            previousHover[0] = controller.hoverMode();
            LOGGER.info("Recruit flight cruise vehicle={} airborne={} initialYaw={} tick={} pos={} velocity={} yaw={} pitch={} roll={} hover={} controls={} goals={}",
                    type, airborne, yaw, helper.getTick(),
                    fixture.vehicle.position().subtract(Vec3.atLowerCornerOf(fixture.origin)),
                    fixture.vehicle.getDeltaMovement(), fixture.vehicle.getYRot(), fixture.vehicle.getXRot(),
                    controller.roll(), controller.hoverMode(), SbwGameTestVehicle.flightControl(fixture.vehicle),
                    fixture.driver.goalSelector.getRunningGoals().map(goal -> goal.getGoal().getClass().getSimpleName()).toList());
            if (!fixture.driver.getShouldMovePos() && fixture.driver.getFollowState() == 2) {
                double averageSpeed = speedSum[0] / Math.max(1, samples[0]);
                LOGGER.info("Recruit flight cruise result vehicle={} airborne={} initialYaw={} arrivalTick={} averageSpeed={} samples={} hoverChanges={} totalHoverChanges={} distance={} speed={}",
                        type, airborne, yaw, helper.getTick(), averageSpeed, samples[0], hoverChanges[0], totalHoverChanges[0],
                        horizontalDistance(fixture.vehicle.position(), fixture.destination(64)), speed);
                helper.assertTrue(samples[0] >= 5 && averageSpeed >= 0.3D,
                        "Open-air command crawled instead of cruising: averageSpeed=" + averageSpeed);
                helper.assertTrue(hoverChanges[0] <= 1,
                        "Open-air cruise repeatedly toggled hover: changes=" + hoverChanges[0]);
                helper.assertTrue(horizontalDistance(fixture.vehicle.position(), fixture.destination(64)) <= 4.0D,
                        "Flight cruise overshot the command point");
                helper.assertTrue(speed < 0.08D, "Flight command completed without stopping");
                helper.assertTrue(fixture.vehicle.getY() > fixture.origin.getY() + 10,
                        "Flight command did not stay airborne");
                fixture.close();
                helper.succeed();
            }
        });
    }

    private static Fixture fixture(GameTestHelper helper, String type, boolean airborne) {
        return fixture(helper, type, airborne, false);
    }

    private static Fixture fixture(GameTestHelper helper, String type, boolean airborne, boolean turning) {
        CommonConfig.VEHICLE_AI_DEBUG.set(true);
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(512 + ARENA.getAndIncrement() * 256, 5, 512);
        List<BlockPos> chunks = new ArrayList<>();
        for (int x = turning ? -3 : -1; x <= (turning ? 3 : 1); x++) {
            for (int z = turning ? -3 : -1; z <= (turning ? 7 : 5); z++) {
                BlockPos chunk = new BlockPos((origin.getX() >> 4) + x, 0, (origin.getZ() >> 4) + z);
                level.setChunkForced(chunk.getX(), chunk.getZ(), true);
                chunks.add(chunk);
            }
        }
        for (int x = turning ? -40 : -12; x <= (turning ? 40 : 12); x++) {
            for (int z = turning ? -40 : -8; z <= (turning ? 100 : 80); z++) {
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
        ResourceLocation key = new ResourceLocation(namespace, id);
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(key)) {
            throw new AssertionError("Missing movement fixture " + key);
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(key);
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

        void pointCommand(int x, int z) {
            pointCommand(driver, Vec3.atBottomCenterOf(origin.offset(x, 0, z)));
        }

        void pointCommand(AbstractRecruitEntity recruit, int z) {
            pointCommand(recruit, destination(z));
        }

        void pointCommand(AbstractRecruitEntity recruit, Vec3 destination) {
            Vec3 point = destination.add(0.0D, 6.0D, 0.0D);
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
