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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundSteeringTest {
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
    void ramSteeringBrakesWhenTooCloseAndNotAligned() {
        TestController controller = new TestController(Vec3.ZERO, 0.0F, new Vec3(0.3D, 0.0D, 0.0D), VehicleMovementType.WHEEL);

        VehicleInput input = GroundSteering.steerRamToward(controller, new Vec3(3.0D, 0.0D, 0.0D), 0).input();

        assertFalse(input.forward());
        assertTrue(input.left());
        assertTrue(input.brake());
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
                                  VehicleMovementType movementType) implements VehicleController {
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
