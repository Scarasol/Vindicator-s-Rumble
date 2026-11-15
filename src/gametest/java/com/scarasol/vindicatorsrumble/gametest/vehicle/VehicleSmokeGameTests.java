package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.mojang.authlib.GameProfile;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleThreats;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwGameTestVehicle;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@GameTestHolder("vindicators_rumble_smoke")
@PrefixGameTestTemplate(false)
public final class VehicleSmokeGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();
    private static final List<Fixture> ACTIVE = new ArrayList<>();
    private static final Consumer<EntityJoinLevelEvent> SPAWN_OBSERVER = VehicleSmokeGameTests::onSpawn;
    private static final Consumer<TickEvent.LevelTickEvent> TICK_OBSERVER = VehicleSmokeGameTests::onLevelTick;
    private static boolean previousAmmo;
    private static boolean previousEnabled;
    private static int previousAcquiring;
    private static double previousHeavy;
    private static double previousLow;

    @BeforeBatch(batch = "smoke_finite")
    public static void finiteAmmo(ServerLevel level) {
        configure(true, true);
    }

    @BeforeBatch(batch = "smoke_infinite")
    public static void infiniteAmmo(ServerLevel level) {
        configure(false, true);
    }

    @BeforeBatch(batch = "smoke_disabled")
    public static void disabledAi(ServerLevel level) {
        configure(true, false);
    }

    @BeforeBatch(batch = "smoke_toggle")
    public static void enabledForToggle(ServerLevel level) {
        configure(true, true);
    }

    @AfterBatch(batch = "smoke_finite")
    public static void restoreFinite(ServerLevel level) {
        restore();
    }

    @AfterBatch(batch = "smoke_infinite")
    public static void restoreInfinite(ServerLevel level) {
        restore();
    }

    @AfterBatch(batch = "smoke_disabled")
    public static void restoreDisabled(ServerLevel level) {
        restore();
    }

    @AfterBatch(batch = "smoke_toggle")
    public static void restoreToggle(ServerLevel level) {
        restore();
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void parkedDriverUsesSmokeOnLockWithoutATarget(GameTestHelper helper) {
        ready(helper, fixture -> {
            helper.assertTrue(fixture.driver.getTarget() == null, "Idle fixture acquired a target");
            Vec3 initial = fixture.vehicle.position();
            fixture.report(VehicleThreats.Stage.LOCKED, 30);
            helper.runAfterDelay(8, () -> {
                fixture.assertVolley(helper);
                helper.assertTrue(fixture.driver.getTarget() == null, "Smoke invented a combat target");
                helper.assertTrue(fixture.vehicle.position().subtract(initial).horizontalDistance() < 0.5,
                        "Idle smoke invented a movement request");
                fixture.finish(helper);
            });
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void continuousAcquisitionWaitsBeforeDeploying(GameTestHelper helper) {
        ready(helper, fixture -> {
            fixture.report(VehicleThreats.Stage.ACQUIRING, 45);
            helper.runAfterDelay(15, () -> fixture.assertNoSmoke(helper));
            helper.runAfterDelay(28, () -> {
                fixture.assertVolley(helper);
                fixture.finish(helper);
            });
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void briefAcquisitionDoesNotWasteSmoke(GameTestHelper helper) {
        ready(helper, fixture -> {
            fixture.report(VehicleThreats.Stage.ACQUIRING, 6);
            fixture.expectNoSmoke(helper, 30);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void acquisitionSourceChangeRestartsTheWait(GameTestHelper helper) {
        ready(helper, fixture -> {
            fixture.report(VehicleThreats.Stage.ACQUIRING, 10);
            Mob second = fixture.source(EntityType.COW);
            helper.runAfterDelay(12, () -> {
                VehicleThreats.clearSource(fixture.attacker);
                VehicleThreats.report(second, fixture.vehicle, VehicleThreats.Stage.ACQUIRING, 10);
            });
            fixture.expectNoSmoke(helper, 35);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void expiredAcquisitionRestartsTheWait(GameTestHelper helper) {
        ready(helper, fixture -> {
            fixture.report(VehicleThreats.Stage.ACQUIRING, 10);
            helper.runAfterDelay(14, () -> fixture.report(VehicleThreats.Stage.ACQUIRING, 10));
            fixture.expectNoSmoke(helper, 35);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void clearedLockDoesNotDeploy(GameTestHelper helper) {
        ready(helper, fixture -> {
            fixture.report(VehicleThreats.Stage.LOCKED, 30);
            VehicleThreats.clearSource(fixture.attacker);
            fixture.expectNoSmoke(helper, 20);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void heavyEnemyHitUsesActualHealthLoss(GameTestHelper helper) {
        ready(helper, fixture -> {
            float before = SbwGameTestVehicle.vehicleHealth(fixture.vehicle);
            SbwGameTestVehicle.enemyHit(fixture.vehicle, fixture.attacker, 150);
            helper.assertTrue(before - SbwGameTestVehicle.vehicleHealth(fixture.vehicle) == 150,
                    "Heavy-hit fixture did not lose the intended vehicle health");
            fixture.expectVolley(helper);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void ordinaryEnemyHitDoesNotWasteSmoke(GameTestHelper helper) {
        ready(helper, fixture -> {
            SbwGameTestVehicle.enemyHit(fixture.vehicle, fixture.attacker, 149);
            fixture.expectNoSmoke(helper, 20);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void largeRawHitWithSmallActualLossDoesNotDeploy(GameTestHelper helper) {
        ready(helper, fixture -> {
            SbwGameTestVehicle.softenMobDamage(fixture.vehicle);
            float before = SbwGameTestVehicle.vehicleHealth(fixture.vehicle);
            SbwGameTestVehicle.enemyHit(fixture.vehicle, fixture.attacker, 200);
            float loss = before - SbwGameTestVehicle.vehicleHealth(fixture.vehicle);
            helper.assertTrue(loss > 0 && loss < 150, "Mitigated-hit fixture did not reduce actual damage");
            fixture.expectNoSmoke(helper, 20);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void lowHealthOnlyTriggersOnANewEnemyHit(GameTestHelper helper) {
        ready(helper, fixture -> {
            SbwGameTestVehicle.setVehicleHealth(fixture.vehicle, 401);
            helper.runAfterDelay(6, () -> {
                fixture.assertNoSmoke(helper);
                SbwGameTestVehicle.enemyHit(fixture.vehicle, fixture.attacker, 1);
                helper.assertTrue(SbwGameTestVehicle.vehicleHealth(fixture.vehicle) == 400,
                        "Low-health fixture missed its boundary");
            });
            helper.runAfterDelay(14, () -> {
                fixture.assertVolley(helper);
                fixture.finish(helper);
            });
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void lowHealthWithoutNewDamageDoesNotDeploy(GameTestHelper helper) {
        ready(helper, fixture -> {
            SbwGameTestVehicle.setVehicleHealth(fixture.vehicle, 200);
            fixture.expectNoSmoke(helper, 25);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void alliedDamageDoesNotDeploy(GameTestHelper helper) {
        ready(helper, fixture -> {
            Mob ally = fixture.source(EntityType.VINDICATOR);
            SbwGameTestVehicle.enemyHit(fixture.vehicle, ally, 200);
            fixture.expectNoSmoke(helper, 20);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void environmentalDamageDoesNotDeploy(GameTestHelper helper) {
        ready(helper, fixture -> {
            fixture.vehicle.hurt(fixture.level.damageSources().generic(), 200);
            fixture.expectNoSmoke(helper, 20);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void collisionWithAnEnemyDoesNotDeploy(GameTestHelper helper) {
        ready(helper, fixture -> {
            SbwGameTestVehicle.collisionHit(fixture.vehicle, fixture.attacker, 200);
            fixture.expectNoSmoke(helper, 20);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void simultaneousLockAndDamageProduceOneVolley(GameTestHelper helper) {
        ready(helper, fixture -> {
            Mob gunner = fixture.source(EntityType.VINDICATOR);
            gunner.setNoAi(false);
            helper.assertTrue(gunner.startRiding(fixture.vehicle, true), "Gunner could not mount");
            fixture.report(VehicleThreats.Stage.LOCKED, 45);
            SbwGameTestVehicle.enemyHit(fixture.vehicle, fixture.attacker, 200);
            helper.runAfterDelay(25, () -> {
                fixture.assertVolley(helper);
                helper.assertTrue(SbwGameTestVehicle.decoyStock(fixture.vehicle) == 0,
                        "Finite smoke was replenished without a spare");
                fixture.finish(helper);
            });
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void noStockAndNoSpareCannotDeploy(GameTestHelper helper) {
        ready(helper, fixture -> {
            SbwGameTestVehicle.setDecoyStock(fixture.vehicle, 0);
            fixture.report(VehicleThreats.Stage.LOCKED, 40);
            fixture.expectNoSmoke(helper, 25);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void untaggedDriverCannotDeploy(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, EntityType.ZOMBIE);
        fixture.start(helper, active -> {
            active.report(VehicleThreats.Stage.LOCKED, 40);
            active.expectNoSmoke(helper, 25);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void aiGunnerDoesNotDeployForAPlayerDriver(GameTestHelper helper) {
        ready(helper, fixture -> {
            fixture.driver.stopRiding();
            FakePlayer player = FakePlayerFactory.get(fixture.level,
                    new GameProfile(UUID.randomUUID(), "SmokeDriver"));
            var scoreboard = fixture.level.getScoreboard();
            var team = scoreboard.addPlayerTeam("smoke" + fixture.vehicle.getId());
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
            scoreboard.addPlayerToTeam(fixture.driver.getScoreboardName(), team);
            player.setPos(fixture.vehicle.position());
            helper.assertTrue(player.startRiding(fixture.vehicle, true), "Player could not take the driver seat");
            helper.assertTrue(fixture.driver.startRiding(fixture.vehicle, true), "AI could not take the gunner seat");
            fixture.extras.add(player);
            fixture.report(VehicleThreats.Stage.LOCKED, 40);
            helper.runAfterDelay(25, () -> {
                fixture.assertNoSmoke(helper);
                helper.assertTrue(fixture.driver.isPassenger(), "Gunner fixture unexpectedly dismounted");
                player.stopRiding();
                scoreboard.removePlayerTeam(team);
                fixture.finish(helper);
            });
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void nativeIncomingEntityGuidedMissileTriggersSmoke(GameTestHelper helper) {
        ready(helper, fixture -> {
            Entity missile = SbwGameTestVehicle.trackedMissile(fixture.level, fixture.attacker, fixture.vehicle,
                    fixture.vehicle.position().add(0, 100, 0));
            fixture.extras.add(missile);
            fixture.expectVolley(helper);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void entityGuidedSwarmDroneTriggersSmoke(GameTestHelper helper) {
        verifyGuidance(helper, "swarm_drone", 0, true);
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void coordinateGuidedSwarmDroneDoesNotTriggerSmoke(GameTestHelper helper) {
        verifyGuidance(helper, "swarm_drone", 1, false);
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void entityGuidedAgm65TriggersSmoke(GameTestHelper helper) {
        verifyGuidance(helper, "agm_65", 0, true);
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void coordinateGuidedAgm65DoesNotTriggerSmoke(GameTestHelper helper) {
        verifyGuidance(helper, "agm_65", 1, false);
    }

    private static void verifyGuidance(GameTestHelper helper, String type, int guidance, boolean effective) {
        ready(helper, fixture -> {
            Entity missile = SbwGameTestVehicle.missileWithGuidance(fixture.level, fixture.attacker, fixture.vehicle,
                    fixture.vehicle.position().add(0, 500, 0), new ResourceLocation("superbwarfare", type), guidance);
            fixture.extras.add(missile);
            if (effective) fixture.expectVolley(helper);
            else fixture.expectNoSmoke(helper, 20);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void wireGuidanceDoesNotTriggerSmoke(GameTestHelper helper) {
        ready(helper, fixture -> {
            Entity missile = SbwGameTestVehicle.wireGuidedMissile(fixture.level, fixture.attacker, fixture.vehicle,
                    fixture.vehicle.position().add(0, 100, 0));
            fixture.extras.add(missile);
            fixture.expectNoSmoke(helper, 20);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void unsupportedIncomingDoesNotMaskAValidLock(GameTestHelper helper) {
        ready(helper, fixture -> {
            Entity missile = SbwGameTestVehicle.wireGuidedMissile(fixture.level, fixture.attacker, fixture.vehicle,
                    fixture.vehicle.position().add(0, 100, 0));
            fixture.extras.add(missile);
            fixture.report(VehicleThreats.Stage.LOCKED, 30);
            fixture.expectVolley(helper);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void lostMissileDoesNotLeaveADeploymentRequest(GameTestHelper helper) {
        ready(helper, fixture -> {
            Entity missile = SbwGameTestVehicle.trackedMissile(fixture.level, fixture.attacker, fixture.vehicle,
                    fixture.vehicle.position().add(0, 100, 0));
            SbwGameTestVehicle.loseMissileGuidance(missile);
            fixture.extras.add(missile);
            helper.runAfterDelay(4, () -> helper.assertTrue(
                    !SbwGameTestVehicle.missileTargets(missile, fixture.vehicle),
                    "Lost-guidance fixture still tracked the vehicle after native ticks"));
            fixture.expectNoSmoke(helper, 20);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void alliedIncomingMissileDoesNotDeploy(GameTestHelper helper) {
        ready(helper, fixture -> {
            Mob ally = fixture.source(EntityType.VINDICATOR);
            Entity missile = SbwGameTestVehicle.trackedMissile(fixture.level, ally, fixture.vehicle,
                    fixture.vehicle.position().add(0, 100, 0));
            fixture.extras.add(missile);
            fixture.expectNoSmoke(helper, 20);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void retargetedMissileDoesNotDeployForTheOldVehicle(GameTestHelper helper) {
        ready(helper, fixture -> {
            Entity missile = SbwGameTestVehicle.trackedMissile(fixture.level, fixture.attacker, fixture.vehicle,
                    fixture.vehicle.position().add(0, 100, 0));
            Mob other = fixture.source(EntityType.COW);
            SbwGameTestVehicle.retargetMissile(missile, other);
            helper.assertTrue(SbwGameTestVehicle.missileTargets(missile, other),
                    "Retargeted missile fixture did not change its target");
            fixture.extras.add(missile);
            fixture.expectNoSmoke(helper, 20);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void clearedQueuedLockDoesNotReleaseSmoke(GameTestHelper helper) {
        ready(helper, fixture -> {
            fixture.whenSmokeQueued(() -> VehicleThreats.clearSource(fixture.attacker));
            fixture.report(VehicleThreats.Stage.LOCKED, 30);
            fixture.expectCancelledSmoke(helper);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void changedDriverDoesNotInheritAQueuedHit(GameTestHelper helper) {
        ready(helper, fixture -> {
            fixture.whenSmokeQueued(() -> {
                fixture.driver.stopRiding();
                fixture.driver.setNoAi(true);
                Mob next = fixture.source(EntityType.VINDICATOR);
                next.setNoAi(false);
                helper.assertTrue(next.startRiding(fixture.vehicle, true), "Replacement driver could not mount");
                helper.assertTrue(fixture.controller.canDrive(next), "Replacement fixture did not own the driver seat");
            });
            SbwGameTestVehicle.enemyHit(fixture.vehicle, fixture.attacker, 200);
            fixture.expectCancelledSmoke(helper);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void disabledMobDoesNotReleaseQueuedSmoke(GameTestHelper helper) {
        ready(helper, fixture -> {
            fixture.whenSmokeQueued(() -> fixture.driver.setNoAi(true));
            fixture.report(VehicleThreats.Stage.LOCKED, 30);
            fixture.expectCancelledSmoke(helper);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void wreckDoesNotReleaseQueuedSmoke(GameTestHelper helper) {
        ready(helper, fixture -> {
            fixture.whenSmokeQueued(() -> SbwGameTestVehicle.setVehicleHealth(fixture.vehicle, 0));
            fixture.report(VehicleThreats.Stage.LOCKED, 30);
            fixture.expectCancelledSmoke(helper);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void playerTakeoverCancelsQueuedAiSmoke(GameTestHelper helper) {
        ready(helper, fixture -> {
            fixture.whenSmokeQueued(() -> {
                fixture.driver.stopRiding();
                fixture.driver.setNoAi(true);
                FakePlayer player = FakePlayerFactory.get(fixture.level,
                        new GameProfile(UUID.randomUUID(), "TakeoverDriver"));
                player.setPos(fixture.vehicle.position());
                helper.assertTrue(player.startRiding(fixture.vehicle, true), "Player takeover failed");
                fixture.extras.add(player);
            });
            fixture.report(VehicleThreats.Stage.LOCKED, 30);
            fixture.expectCancelledSmoke(helper);
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 100)
    public static void navigationKeepsMovingWhileDeployingSmoke(GameTestHelper helper) {
        ready(helper, fixture -> {
            Vec3 initial = fixture.vehicle.position();
            helper.assertTrue(fixture.driver.getNavigation().moveTo(
                    initial.x, initial.y, initial.z + 40, 1.0), "Navigation fixture could not submit a real path");
            fixture.report(VehicleThreats.Stage.LOCKED, 30);
            helper.runAfterDelay(8, () -> fixture.assertVolley(helper));
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(fixture.vehicle.position().subtract(initial).horizontalDistance() > 1,
                        "Smoke interrupted navigation movement");
                helper.assertTrue(fixture.driver.getTarget() == null, "Navigation smoke invented a combat target");
                fixture.finish(helper);
            });
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 140)
    public static void combatKeepsItsTargetAndMovementWhileDeployingSmoke(GameTestHelper helper) {
        ready(helper, fixture -> {
            Mob target = fixture.source(EntityType.VILLAGER);
            target.setInvulnerable(false);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
            target.setHealth(target.getMaxHealth());
            target.setPos(fixture.vehicle.position().add(0, 0, 20));
            boolean[] engaged = {false};
            helper.runAtTickTime(80, () -> helper.assertTrue(engaged[0],
                    "Combat fixture did not acquire and approach its target"));
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                    fixture.driver.getTarget() == target
                            && fixture.vehicle.getDeltaMovement().horizontalDistance() > 0.02,
                    "Waiting for natural combat acquisition and movement")).thenExecute(() -> {
                engaged[0] = true;
                Vec3 initial = fixture.vehicle.position();
                fixture.report(VehicleThreats.Stage.LOCKED, 30);
                helper.runAfterDelay(8, () -> fixture.assertVolley(helper));
                helper.runAfterDelay(25, () -> {
                    helper.assertTrue(fixture.driver.getTarget() == target, "Smoke interrupted the combat target");
                    helper.assertTrue(fixture.vehicle.position().subtract(initial).horizontalDistance() > 1,
                            "Smoke interrupted combat movement");
                    fixture.finish(helper);
                });
            });
        });
    }

    @GameTest(batch = "smoke_finite", template = "empty", timeoutTicks = 160)
    public static void finiteSpareReloadsAndConsumesOnlyThroughSbw(GameTestHelper helper) {
        ready(helper, fixture -> {
            SbwGameTestVehicle.prepareSmokeFixture(fixture.vehicle, 1, 1);
            fixture.report(VehicleThreats.Stage.LOCKED, 15);
            helper.runAfterDelay(10, () -> {
                fixture.assertVolley(helper);
                helper.assertTrue(SbwGameTestVehicle.spareSmoke(fixture.vehicle) == 1,
                        "Spare was consumed before native reload finished");
            });
            helper.runAfterDelay(80, () -> {
                helper.assertTrue(SbwGameTestVehicle.decoyStock(fixture.vehicle) == 1,
                        "Finite spare did not reload");
                helper.assertTrue(SbwGameTestVehicle.spareSmoke(fixture.vehicle) == 0,
                        "Native reload did not consume its spare");
                fixture.report(VehicleThreats.Stage.LOCKED, 15);
            });
            helper.runAfterDelay(90, () -> {
                helper.assertTrue(fixture.smoke() == 16, "Second lock did not produce exactly a second volley");
                fixture.finish(helper);
            });
        });
    }

    @GameTest(batch = "smoke_infinite", template = "empty", timeoutTicks = 160)
    public static void infiniteAmmoStillRespectsNativeReload(GameTestHelper helper) {
        ready(helper, fixture -> {
            fixture.report(VehicleThreats.Stage.LOCKED, 130);
            helper.runAfterDelay(10, () -> fixture.assertVolley(helper));
            helper.runAfterDelay(45, () -> fixture.assertVolley(helper));
            helper.runAfterDelay(85, () -> {
                helper.assertTrue(fixture.smoke() == 16, "Infinite smoke did not obey its native reload cycle");
                fixture.finish(helper);
            });
        });
    }

    @GameTest(batch = "smoke_infinite", template = "empty", timeoutTicks = 160)
    public static void oldHitDoesNotFireAfterReload(GameTestHelper helper) {
        ready(helper, fixture -> {
            fixture.report(VehicleThreats.Stage.LOCKED, 12);
            helper.runAfterDelay(8, () -> {
                fixture.assertVolley(helper);
                SbwGameTestVehicle.enemyHit(fixture.vehicle, fixture.attacker, 200);
            });
            helper.runAfterDelay(90, () -> {
                fixture.assertVolley(helper);
                helper.assertTrue(SbwGameTestVehicle.decoyStock(fixture.vehicle) == 1,
                        "Expired hit fired after native reload");
                fixture.finish(helper);
            });
        });
    }

    @GameTest(batch = "smoke_disabled", template = "empty", timeoutTicks = 100)
    public static void disabledVehicleAiDoesNotDeploy(GameTestHelper helper) {
        ready(helper, fixture -> {
            fixture.report(VehicleThreats.Stage.LOCKED, 40);
            SbwGameTestVehicle.enemyHit(fixture.vehicle, fixture.attacker, 200);
            fixture.expectNoSmoke(helper, 25);
        });
    }

    @GameTest(batch = "smoke_toggle", template = "empty", timeoutTicks = 100)
    public static void masterSwitchKeepsQueuedSmokeForAnExistingAiDriver(GameTestHelper helper) {
        ready(helper, fixture -> {
            fixture.whenSmokeQueued(() -> CommonConfig.ENABLE_VEHICLE_AI.set(false));
            fixture.report(VehicleThreats.Stage.LOCKED, 30);
            helper.runAfterDelay(8, () -> {
                helper.assertTrue(fixture.queueObserved && !CommonConfig.ENABLE_VEHICLE_AI.get(),
                        "Fixture did not disable the master switch after queuing smoke");
                fixture.assertVolley(helper);
                fixture.finish(helper);
            });
        });
    }

    private static void configure(boolean finite, boolean enabled) {
        MinecraftForge.EVENT_BUS.addListener(SPAWN_OBSERVER);
        MinecraftForge.EVENT_BUS.addListener(TICK_OBSERVER);
        previousAmmo = CommonConfig.VEHICLE_AI_NEEDS_AMMO.get();
        previousEnabled = CommonConfig.ENABLE_VEHICLE_AI.get();
        previousAcquiring = CommonConfig.SMOKE_ACQUIRING_TICKS.get();
        previousHeavy = CommonConfig.SMOKE_HEAVY_HIT_RATIO.get();
        previousLow = CommonConfig.SMOKE_LOW_HEALTH_RATIO.get();
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(finite);
        CommonConfig.ENABLE_VEHICLE_AI.set(enabled);
        CommonConfig.SMOKE_ACQUIRING_TICKS.set(20);
        CommonConfig.SMOKE_HEAVY_HIT_RATIO.set(0.15);
        CommonConfig.SMOKE_LOW_HEALTH_RATIO.set(0.40);
    }

    private static void restore() {
        MinecraftForge.EVENT_BUS.unregister(SPAWN_OBSERVER);
        MinecraftForge.EVENT_BUS.unregister(TICK_OBSERVER);
        List.copyOf(ACTIVE).forEach(Fixture::cleanup);
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(previousAmmo);
        CommonConfig.ENABLE_VEHICLE_AI.set(previousEnabled);
        CommonConfig.SMOKE_ACQUIRING_TICKS.set(previousAcquiring);
        CommonConfig.SMOKE_HEAVY_HIT_RATIO.set(previousHeavy);
        CommonConfig.SMOKE_LOW_HEALTH_RATIO.set(previousLow);
    }

    private static void onSpawn(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.isCanceled() || !SbwGameTestVehicle.isSmoke(entity)) return;
        for (Fixture fixture : ACTIVE) {
            if (fixture.level == event.getLevel() && new AABB(fixture.origin).inflate(40).contains(entity.position())) {
                fixture.spawnedSmoke.add(entity);
            }
        }
    }

    private static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) return;
        for (Fixture fixture : List.copyOf(ACTIVE)) {
            if (fixture.level == event.level && fixture.queuedAction != null
                    && SbwGameTestVehicle.hasDecoyInput(fixture.vehicle)) {
                Runnable action = fixture.queuedAction;
                fixture.queuedAction = null;
                fixture.queueObserved = true;
                try {
                    action.run();
                } catch (GameTestAssertException failure) {
                    fixture.helper.fail(failure.getMessage());
                }
            }
        }
    }

    private static void ready(GameTestHelper helper, Consumer<Fixture> scenario) {
        new Fixture(helper, EntityType.VINDICATOR).start(helper, scenario);
    }

    private static final class Fixture {
        final ServerLevel level;
        final GameTestHelper helper;
        final BlockPos origin;
        final Entity vehicle;
        final Mob driver;
        final Mob attacker;
        final VehicleController controller;
        final List<Entity> extras = new ArrayList<>();
        final List<Entity> spawnedSmoke = new ArrayList<>();
        final List<BlockPos> chunks = new ArrayList<>();
        boolean started;
        boolean queueObserved;
        Runnable queuedAction;

        Fixture(GameTestHelper helper, EntityType<? extends Mob> driverType) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.origin = new BlockPos(8200 + ARENA.getAndIncrement() * 128, 5, 8200);
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos chunk = new BlockPos((this.origin.getX() >> 4) + x, 0, (this.origin.getZ() >> 4) + z);
                    this.level.setChunkForced(chunk.getX(), chunk.getZ(), true);
                    this.chunks.add(chunk);
                }
            }
            for (int x = -12; x <= 12; x++) {
                for (int z = -12; z <= 12; z++) {
                    this.level.setBlock(this.origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
                }
            }
            this.vehicle = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("superbwarfare", "m_1a_2"))
                    .create(this.level);
            this.vehicle.setPos(Vec3.atBottomCenterOf(this.origin));
            this.level.addFreshEntity(this.vehicle);
            SbwGameTestVehicle.prepareSmokeFixture(this.vehicle, 1, 0);
            this.controller = VehicleControllers.create(this.vehicle).orElseThrow();
            this.driver = driverType.create(this.level);
            this.driver.setPersistenceRequired();
            this.driver.setInvulnerable(true);
            this.driver.setPos(this.vehicle.position());
            this.level.addFreshEntity(this.driver);
            helper.assertTrue(this.driver.startRiding(this.vehicle, true), "Smoke driver could not mount");
            this.attacker = source(EntityType.COW);
            ACTIVE.add(this);
        }

        Mob source(EntityType<? extends Mob> type) {
            Mob source = type.create(this.level);
            source.setPersistenceRequired();
            source.setNoAi(true);
            source.setNoGravity(true);
            source.setInvulnerable(true);
            source.setPos(this.vehicle.position().add(0, 100, 0));
            this.level.addFreshEntity(source);
            this.extras.add(source);
            return source;
        }

        void start(GameTestHelper helper, Consumer<Fixture> scenario) {
            helper.runAtTickTime(50, () -> helper.assertTrue(this.started,
                    "Smoke fixture did not begin normal server ticks"));
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                    this.vehicle.tickCount >= 4 && this.driver.tickCount >= 4,
                    "Waiting for normal vehicle and driver ticks")).thenExecute(() -> {
                this.started = true;
                helper.assertTrue(this.driver.getVehicle() == this.vehicle, "Driver fixture left before observation");
                scenario.accept(this);
            });
        }

        void report(VehicleThreats.Stage stage, int freshness) {
            VehicleThreats.report(this.attacker, this.vehicle, stage, freshness);
        }

        int smoke() {
            // Spawn events measure emitted rounds without depending on neighboring chunk query visibility.
            return this.spawnedSmoke.size();
        }

        void assertVolley(GameTestHelper helper) {
            helper.assertTrue(smoke() == 8, "Expected one eight-entity smoke volley, observed " + smoke()
                    + ", entities="
                    + this.spawnedSmoke.stream().map(entity -> entity.getId() + ":" + entity.position()
                    + ":removed=" + entity.isRemoved() + ":tick=" + entity.tickCount).toList());
        }

        void assertNoSmoke(GameTestHelper helper) {
            helper.assertTrue(smoke() == 0, "Unexpected smoke deployment: " + smoke());
        }

        void expectVolley(GameTestHelper helper) {
            helper.runAfterDelay(8, () -> {
                assertVolley(helper);
                finish(helper);
            });
        }

        void expectNoSmoke(GameTestHelper helper, int ticks) {
            helper.runAfterDelay(ticks, () -> {
                assertNoSmoke(helper);
                finish(helper);
            });
        }

        void whenSmokeQueued(Runnable action) {
            this.queuedAction = action;
        }

        void expectCancelledSmoke(GameTestHelper helper) {
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(this.queueObserved, "Fixture never observed a queued native smoke input");
                assertNoSmoke(helper);
                helper.assertTrue(SbwGameTestVehicle.decoyStock(this.vehicle) == 1,
                        "Cancelled input consumed smoke ammunition");
                finish(helper);
            });
        }

        void finish(GameTestHelper helper) {
            cleanup();
            helper.succeed();
        }

        void cleanup() {
            this.level.getEntitiesOfClass(Entity.class, new AABB(this.origin).inflate(40))
                    .stream().filter(entity -> entity != this.vehicle && entity != this.driver)
                    .forEach(Entity::discard);
            this.extras.forEach(Entity::discard);
            this.spawnedSmoke.forEach(Entity::discard);
            this.driver.discard();
            this.vehicle.discard();
            this.chunks.forEach(chunk -> this.level.setChunkForced(chunk.getX(), chunk.getZ(), false));
            ACTIVE.remove(this);
        }
    }
}
