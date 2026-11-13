package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground;

import com.scarasol.vindicatorsrumble.api.vehicle.SeatHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleDimensions;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleInput;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleMovementType;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundSteeringTest {
    @Test
    void fallbackCollisionBoundsPreserveTheOriginAndQuarterTurn() {
        VehicleController controller = new TestController(Vec3.ZERO, 90.0F, Vec3.ZERO, VehicleMovementType.WHEEL);
        AABB local = controller.localCollisionBounds();

        assertEquals(0.0D, local.minX, 1.0E-9D);
        assertEquals(1.0D, local.maxX, 1.0E-9D);
        assertEquals(-1.0D, local.minZ, 1.0E-9D);
        assertEquals(0.0D, local.maxZ, 1.0E-9D);
    }

    @Test
    void fallbackCollisionBoundsContainEveryWorldBoxCornerAtAnyHeading() {
        Vec3 position = new Vec3(20.0D, 3.0D, -12.0D);
        for (float yaw : new float[]{0.0F, 30.0F, 45.0F, -127.0F, 270.0F}) {
            VehicleController controller = new TestController(position, yaw, Vec3.ZERO, VehicleMovementType.WHEEL);
            AABB local = controller.localCollisionBounds().inflate(1.0E-8D);
            double radians = Math.toRadians(yaw);
            for (int x = 0; x <= 1; x++) {
                for (int y = 0; y <= 1; y++) {
                    for (int z = 0; z <= 1; z++) {
                        Vec3 corner = new Vec3(x, y, z).subtract(position);
                        assertTrue(local.contains(
                                corner.x * Math.cos(radians) + corner.z * Math.sin(radians),
                                corner.y,
                                -corner.x * Math.sin(radians) + corner.z * Math.cos(radians)));
                    }
                }
            }
        }
    }

    @Test
    void sideWaypointInsideFullLockCircleNeedsManeuveringOnEitherSide() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, Vec3.ZERO,
                VehicleMovementType.WHEEL, 10.0D);

        assertTrue(GroundSteering.needsTurningRoom(controller, new Vec3(10.0D, 0.0D, 0.0D)));
        assertTrue(GroundSteering.needsTurningRoom(controller, new Vec3(-10.0D, 0.0D, 0.0D)));
        assertFalse(GroundSteering.needsTurningRoom(controller, new Vec3(25.0D, 0.0D, 0.0D)));
        assertFalse(GroundSteering.needsTurningRoom(controller, new Vec3(0.0D, 0.0D, 10.0D)));
    }

    @Test
    void turningRoomUsesControllerCapabilityInsteadOfOneVehicleThreshold() {
        Vec3 waypoint = new Vec3(25.0D, 0.0D, 0.0D);
        assertFalse(GroundSteering.needsTurningRoom(new TestController(Vec3.ZERO, 0.0F, Vec3.ZERO,
                VehicleMovementType.WHEEL, 10.0D), waypoint));
        assertTrue(GroundSteering.needsTurningRoom(new TestController(Vec3.ZERO, 0.0F, Vec3.ZERO,
                VehicleMovementType.WHEEL, 20.0D), waypoint));
    }

    @Test
    void turningRoomIsRelativeToVehiclePositionAndWrappedHeading() {
        TestController controller = new TestController(new Vec3(40.0D, 7.0D, 30.0D), 450.0F, Vec3.ZERO,
                VehicleMovementType.WHEEL, 10.0D);

        assertTrue(GroundSteering.needsTurningRoom(controller, new Vec3(40.0D, 7.0D, 40.0D)));
        assertFalse(GroundSteering.needsTurningRoom(controller, new Vec3(30.0D, 7.0D, 30.0D)));
    }

    @Test
    void vehiclesWithoutLimitedTurningRadiusKeepExistingSteering() {
        for (VehicleMovementType type : List.of(VehicleMovementType.WHEEL, VehicleMovementType.TRACK)) {
            TestController controller = new TestController(Vec3.ZERO, 0.0F, Vec3.ZERO, type);
            assertFalse(GroundSteering.needsTurningRoom(controller, new Vec3(10.0D, 0.0D, 0.0D)));
        }
    }

    @Test
    void movingIntoUnreachableTurnBrakesBeforeReversing() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, new Vec3(0.0D, 0.0D, 0.8D),
                VehicleMovementType.WHEEL, 10.0D);
        VehicleInput input = GroundSteering.steerToward(controller, new Vec3(10.0D, 0.0D, 0.0D),
                100.0D, false, 0).input();

        assertTrue(input.brake());
        assertFalse(input.forward());
        assertFalse(input.back());
    }

    @Test
    void unreachableTurnReversesWithOppositeSteeringAtLowSpeed() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, Vec3.ZERO,
                VehicleMovementType.WHEEL, 10.0D);
        VehicleInput rightSide = GroundSteering.steerToward(controller, new Vec3(10.0D, 0.0D, 0.0D),
                100.0D, false, 0).input();
        VehicleInput leftSide = GroundSteering.steerToward(controller, new Vec3(-10.0D, 0.0D, 0.0D),
                100.0D, false, 0).input();

        assertTrue(rightSide.back());
        assertTrue(rightSide.right());
        assertFalse(rightSide.forward());
        assertFalse(rightSide.left());
        assertTrue(leftSide.back());
        assertTrue(leftSide.left());
        assertFalse(leftSide.right());
    }

    @Test
    void intermediateWaypointGuidesForwardWithoutReversingIntoItsTurningCircle() {
        for (double speed : new double[]{0.0D, 0.05D, 0.3D}) {
            TestController controller = new TestController(Vec3.ZERO, 0.0F, new Vec3(0.0D, 0.0D, speed),
                    VehicleMovementType.WHEEL, 10.0D);
            VehicleInput input = GroundSteering.steerToward(controller, new Vec3(6.0D, 0.0D, 6.0D),
                    900.0D, false, false, 0).input();

            assertTrue(input.forward());
            assertTrue(input.left());
            assertFalse(input.back());
            assertFalse(input.brake());
        }
    }

    @Test
    void passingWaypointDoesNotApplyArrivalBraking() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, new Vec3(0.0D, 0.0D, 0.3D),
                VehicleMovementType.WHEEL, 10.0D);
        VehicleInput input = GroundSteering.steerToward(controller, new Vec3(0.0D, 0.0D, 1.0D),
                900.0D, false, false, 0).input();

        assertTrue(input.forward());
        assertFalse(input.back());
        assertFalse(input.brake());
    }

    @Test
    void sidewaysPassingWaypointStillRespectsWheelTurningRadius() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, Vec3.ZERO,
                VehicleMovementType.WHEEL, 10.0D);
        VehicleInput input = GroundSteering.steerToward(controller, new Vec3(10.0D, 0.0D, 0.0D),
                900.0D, false, false, 0).input();

        assertTrue(input.back());
        assertFalse(input.forward());
        assertFalse(input.brake());
    }

    @Test
    void backingTurnContinuesUntilReachableThenBrakesBeforeForward() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, new Vec3(0.0D, 0.0D, -0.3D),
                VehicleMovementType.WHEEL, 10.0D);
        VehicleInput inside = GroundSteering.steerToward(controller, new Vec3(10.0D, 0.0D, 0.0D),
                100.0D, false, 0).input();
        VehicleInput outside = GroundSteering.steerToward(controller, new Vec3(25.0D, 0.0D, 0.0D),
                625.0D, false, 0).input();

        assertTrue(inside.back());
        assertTrue(inside.right());
        assertFalse(inside.brake());
        assertTrue(outside.brake());
        assertFalse(outside.forward());
        assertFalse(outside.back());
    }

    @Test
    void forwardSteeringUsesRequestedGearWhileResidualMotionIsBackward() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, new Vec3(0.0D, 0.0D, -0.05D),
                VehicleMovementType.WHEEL);
        VehicleInput input = GroundSteering.steerToward(controller, new Vec3(8.0D, 0.0D, 12.0D),
                208.0D, false, 0).input();

        assertTrue(input.forward());
        assertTrue(input.left());
        assertFalse(input.back());
        assertFalse(input.right());
    }

    @Test
    void wheelReversesStraightToWaypointBehindInsteadOfCrawlingAround() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, Vec3.ZERO,
                VehicleMovementType.WHEEL, 10.0D);
        VehicleInput input = GroundSteering.steerToward(controller, new Vec3(0.0D, 0.0D, -16.0D),
                256.0D, false, 0).input();

        assertTrue(input.back());
        assertFalse(input.forward());
        assertFalse(input.left());
        assertFalse(input.right());
        assertFalse(input.brake());
    }

    @Test
    void distantDestinationAcceleratesWithShortLocalWaypointForWheelsAndTracks() {
        for (VehicleMovementType type : List.of(VehicleMovementType.WHEEL, VehicleMovementType.TRACK)) {
            TestController controller = new TestController(Vec3.ZERO, 0.0F, Vec3.ZERO, type);

            VehicleInput input = GroundSteering.steerToward(controller,
                    new Vec3(0.0D, 0.0D, 8.0D), 1600.0D, false, 0).input();

            assertTrue(input.forward());
            assertTrue(input.sprint());
            assertFalse(input.brake());
        }
    }

    @Test
    void nearbyDestinationDoesNotAccelerateDespiteDistantWaypoint() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, Vec3.ZERO, VehicleMovementType.WHEEL);

        VehicleInput input = GroundSteering.steerToward(controller,
                new Vec3(0.0D, 0.0D, 20.0D), 100.0D, false, 0).input();

        assertTrue(input.forward());
        assertFalse(input.sprint());
    }

    @Test
    void distantDestinationDoesNotOverrideTurningOrStableFire() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, Vec3.ZERO, VehicleMovementType.WHEEL);

        VehicleInput turning = GroundSteering.steerToward(controller,
                new Vec3(8.0D, 0.0D, 8.0D), 1600.0D, false, 0).input();
        VehicleInput stableFire = GroundSteering.steerToward(controller,
                new Vec3(0.0D, 0.0D, 8.0D), 1600.0D, true, 0).input();

        assertTrue(turning.left());
        assertFalse(turning.sprint());
        assertFalse(stableFire.sprint());
    }

    @Test
    void closeWaypointStillBrakesWithDistantDestination() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F,
                new Vec3(0.0D, 0.0D, 0.3D), VehicleMovementType.WHEEL);

        VehicleInput input = GroundSteering.steerToward(controller,
                new Vec3(0.0D, 0.0D, 1.0D), 1600.0D, false, 0).input();

        assertTrue(input.brake());
        assertFalse(input.forward());
        assertFalse(input.sprint());
    }

    @Test
    void ramSteeringFeedsThrottleWhenThereIsRunUpRoomAndTurnIsLarge() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, Vec3.ZERO, VehicleMovementType.WHEEL);

        VehicleInput input = GroundSteering.steerRamToward(controller, new Vec3(10.0D, 0.0D, 0.0D), 0).input();

        assertTrue(input.forward());
        assertTrue(input.left());
        assertFalse(input.brake());
    }

    @Test
    void ramSteeringKeepsThrottleWhenMovingFastWithRunUpRoom() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, new Vec3(0.3D, 0.0D, 0.0D), VehicleMovementType.WHEEL);

        VehicleInput input = GroundSteering.steerRamToward(controller, new Vec3(10.0D, 0.0D, 0.0D), 0).input();

        assertTrue(input.forward());
        assertTrue(input.left());
        assertFalse(input.brake());
    }

    @Test
    void ramSteeringBrakesBeforeAnUnreachableWheelTurn() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, new Vec3(0.0D, 0.0D, 0.8D),
                VehicleMovementType.WHEEL, 10.0D);
        VehicleInput input = GroundSteering.steerRamToward(controller, new Vec3(10.0D, 0.0D, 0.0D), 0).input();

        assertTrue(input.brake());
        assertFalse(input.forward());
        assertFalse(input.back());
    }

    @Test
    void ramSteeringKeepsThrottleOnShallowPassThroughApproach() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, new Vec3(0.0D, 0.0D, 0.6D),
                VehicleMovementType.WHEEL, 10.0D);
        VehicleInput input = GroundSteering.steerRamToward(controller, new Vec3(6.0D, 0.0D, 8.0D), 0).input();

        assertTrue(input.forward());
        assertFalse(input.back());
        assertFalse(input.brake());
    }

    @Test
    void rearRamTargetStartsAPoweredTurnOnEitherSideOfTheApproachBoundary() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, Vec3.ZERO,
                VehicleMovementType.WHEEL, 10.0D);
        for (double side : new double[]{-1.0D, 1.0D}) {
            for (double distance : new double[]{11.0D, 13.0D}) {
                VehicleInput input = GroundSteering.steerRamToward(controller,
                        new Vec3(side, 0.0D, -distance), 0).input();
                assertTrue(input.back());
                assertTrue(input.left() || input.right());
                assertFalse(input.forward());
            }
        }
    }

    @Test
    void rearRamTargetBrakesForwardMomentumBeforeTurningBack() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, new Vec3(0.0D, 0.0D, 0.6D),
                VehicleMovementType.WHEEL, 10.0D);
        VehicleInput input = GroundSteering.steerRamToward(controller, new Vec3(1, 0, -11), 0).input();
        assertTrue(input.brake());
        assertFalse(input.forward());
        assertFalse(input.back());
    }

    @Test
    void ramSteeringBrakesWhenTooCloseAndNotAligned() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, new Vec3(0.3D, 0.0D, 0.0D), VehicleMovementType.WHEEL);

        VehicleInput input = GroundSteering.steerRamToward(controller, new Vec3(3.0D, 0.0D, 0.0D), 0).input();

        assertFalse(input.forward());
        assertTrue(input.left());
        assertTrue(input.brake());
    }

    @Test
    void closeAngledWheelTargetRequestsPoweredTurnAtRest() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, Vec3.ZERO, VehicleMovementType.WHEEL, 10.0D);
        VehicleInput input = GroundSteering.steerRamToward(controller, new Vec3(5.0D, 0.0D, 3.0D), 0).input();

        assertTrue(input.back());
        assertTrue(input.right());
        assertFalse(input.forward());
    }

    @Test
    void ramWindupMovesStraightWithoutSteering() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, Vec3.ZERO, VehicleMovementType.WHEEL);

        VehicleInput input = GroundSteering.ramWindupAway(controller, new Vec3(10.0D, 0.0D, 0.0D), 0).input();

        assertTrue(input.back());
        assertFalse(input.left());
        assertFalse(input.right());
    }

    @Test
    void retreatBacksAwayWhenTargetIsInFront() {
        TestController controller = new TestController(Vec3.ZERO, -90.0F, Vec3.ZERO, VehicleMovementType.WHEEL);

        VehicleInput input = GroundSteering.retreatFrom(controller, new Vec3(10.0D, 0.0D, 0.0D), 0).input();

        assertTrue(input.back());
        assertFalse(input.forward());
    }

    @Test
    void retreatDrivesForwardWhenTargetIsBehind() {
        TestController controller = new TestController(Vec3.ZERO, 90.0F, Vec3.ZERO, VehicleMovementType.WHEEL);

        VehicleInput input = GroundSteering.retreatFrom(controller, new Vec3(10.0D, 0.0D, 0.0D), 0).input();

        assertTrue(input.forward());
        assertFalse(input.back());
    }

    @Test
    void maneuverBackUsesReverseSteeringInput() {
        VehicleInput input = GroundSteering.maneuverBack(-1);

        assertTrue(input.back());
        assertTrue(input.right());
        assertFalse(input.left());
        assertFalse(input.forward());

        VehicleInput oppositeInput = GroundSteering.maneuverBack(1);

        assertTrue(oppositeInput.back());
        assertTrue(oppositeInput.left());
        assertFalse(oppositeInput.right());
        assertFalse(oppositeInput.forward());
    }

    @Test
    void maneuverForwardCanSprintForRamRecovery() {
        VehicleInput input = GroundSteering.maneuverForward(1, true);

        assertTrue(input.forward());
        assertTrue(input.right());
        assertTrue(input.sprint());
        assertFalse(input.back());
    }

    private record TestController(Vec3 position, float yaw, Vec3 velocity,
                                  VehicleMovementType movementType,
                                  double estimatedGroundTurningRadius) implements VehicleController {
        TestController(Vec3 position, float yaw, Vec3 velocity, VehicleMovementType movementType) {
            this(position, yaw, velocity, movementType, 0.0D);
        }

        @Override
        public Entity entity() {
            return null;
        }

        @Override
        public Level level() {
            return null;
        }

        @Override
        public AABB boundingBox() {
            return new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public boolean isWreck() {
            return false;
        }

        @Override
        public boolean isControllable() {
            return true;
        }

        @Override
        public double estimatedHorizontalStoppingDistance() {
            return 0.0D;
        }

        @Override
        public VehicleDimensions pathingDimensions() {
            return VehicleDimensions.DEFAULT;
        }

        @Override
        public List<SeatHandle> seats() {
            return List.of();
        }

        @Override
        public Optional<SeatHandle> seatOf(Mob mob) {
            return Optional.empty();
        }

        @Override
        public Optional<Entity> controllingPassenger() {
            return Optional.empty();
        }

        @Override
        public List<Entity> passengers() {
            return List.of();
        }

        @Override
        public boolean canMount(Mob mob) {
            return false;
        }

        @Override
        public boolean mount(Mob mob) {
            return false;
        }

        @Override
        public boolean moveToSeat(Mob mob, SeatHandle seat) {
            return false;
        }

        @Override
        public boolean dismount(Mob mob) {
            return false;
        }

        @Override
        public boolean canDrive(Mob mob) {
            return true;
        }

        @Override
        public void drive(Mob mob, VehicleInput input) {
        }

        @Override
        public void stopDriving(Mob mob) {
        }

        @Override
        public List<WeaponHandle> weapons(Mob mob) {
            return List.of();
        }

        @Override
        public WeaponStatus weaponStatus(Mob mob, WeaponHandle weapon) {
            return new WeaponStatus(false, false, false, false, false, false, 0);
        }

        @Override
        public boolean selectWeapon(Mob mob, WeaponHandle weapon) {
            return false;
        }

        @Override
        public boolean aimAt(Mob mob, Entity target, Vec3 aimPoint) {
            return false;
        }

        @Override
        public boolean fire(Mob mob, Entity target) {
            return false;
        }

        @Override
        public boolean reload(Mob mob) {
            return false;
        }
    }
}
