package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.mojang.logging.LogUtils;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleThreats;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponHandle;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwGameTestVehicle;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.sona.init.SonaMobEffects;
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
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder("vindicators_rumble_helicopters")
@PrefixGameTestTemplate(false)
public final class HelicopterCombatGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();
    private static boolean previousAmmoRequirement;

    private HelicopterCombatGameTests() {
    }

    @BeforeBatch(batch = "helicopter_combat")
    public static void requireFiniteFixtureAmmo(ServerLevel level) {
        previousAmmoRequirement = CommonConfig.VEHICLE_AI_NEEDS_AMMO.get();
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(true);
    }

    @AfterBatch(batch = "helicopter_combat")
    public static void restoreAmmoRequirement(ServerLevel level) {
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(previousAmmoRequirement);
    }

    @GameTest(batch = "helicopter_combat", template = "empty", timeoutTicks = 260)
    public static void ah6FiresAtALevelAirborneTargetWithoutDivingAtTheGround(GameTestHelper helper) {
        verifyAirCombat(helper, "ah_6", 0.0D);
    }

    @GameTest(batch = "helicopter_combat", template = "empty", timeoutTicks = 260)
    public static void mi28FiresAtASlightlyLowerAirborneTargetWithoutASteepDive(GameTestHelper helper) {
        verifyAirCombat(helper, "mi_28", -4.0D);
    }

    @GameTest(batch = "helicopter_combat", template = "empty", timeoutTicks = 320)
    public static void ah6ReactsToLockWithoutDumpingItsDecoyMagazine(GameTestHelper helper) {
        verifyLockedDefense(helper, "ah_6", 8);
    }

    @GameTest(batch = "helicopter_combat", template = "empty", timeoutTicks = 320)
    public static void mi28ReactsToLockWithoutDumpingItsDecoyMagazine(GameTestHelper helper) {
        verifyLockedDefense(helper, "mi_28", 8);
    }

    @GameTest(batch = "helicopter_combat", template = "empty", timeoutTicks = 320)
    public static void exhaustedCountermeasuresDoNotBlockEvasiveMovement(GameTestHelper helper) {
        verifyLockedDefense(helper, "ah_6", 0);
    }

    private static void verifyLockedDefense(GameTestHelper helper, String helicopter, int decoys) {
        Fixture fixture = new Fixture(helper, helicopter, 0.0D);
        SbwGameTestVehicle.setDecoyStock(fixture.vehicle, decoys);
        long[] started = {-1};
        Vec3[] initialPosition = {null};
        double[] maxDisplacement = {0.0D};
        int[] firstRelease = {-1};
        helper.onEachTick(() -> {
            if (fixture.driver.getTarget() != fixture.target) {
                helper.assertTrue(started[0] < 0, "Defensive maneuver abandoned its acquired combat target");
                helper.assertTrue(helper.getTick() < 100, "Defense fixture failed natural target acquisition");
                return;
            }
            if (started[0] < 0) {
                started[0] = helper.getTick();
                initialPosition[0] = fixture.vehicle.position();
                VehicleThreats.report(fixture.targetVehicle, fixture.vehicle, VehicleThreats.Stage.LOCKED, 100);
            }
            int elapsed = (int) (helper.getTick() - started[0]);
            int stock = SbwGameTestVehicle.decoyStock(fixture.vehicle);
            if (stock < decoys && firstRelease[0] < 0) firstRelease[0] = elapsed;
            if (firstRelease[0] >= 0) {
                helper.assertTrue(decoys - stock <= 1 + (elapsed - firstRelease[0]) / 20,
                        "Defense consumed countermeasures every tick");
            }
            maxDisplacement[0] = Math.max(maxDisplacement[0],
                    fixture.vehicle.position().subtract(initialPosition[0]).horizontalDistance());
            helper.assertTrue(fixture.vehicle.isAlive() && !fixture.controller.isWreck(),
                    "Defensive maneuver wrecked the helicopter");
            helper.assertTrue(Math.abs(fixture.controller.pitch()) < 50.0F,
                    "Lock evasion introduced a steep ground attack dive");
            helper.assertTrue(fixture.vehicle.getY() >= fixture.origin.getY() + 12.0D,
                    "Lock evasion lost safe ground clearance");
            helper.assertTrue(fixture.vehicle.position().subtract(fixture.targetVehicle.position())
                    .horizontalDistance() < 64.0D, "Defense left acquisition range");
            if (elapsed < 180) return;
            LogUtils.getLogger().info("Helicopter defense vehicle={} initialDecoys={} remainingDecoys={} firstRelease={} maxDisplacement={} finalDistance={}",
                    helicopter, decoys, stock, firstRelease[0], maxDisplacement[0],
                    fixture.vehicle.position().subtract(fixture.targetVehicle.position()).horizontalDistance());
            helper.assertTrue(maxDisplacement[0] > 6.0D, "Lock pressure never produced a maneuver");
            helper.assertTrue(decoys == 0 ? stock == 0 : firstRelease[0] >= 0,
                    "Countermeasure capability was not respected");
            fixture.close();
            helper.succeed();
        });
    }

    @GameTest(batch = "helicopter_combat", template = "empty", timeoutTicks = 520)
    public static void opposingAh6sDoNotKeepRaisingEachOthersCombatHeight(GameTestHelper helper) {
        verifyReciprocalCombat(helper, "ah_6", "ah_6");
    }

    @GameTest(batch = "helicopter_combat", template = "empty", timeoutTicks = 520)
    public static void differentSizedHelicoptersDoNotKeepRaisingEachOthersCombatHeight(GameTestHelper helper) {
        verifyReciprocalCombat(helper, "ah_6", "mi_28");
    }

    @GameTest(batch = "helicopter_combat", template = "empty", timeoutTicks = 1000)
    public static void closeAh6sDoNotDisengageBeyondTargetRange(GameTestHelper helper) {
        verifyClosingCombat(helper, "ah_6", 16);
    }

    @GameTest(batch = "helicopter_combat", template = "empty", timeoutTicks = 1000)
    public static void closeDifferentHelicoptersDoNotDisengageBeyondTargetRange(GameTestHelper helper) {
        verifyClosingCombat(helper, "mi_28", 24);
    }

    @GameTest(batch = "helicopter_combat", template = "empty", timeoutTicks = 1000)
    public static void ah6BreaksStationaryAirborneFirePressureAndResumesFiring(GameTestHelper helper) {
        verifyTacticalEvasion(helper, "ah_6", null);
    }

    @GameTest(batch = "helicopter_combat", template = "empty", timeoutTicks = 1000)
    public static void mi28BreaksStationaryAirborneFirePressureAndResumesFiring(GameTestHelper helper) {
        verifyTacticalEvasion(helper, "mi_28", null);
    }

    @GameTest(batch = "helicopter_combat", template = "empty", timeoutTicks = 1000)
    public static void identicalPilotsManeuverWithoutContinuousMirroredOrbits(GameTestHelper helper) {
        verifyTacticalEvasion(helper, "ah_6", "ah_6");
    }

    @GameTest(batch = "helicopter_combat", template = "empty", timeoutTicks = 1000)
    public static void differentPilotsManeuverWithoutLosingRangeOrCombatHeight(GameTestHelper helper) {
        verifyTacticalEvasion(helper, "ah_6", "mi_28");
    }

    @GameTest(batch = "helicopter_combat", template = "empty", timeoutTicks = 1000)
    public static void changingIncomingSourcesDoesNotExtendTheActiveCrossing(GameTestHelper helper) {
        verifyTacticalEvasion(helper, "ah_6", null, true);
    }

    private static void verifyTacticalEvasion(GameTestHelper helper, String helicopter, String opponent) {
        verifyTacticalEvasion(helper, helicopter, opponent, false);
    }

    private static void verifyTacticalEvasion(GameTestHelper helper, String helicopter, String opponent,
                                             boolean replaceThreats) {
        Fixture fixture = new Fixture(helper, helicopter, 0.0D, opponent);
        VehicleController opposingController = VehicleControllers.create(fixture.targetVehicle).orElseThrow();
        if (opponent == null) {
            fixture.targetVehicle.setYRot(180.0F);
            SbwGameTestVehicle.prepareStationaryAirborneTarget(fixture.targetVehicle);
            SbwGameTestVehicle.increaseCombatFixtureDurability(fixture.vehicle);
        }
        double initialHeight = Math.max(fixture.vehicle.getBoundingBox().getCenter().y,
                fixture.targetVehicle.getBoundingBox().getCenter().y);
        long[] acquiredAt = {-1};
        Vec3[] initialPosition = {null};
        double[] maxDistance = {0.0D};
        double[] highest = {initialHeight};
        double[] maxCrossing = {0.0D};
        double[] previousBearing = {Double.NaN};
        double[] turn = {0.0D};
        double[] maxTurn = {0.0D};
        int[] lateShots = {0, 0};
        Entity[] incoming = {null};
        boolean[] checkedRelay = {false};
        helper.onEachTick(() -> {
            boolean acquired = fixture.driver.getTarget() == fixture.target
                    && (opponent == null || fixture.target.getTarget() == fixture.driver);
            if (!acquired) {
                helper.assertTrue(acquiredAt[0] < 0, "Tactical evasion lost an acquired opponent");
                helper.assertTrue(helper.getTick() < 100, "Tactical fixture failed natural acquisition");
                return;
            }
            if (acquiredAt[0] < 0) {
                if (opponent != null) {
                    NeutralMob pilot = (NeutralMob) fixture.target;
                    if (!fixture.driver.getUUID().equals(pilot.getPersistentAngerTarget())) return;
                    pilot.setRemainingPersistentAngerTime(1200);
                }
                acquiredAt[0] = helper.getTick();
                initialPosition[0] = fixture.vehicle.position();
            }
            long elapsed = helper.getTick() - acquiredAt[0];
            if (replaceThreats && !checkedRelay[0]) {
                if (fixture.evasionFinishes > 0) {
                    helper.assertTrue(fixture.evasionStarts == 1,
                            "A replacement threat restarted an unfinished crossing");
                    helper.assertTrue(fixture.vehicle.position().subtract(initialPosition[0]).horizontalDistance() < 24.0D,
                            "Replacement threats extended the finite crossing");
                    checkedRelay[0] = true;
                    VehicleThreats.clearSource(incoming[0]);
                } else if (elapsed % 3 == 0) {
                    if (incoming[0] != null) incoming[0].discard();
                    incoming[0] = EntityType.SNOWBALL.create(fixture.level);
                    incoming[0].setPos(fixture.targetVehicle.position());
                    incoming[0].setNoGravity(true);
                    incoming[0].setDeltaMovement(0.0D, 0.0D, -0.2D);
                    fixture.level.addFreshEntity(incoming[0]);
                    fixture.additionalEntities.add(incoming[0]);
                    VehicleThreats.report(incoming[0], fixture.vehicle, VehicleThreats.Stage.INCOMING, 10);
                }
            }
            Vec3 separation = fixture.targetVehicle.position().subtract(fixture.vehicle.position());
            double distance = separation.horizontalDistance();
            double bearing = Math.toDegrees(Math.atan2(separation.x, separation.z));
            double crossingRadius = fixture.controller.horizontalFootprintRadius()
                    + opposingController.horizontalFootprintRadius();
            // A fly-through flips the target bearing without tracing an orbit.
            if (distance <= crossingRadius) {
                previousBearing[0] = Double.NaN;
                turn[0] = 0.0D;
            } else {
                if (!Double.isNaN(previousBearing[0])) {
                    turn[0] += net.minecraft.util.Mth.wrapDegrees(bearing - previousBearing[0]);
                }
                previousBearing[0] = bearing;
            }
            maxTurn[0] = Math.max(maxTurn[0], Math.abs(turn[0]));
            maxDistance[0] = Math.max(maxDistance[0], distance);
            maxCrossing[0] = Math.max(maxCrossing[0],
                    Math.abs(fixture.vehicle.getX() - initialPosition[0].x));
            double range = opponent == null ? fixture.driver.getAttributeValue(Attributes.FOLLOW_RANGE)
                    : Math.min(fixture.driver.getAttributeValue(Attributes.FOLLOW_RANGE),
                    fixture.target.getAttributeValue(Attributes.FOLLOW_RANGE));
            helper.assertTrue(distance <= range, "Tactical crossing left target range: " + distance);
            helper.assertTrue(fixture.driver.isPassenger() && fixture.target.isPassenger(),
                    "Tactical crossing lost a passenger");
            helper.assertTrue(!fixture.controller.isWreck() && !opposingController.isWreck(),
                    "Tactical crossing wrecked an aircraft");
            double height = Math.max(fixture.vehicle.getBoundingBox().getCenter().y,
                    fixture.targetVehicle.getBoundingBox().getCenter().y);
            highest[0] = Math.max(highest[0], height);
            helper.assertTrue(height <= initialHeight + 8.0D,
                    "Tactical crossing raised combat height: height=" + height + " initial=" + initialHeight
                            + " elapsed=" + elapsed + " decisions=" + fixture.driverDecision + "/" + fixture.targetDecision);
            helper.assertTrue(fixture.vehicle.getY() >= fixture.origin.getY() + 12.0D,
                    "Tactical crossing lost ground clearance");
            helper.assertTrue(maxTurn[0] < 180.0D, "Tactical crossing became a sustained orbit");
            if (elapsed == 360) {
                lateShots[0] = fixture.firedShots;
                lateShots[1] = fixture.targetFiredShots;
            }
            if (elapsed % 20 == 0) {
                LogUtils.getLogger().info("Helicopter tactical tick vehicles={}/{} elapsed={} distance={} maxDistance={} cross={} relativeTurn={} shots={}/{} decisions={}/{}",
                        helicopter, opponent, elapsed, distance, maxDistance[0], maxCrossing[0],
                        turn[0], fixture.firedShots, fixture.targetFiredShots,
                        fixture.driverDecision, fixture.targetDecision);
            }
            if (elapsed < 720) return;
            LogUtils.getLogger().info("Helicopter tactical summary vehicles={}/{} crossing={} maxDistance={} highest={} initialHeight={} maxRelativeTurn={} segments={}/{} shots={}/{} lateShots={}/{}",
                    helicopter, opponent, maxCrossing[0], maxDistance[0], highest[0], initialHeight, maxTurn[0],
                    fixture.evasionStarts, fixture.targetEvasionStarts, fixture.firedShots,
                    fixture.targetFiredShots, fixture.firedShots - lateShots[0],
                    fixture.targetFiredShots - lateShots[1]);
            helper.assertTrue(maxCrossing[0] > 6.0D, "Airborne pressure never produced meaningful crossing");
            helper.assertTrue(!replaceThreats || checkedRelay[0], "Incoming crossing never finished");
            helper.assertTrue(fixture.firedShots > lateShots[0]
                            && (opponent == null || fixture.targetFiredShots > lateShots[1]),
                    "Tactical crossing did not return to sustained combat");
            fixture.close();
            helper.succeed();
        });
    }

    private static void verifyClosingCombat(GameTestHelper helper, String opponent, int separation) {
        Fixture fixture = new Fixture(helper, "ah_6", 0.0D, opponent, separation);
        fixture.vehicle.setDeltaMovement(0.0D, 0.0D, 0.6D);
        fixture.targetVehicle.setDeltaMovement(0.0D, 0.0D, -0.6D);
        long[] acquiredAt = {-1};
        double[] maxDistance = {0.0D};
        int[] previousAnger = {-1};
        helper.onEachTick(() -> {
            if (fixture.driver.getTarget() != fixture.target || fixture.target.getTarget() != fixture.driver) {
                helper.assertTrue(acquiredAt[0] < 0,
                        "Close air combat lost an acquired opponent: driver=" + fixture.driver.getTarget()
                                + " opponent=" + fixture.target.getTarget() + " distance="
                                + fixture.driver.position().subtract(fixture.target.position()).horizontalDistance()
                                + " alive=" + fixture.driver.isAlive() + "/" + fixture.target.isAlive()
                                + " health=" + fixture.driver.getHealth() + "/" + fixture.target.getHealth()
                                + " passengers=" + fixture.driver.isPassenger() + "/" + fixture.target.isPassenger()
                                + " range=" + fixture.driver.getAttributeValue(Attributes.FOLLOW_RANGE) + "/"
                                + fixture.target.getAttributeValue(Attributes.FOLLOW_RANGE)
                                + " elapsed=" + (helper.getTick() - acquiredAt[0])
                                + " anger=" + ((NeutralMob) fixture.target).getRemainingPersistentAngerTime()
                                + " previousAnger=" + previousAnger[0]
                                + " canAttack=" + fixture.target.canAttack(fixture.driver)
                                + " seen=" + fixture.target.getSensing().hasLineOfSight(fixture.driver)
                                + " effects=" + fixture.driver.getActiveEffects()
                                + " targetGoals=" + fixture.target.targetSelector.getRunningGoals()
                                .map(goal -> goal.getGoal().getClass().getSimpleName()).toList()
                                + " lastEvent=" + fixture.targetEvent);
                helper.assertTrue(helper.getTick() < 100, "Close combat failed natural mutual acquisition");
                return;
            }
            NeutralMob opposingPilot = (NeutralMob) fixture.target;
            if (acquiredAt[0] < 0) {
                if (!fixture.driver.getUUID().equals(opposingPilot.getPersistentAngerTarget())) return;
                acquiredAt[0] = helper.getTick();
                // Exclude native anger expiry from the movement observation, after natural acquisition.
                opposingPilot.setRemainingPersistentAngerTime(1000);
            }
            previousAnger[0] = opposingPilot.getRemainingPersistentAngerTime();
            double distance = fixture.driver.position().subtract(fixture.target.position()).horizontalDistance();
            maxDistance[0] = Math.max(maxDistance[0], distance);
            double range = Math.min(fixture.driver.getAttributeValue(Attributes.FOLLOW_RANGE),
                    fixture.target.getAttributeValue(Attributes.FOLLOW_RANGE));
            if ((helper.getTick() - acquiredAt[0]) % 20 == 0) {
                LogUtils.getLogger().info("Helicopter closing combat opponent={} elapsed={} distance={} maxDistance={} range={} velocities={}/{}",
                        opponent, helper.getTick() - acquiredAt[0], distance, maxDistance[0], range,
                        fixture.vehicle.getDeltaMovement(), fixture.targetVehicle.getDeltaMovement());
                LogUtils.getLogger().info("Helicopter closing decisions driver={} opponent={}",
                        fixture.driverDecision, fixture.targetDecision);
            }
            helper.assertTrue(distance <= range, "Evasion left mutual target range: " + distance + " > " + range);
            helper.assertTrue(fixture.driver.isPassenger() && fixture.target.isPassenger(),
                    "Close air combat lost a passenger");
            if (helper.getTick() - acquiredAt[0] < 720) return;
            LogUtils.getLogger().info("Helicopter closing summary opponent={} elapsed={} maxDistance={} range={} firedShots={}/{}",
                    opponent, helper.getTick() - acquiredAt[0], maxDistance[0], range,
                    fixture.firedShots, fixture.targetFiredShots);
            helper.assertTrue(fixture.firedShots > 0 && fixture.targetFiredShots > 0,
                    "Close maneuvering prevented either opponent from fighting");
            fixture.close();
            helper.succeed();
        });
    }

    @GameTest(batch = "helicopter_combat", template = "empty", timeoutTicks = 340)
    public static void roofTargetKeepsAnAlreadySafeCombatHeight(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "ah_6", 0.0D);
        for (int x = -8; x <= 8; x++) {
            for (int z = 32; z <= 48; z++) {
                fixture.level.setBlock(fixture.origin.offset(x, 31, z), Blocks.BEDROCK.defaultBlockState(), 2);
            }
        }
        fixture.target.stopRiding();
        fixture.target.setPos(Vec3.atBottomCenterOf(fixture.origin.offset(0, 32, 40)));
        fixture.target.setOnGround(true);
        double initialHeight = fixture.target.getY() + 12.0D;
        fixture.vehicle.setPos(fixture.vehicle.getX(), initialHeight, fixture.vehicle.getZ());
        SbwGameTestVehicle.prepareUnarmedAirborne(fixture.vehicle);
        SbwGameTestVehicle.setHoverFixture(fixture.vehicle);
        long[] acquiredAt = {-1};
        helper.onEachTick(() -> {
            CommonConfig.VEHICLE_AI_DEBUG.set(true);
            if (helper.getTick() % 20 == 0) {
                LogUtils.getLogger().info("Helicopter roof height tick={} acquired={} aircraftY={} targetY={} initialHeight={} power={} velocity={} goals={}",
                        helper.getTick(), fixture.driver.getTarget() == fixture.target, fixture.vehicle.getY(),
                        fixture.target.getY(), initialHeight, fixture.controller.power(), fixture.vehicle.getDeltaMovement(),
                        fixture.driver.goalSelector.getRunningGoals()
                                .map(goal -> goal.getGoal().getClass().getSimpleName()).toList());
            }
            helper.assertTrue(fixture.vehicle.getY() >= initialHeight - 4.0D,
                    "Safe rooftop combat descended toward the LOS boundary: aircraftY=" + fixture.vehicle.getY());
            helper.assertTrue(fixture.vehicle.getY() <= initialHeight + 5.0D,
                    "Safe rooftop combat kept climbing");
            if (fixture.driver.getTarget() != fixture.target) {
                helper.assertTrue(acquiredAt[0] < 0, "Rooftop target was lost after acquisition");
                helper.assertTrue(helper.getTick() < 100, "Rooftop target was not naturally acquired");
                return;
            }
            if (acquiredAt[0] < 0) acquiredAt[0] = helper.getTick();
            if (helper.getTick() - acquiredAt[0] < 220) return;
            fixture.close();
            helper.succeed();
        });
    }

    @GameTest(batch = "helicopter_combat", template = "empty", timeoutTicks = 80)
    public static void heavyWeaponUsesGroundImpactOnlyForAGroundedTarget(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "ah_6", 0.0D);
        WeaponHandle rocket = fixture.controller.weapons(fixture.driver).stream()
                .filter(weapon -> weapon.payloadType().isHeavyTargetPreferred()).findFirst().orElseThrow();
        fixture.target.stopRiding();
        Vec3 groundPosition = Vec3.atBottomCenterOf(fixture.origin.offset(0, 0, 40));
        fixture.target.setPos(groundPosition);
        fixture.target.setNoGravity(false);
        fixture.target.setOnGround(true);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(fixture.target.onGround(), "Grounded target has not settled");
        }).thenExecute(() -> {
            Vec3 point = fixture.controller.aimPoint(fixture.driver, fixture.target, rocket);
            helper.assertTrue(Math.abs(point.y - groundPosition.y) < 0.2D,
                    "Grounded explosive target no longer uses a ground impact: " + point);
            fixture.target.setPos(groundPosition.add(0.0D, 32.0D, 0.0D));
            fixture.target.setOnGround(false);
            fixture.target.setNoGravity(true);
            point = fixture.controller.aimPoint(fixture.driver, fixture.target, rocket);
            helper.assertTrue(point.distanceTo(fixture.target.getBoundingBox().getCenter()) < 0.01D,
                    "Unmounted airborne target was projected onto the ground: " + point);
            fixture.close();
            helper.succeed();
        });
    }

    private static void verifyAirCombat(GameTestHelper helper, String helicopter, double targetHeightOffset) {
        Fixture fixture = new Fixture(helper, helicopter, targetHeightOffset);
        long[] acquiredAt = {-1};
        int[] alignedTicks = {0};
        double[] maxShallowPitch = {0.0D};
        double[] highestTargetY = {fixture.targetVehicle.getBoundingBox().getCenter().y};
        helper.onEachTick(() -> {
            helper.assertTrue(fixture.driver.isPassenger() && fixture.target.isPassenger(),
                    "Air combat lost a passenger");
            if (helper.getTick() % 20 == 0) {
                LogUtils.getLogger().info("Helicopter air combat tick={} vehicle={} driverTick={} targetTick={} acquired={} position={} targetPosition={} pitch={} goals={}",
                        helper.getTick(), helicopter, fixture.driver.tickCount, fixture.target.tickCount,
                        fixture.driver.getTarget(), fixture.vehicle.position(), fixture.targetVehicle.position(),
                        fixture.controller.pitch(), fixture.driver.goalSelector.getRunningGoals()
                                .map(goal -> goal.getGoal().getClass().getSimpleName()).toList());
            }
            if (fixture.driver.getTarget() != fixture.target) {
                helper.assertTrue(acquiredAt[0] < 0, "Naturally acquired air target was lost");
                return;
            }
            if (acquiredAt[0] < 0) acquiredAt[0] = helper.getTick();
            Vec3 center = fixture.targetVehicle.getBoundingBox().getCenter();
            highestTargetY[0] = Math.max(highestTargetY[0], center.y);
            helper.assertTrue(fixture.vehicle.getY() < highestTargetY[0] + 12.0D,
                    "Air pass kept climbing instead of tracking target height: aircraftY="
                            + fixture.vehicle.getY() + " highestTargetY=" + highestTargetY[0]);
            List<WeaponHandle> heavyWeapons = fixture.controller.weapons(fixture.driver).stream()
                    .filter(weapon -> weapon.payloadType().isHeavyTargetPreferred()).toList();
            helper.assertFalse(heavyWeapons.isEmpty(), "Air combat fixture has no heavy weapon");
            for (WeaponHandle weapon : heavyWeapons) {
                Vec3 point = fixture.controller.aimPoint(fixture.driver, fixture.target, weapon);
                helper.assertTrue(point.distanceTo(center) < 0.01D,
                        "Air weapon " + weapon.name() + " aims away from the target vehicle: " + point + " vs " + center);
                if (fixture.controller.isAimedAt(fixture.driver, fixture.target, point, weapon)) {
                    alignedTicks[0]++;
                }
            }
            Vec3 delta = center.subtract(fixture.vehicle.position());
            double elevation = -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
            if (delta.horizontalDistance() > 20.0D && Math.abs(elevation) < 15.0D) {
                double pitch = Math.abs(fixture.controller.pitch());
                maxShallowPitch[0] = Math.max(maxShallowPitch[0], pitch);
                helper.assertTrue(pitch < 45.0D,
                        "Shallow airborne target forced a steep dive: elevation=" + elevation + " pitch=" + pitch);
            }
            if (helper.getTick() - acquiredAt[0] < 120) return;
            LogUtils.getLogger().info("Helicopter air combat vehicle={} targetOffset={} alignedTicks={} firedShots={} maxShallowPitch={} distance={} elevation={}",
                    helicopter, targetHeightOffset, alignedTicks[0], fixture.firedShots, maxShallowPitch[0],
                    delta.horizontalDistance(), elevation);
            helper.assertTrue(alignedTicks[0] > 0, "Aircraft never aimed a weapon at the actual airborne target");
            helper.assertTrue(fixture.firedShots > 0, "Air combat never fired with the actual target UUID");
            fixture.close();
            helper.succeed();
        });
    }

    private static void verifyReciprocalCombat(GameTestHelper helper, String helicopter, String opponent) {
        Fixture fixture = new Fixture(helper, helicopter, 0.0D, opponent);
        VehicleController targetController = VehicleControllers.create(fixture.targetVehicle).orElseThrow();
        double initialHeight = Math.max(fixture.vehicle.getBoundingBox().getCenter().y,
                fixture.targetVehicle.getBoundingBox().getCenter().y);
        double[] highest = {initialHeight};
        long[] acquiredAt = {-1};
        helper.onEachTick(() -> {
            double height = Math.max(fixture.vehicle.getBoundingBox().getCenter().y,
                    fixture.targetVehicle.getBoundingBox().getCenter().y);
            highest[0] = Math.max(highest[0], height);
            if (helper.getTick() % 20 == 0) {
                LogUtils.getLogger().info("Helicopter reciprocal combat tick={} vehicles={}/{} acquired={}/{} heights={}/{} initialHeight={} highest={} pitch={}/{} shots={}/{}",
                        helper.getTick(), helicopter, opponent, fixture.driver.getTarget() == fixture.target,
                        fixture.target.getTarget() == fixture.driver, fixture.vehicle.getBoundingBox().getCenter().y,
                        fixture.targetVehicle.getBoundingBox().getCenter().y, initialHeight, highest[0],
                        fixture.controller.pitch(), targetController.pitch(), fixture.firedShots, fixture.targetFiredShots);
                LogUtils.getLogger().info("Helicopter reciprocal decisions driver={} opponent={}",
                        fixture.driverDecision, fixture.targetDecision);
            }
            helper.assertTrue(fixture.driver.isPassenger() && fixture.target.isPassenger(),
                    "Reciprocal combat lost a passenger: driverAlive=" + fixture.driver.isAlive()
                            + " targetAlive=" + fixture.target.isAlive() + " driverVehicle=" + fixture.driver.getVehicle()
                            + " targetVehicle=" + fixture.target.getVehicle() + " wrecks="
                            + fixture.controller.isWreck() + "/" + targetController.isWreck());
            helper.assertTrue(height <= initialHeight + 8.0D,
                    "Opposing aircraft kept climbing: height=" + height + " initialHeight=" + initialHeight);
            if (fixture.driver.getTarget() != fixture.target || fixture.target.getTarget() != fixture.driver) {
                helper.assertTrue(acquiredAt[0] < 0, "Reciprocal combat lost a naturally acquired target");
                helper.assertTrue(helper.getTick() < 120, "Opposing pilots did not naturally acquire each other");
                return;
            }
            if (acquiredAt[0] < 0) acquiredAt[0] = helper.getTick();
            if (helper.getTick() - acquiredAt[0] < 360) return;
            helper.assertTrue(fixture.firedShots > 0 && fixture.targetFiredShots > 0,
                    "Both aircraft must actually fire during reciprocal combat");
            fixture.close();
            helper.succeed();
        });
    }

    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;
        final BlockPos origin;
        final List<BlockPos> chunks = new ArrayList<>();
        final List<Entity> additionalEntities = new ArrayList<>();
        final Entity vehicle;
        final Entity targetVehicle;
        final Mob driver;
        final Mob target;
        final VehicleController controller;
        final org.apache.logging.log4j.core.Logger logger;
        final AbstractAppender observer;
        int firedShots;
        int targetFiredShots;
        int evasionStarts;
        int targetEvasionStarts;
        int evasionFinishes;
        String driverDecision;
        String targetDecision;
        String targetEvent;

        Fixture(GameTestHelper helper, String helicopter, double targetHeightOffset) {
            this(helper, helicopter, targetHeightOffset, null);
        }

        Fixture(GameTestHelper helper, String helicopter, double targetHeightOffset, String opponent) {
            this(helper, helicopter, targetHeightOffset, opponent, 40);
        }

        Fixture(GameTestHelper helper, String helicopter, double targetHeightOffset, String opponent, int separation) {
            CommonConfig.VEHICLE_AI_DEBUG.set(true);
            this.level = helper.getLevel();
            this.origin = new BlockPos(4096 + ARENA.getAndIncrement() * 256, 5, 4096);
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 7; z++) {
                    BlockPos chunk = new BlockPos((this.origin.getX() >> 4) + x, 0, (this.origin.getZ() >> 4) + z);
                    this.level.setChunkForced(chunk.getX(), chunk.getZ(), true);
                    this.chunks.add(chunk);
                }
            }
            for (int x = -32; x <= 32; x++) {
                for (int z = -32; z <= 112; z++) {
                    this.level.setBlock(this.origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
                }
            }
            this.vehicle = this.create(helicopter, Vec3.atBottomCenterOf(this.origin).add(0.0D, 32.0D, 0.0D));
            this.targetVehicle = this.create(opponent == null ? helicopter : opponent,
                    Vec3.atBottomCenterOf(this.origin.offset(0, 0, separation)).add(0.0D, 32.0D + targetHeightOffset, 0.0D));
            this.controller = VehicleControllers.create(this.vehicle).orElseThrow();
            this.target = opponent == null ? EntityType.VILLAGER.create(this.level)
                    : (Mob) ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("guardvillagers", "guard"))
                    .create(this.level);
            this.target.setNoAi(opponent == null);
            this.target.setPersistenceRequired();
            this.target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255, false, false));
            this.target.setPos(this.targetVehicle.position());
            this.target.addEffect(new MobEffectInstance(SonaMobEffects.EXPOSURE.get(), 600, 3, false, false));
            this.level.addFreshEntity(this.target);
            helper.assertTrue(this.target.startRiding(this.targetVehicle, true), "Air target could not mount");
            this.driver = EntityType.VINDICATOR.create(this.level);
            this.driver.setPersistenceRequired();
            this.driver.setInvulnerable(opponent == null);
            this.driver.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255, false, false));
            this.driver.addEffect(new MobEffectInstance(SonaMobEffects.EXPOSURE.get(), 600, 3, false, false));
            this.driver.setPos(this.vehicle.position());
            this.level.addFreshEntity(this.driver);
            helper.assertTrue(this.driver.startRiding(this.vehicle, true), "Combat driver could not mount");
            SbwGameTestVehicle.prepareArmedAirborne(this.vehicle);
            if (opponent != null) {
                this.targetVehicle.setYRot(180.0F);
                SbwGameTestVehicle.prepareArmedAirborne(this.targetVehicle);
                SbwGameTestVehicle.increaseCombatFixtureDurability(this.vehicle);
                SbwGameTestVehicle.increaseCombatFixtureDurability(this.targetVehicle);
            }
            this.logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(
                    "com.scarasol.vindicatorsrumble.VindicatorsRumbleMod");
            String driverPrefix = "[entity.minecraft.vindicator#" + this.driver.getId() + "]";
            String targetPrefix = "[entity.guardvillagers.guard#" + this.target.getId() + "]";
            String vehiclePrefix = "#" + this.vehicle.getId() + "]";
            String targetVehiclePrefix = "#" + this.targetVehicle.getId() + "]";
            this.observer = new AbstractAppender("air_combat_" + this.driver.getId(), null, null, false) {
                @Override
                public void append(LogEvent event) {
                    String message = event.getMessage().getFormattedMessage();
                    if (message.contains(targetPrefix)) targetEvent = message;
                    if (message.contains("Helicopter flight tick=")) {
                        if (message.contains(driverPrefix)) driverDecision = message;
                        if (message.contains(targetPrefix)) targetDecision = message;
                    }
                    if (message.contains("Helicopter evasion started")
                            || message.contains("Helicopter evasive braking")
                            || message.contains("Helicopter evasion finished")) {
                        if (message.contains(vehiclePrefix)) {
                            if (message.contains("started")) evasionStarts++;
                            if (message.contains("finished")) evasionFinishes++;
                        }
                        if (message.contains(targetVehiclePrefix) && message.contains("started")) targetEvasionStarts++;
                        if (message.contains(vehiclePrefix) || message.contains(targetVehiclePrefix)) {
                            LogUtils.getLogger().info("Helicopter maneuver event {}", message);
                        }
                    }
                    if (message.contains("SBW fire called")
                            && message.contains("groundTarget=false") && message.contains("canShootBefore=true")) {
                        if (message.contains(driverPrefix)) firedShots++;
                        if (message.contains(targetPrefix)) targetFiredShots++;
                    }
                }
            };
            this.observer.start();
            this.logger.addAppender(this.observer);
        }

        private Entity create(String helicopter, Vec3 position) {
            Entity entity = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("superbwarfare", helicopter))
                    .create(this.level);
            entity.setPos(position);
            entity.setInvulnerable(true);
            this.level.addFreshEntity(entity);
            SbwGameTestVehicle.prepareUnarmedAirborne(entity);
            SbwGameTestVehicle.setHoverFixture(entity);
            return entity;
        }

        @Override
        public void close() {
            this.logger.removeAppender(this.observer);
            this.observer.stop();
            this.driver.discard();
            this.target.discard();
            this.vehicle.discard();
            this.targetVehicle.discard();
            this.additionalEntities.forEach(Entity::discard);
            this.chunks.forEach(chunk -> this.level.setChunkForced(chunk.getX(), chunk.getZ(), false));
        }
    }
}
