package com.scarasol.vindicatorsrumble.compat.limitless;

import com.scarasol.vindicatorsrumble.api.vehicle.SeatHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleDimensions;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleInput;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleMovementType;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponFireWindow;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponFireWindowState;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponStatus;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;
import org.ywzj.vehicle.entity.vehicle.TrackedVehicle;
import org.ywzj.vehicle.entity.vehicle.WheeledVehicle;
import org.ywzj.vehicle.util.PhysicsHelper;
import org.ywzj.vehicle.util.VectorUtil;
import org.ywzj.vehicle.vehicle.control.ControlUnit;
import org.ywzj.vehicle.vehicle.structure.VehicleCubeOBB;

import java.util.List;
import java.util.Optional;

final class LimitlessVehicleController implements VehicleController {
    private final AbstractVehicle vehicle;
    private final LimitlessWeapons weapons;

    LimitlessVehicleController(AbstractVehicle vehicle) {
        this.vehicle = vehicle;
        this.weapons = new LimitlessWeapons(vehicle);
    }

    @Override
    public Entity entity() {
        return this.vehicle;
    }

    @Override
    public Level level() {
        return this.vehicle.level();
    }

    @Override
    public AABB boundingBox() {
        return this.vehicle.getBoundingBox();
    }

    @Override
    public void placeGenerationCandidate(Vec3 position, float yaw) {
        this.vehicle.moveTo(position.x, position.y, position.z, yaw, 0.0F);
        this.vehicle.setYRot(yaw);
        this.vehicle.updateOBBs();
        this.vehicle.setBoundingBox(this.vehicle.makeBoundingBox());
    }

    @Override
    public AABB localCollisionBounds() {
        VehicleCubeOBB hull = this.vehicle.getMainCubeOBB();
        if (hull == null || this.vehicle.seats.isEmpty()) {
            return VehicleController.super.localCollisionBounds();
        }
        Vec3 origin = this.position();
        float inverseYaw = (float) Math.toRadians(this.yaw());
        AABB box = this.localBounds(hull, origin, inverseYaw);
        for (VehicleCubeOBB body : this.vehicle.getVehicleCubeOBBs()) {
            box = box.minmax(this.localBounds(body, origin, inverseYaw));
        }
        return box;
    }

    private AABB localBounds(VehicleCubeOBB cube, Vec3 origin, float inverseYaw) {
        AABB box = null;
        for (var vertex : cube.obb().getVertices()) {
            Vec3 point = new Vec3(vertex).subtract(origin).yRot(inverseYaw);
            box = box == null ? new AABB(point, point) : box.minmax(new AABB(point, point));
        }
        return box;
    }

    @Override
    public VehicleDimensions pathingDimensions() {
        AABB bounds = this.localCollisionBounds();
        return new VehicleDimensions(bounds.getXsize(), bounds.getYsize(), bounds.getZsize());
    }

    @Override
    public double horizontalFootprintRadius() {
        AABB bounds = this.localCollisionBounds();
        return Math.hypot(Math.max(Math.abs(bounds.minX), Math.abs(bounds.maxX)),
                Math.max(Math.abs(bounds.minZ), Math.abs(bounds.maxZ)));
    }

    @Override
    public boolean isValid() {
        return !this.vehicle.isRemoved() && this.vehicle.isAlive();
    }

    @Override
    public boolean isWreck() {
        return this.vehicle.isDestroyed();
    }

    @Override
    public boolean isControllable() {
        return this.isValid() && !this.isWreck() && !this.vehicle.seats.isEmpty();
    }

    @Override
    public Vec3 position() {
        return this.vehicle.position();
    }

    @Override
    public float yaw() {
        return this.vehicle.getYRot();
    }

    @Override
    public Vec3 velocity() {
        return this.vehicle.getDeltaMovement();
    }

    @Override
    public boolean hasHorizontalCollision() {
        // LV resolves contact in its own physics engine without setting the vanilla flag.
        return VehicleController.super.hasHorizontalCollision() || this.vehicle.physicsEngine.stuckTick > 0;
    }

    @Override
    public VehicleMovementType movementType() {
        return this.vehicle instanceof WheeledVehicle ? VehicleMovementType.WHEEL : VehicleMovementType.TRACK;
    }

    private double brakeStep() {
        if (this.vehicle instanceof TrackedVehicle tracked) {
            return tracked.brakeAcceleration;
        }
        WheeledVehicle wheeled = (WheeledVehicle) this.vehicle;
        return PhysicsHelper.accelerationPerTick(wheeled.brakeForce, wheeled.physicsEngine.physicsInfo.mass);
    }

    @Override
    public double estimatedHorizontalStoppingDistance() {
        double speed = this.velocity().horizontalDistance();
        double deceleration = this.brakeStep();
        if (this.vehicle instanceof WheeledVehicle && this.vehicle.onGround()) {
            var physics = this.vehicle.physicsEngine.physicsInfo;
            // Wheels also lose speed to ground friction and 2% handbrake damping each tick.
            deceleration += PhysicsHelper.accelerationPerTick(physics.friction, physics.mass) + speed * 0.01D;
        }
        return 0.5D * speed + speed * speed / (2.0D * Math.max(0.001D, deceleration));
    }

    @Override
    public double estimatedGroundTurningRadius() {
        if (this.vehicle instanceof WheeledVehicle wheeled && wheeled.maxSpeedForward > 0.0F
                && wheeled.getMainCubeOBB() != null) {
            double speedRatio = Math.min(1.0D, this.velocity().horizontalDistance() / wheeled.maxSpeedForward);
            return wheeled.getMainCubeOBB().depth / (1.0D - 0.5D * speedRatio);
        }
        return 0.0D;
    }

    @Override
    public boolean engineStarted() {
        return this.vehicle.isEngineOn();
    }

    @Override
    public float power() {
        return this.vehicle.getPower();
    }

    @Override
    public List<SeatHandle> seats() {
        return this.vehicle.seats.stream().map(this::seatHandle).toList();
    }

    @Override
    public Optional<SeatHandle> seatOf(Mob mob) {
        return this.vehicle.seats.stream().filter(seat -> seat.passengerId == mob.getId())
                .findFirst().map(this::seatHandle);
    }

    private SeatHandle seatHandle(AbstractVehicle.Seat seat) {
        Optional<Entity> occupant = this.vehicle.getPassengers().stream()
                .filter(passenger -> passenger.getId() == seat.passengerId).findFirst();
        return new SeatHandle(seat.seatIndex, seat.seatIndex == 0, LimitlessWeapons.hasWeapons(seat.partUnit), occupant);
    }

    @Override
    public Optional<Entity> controllingPassenger() {
        return Optional.ofNullable(this.vehicle.getDriver());
    }

    @Override
    public List<Entity> passengers() {
        return this.vehicle.getPassengers();
    }

    @Override
    public boolean canMount(Mob mob) {
        return this.isControllable() && this.vehicle.tickCount > 0
                && this.vehicle.seats.stream().anyMatch(seat -> seat.passengerId == -1);
    }

    @Override
    public boolean mount(Mob mob) {
        return this.canMount(mob) && mob.startRiding(this.vehicle);
    }

    @Override
    public boolean moveToSeat(Mob mob, SeatHandle seat) {
        int id = seat.id();
        if (mob.getVehicle() != this.vehicle || id < 0 || id >= this.vehicle.seats.size()) {
            return false;
        }
        int occupant = this.vehicle.seats.get(id).passengerId;
        if (occupant == mob.getId()) {
            return true;
        }
        if (occupant != -1) {
            return false;
        }
        if (this.vehicle.getDriver() == mob) {
            this.vehicle.controlUnit.reset();
        }
        return this.vehicle.changeSeat(mob, id);
    }

    @Override
    public boolean dismount(Mob mob) {
        if (mob.getVehicle() != this.vehicle) {
            return false;
        }
        mob.stopRiding();
        return mob.getVehicle() != this.vehicle;
    }

    @Override
    public boolean canDrive(Mob mob) {
        return this.isControllable() && mob.getVehicle() == this.vehicle && this.vehicle.getDriver() == mob;
    }

    @Override
    public void drive(Mob mob, VehicleInput input) {
        if (this.level().isClientSide() || !this.canDrive(mob)) {
            return;
        }
        if (!CommonConfig.VEHICLE_AI_NEEDS_FUEL.get() && this.vehicle.getEnergy() <= 0.0F) {
            this.vehicle.setEnergy(this.vehicle.energyInfo.energyCapacity);
        }
        ControlUnit controls = this.vehicle.controlUnit;
        controls.reset();
        double speed = this.velocity().dot(VectorUtil.rotToVec(this.vehicle.getXRot(), this.yaw()));
        double brakeStep = this.brakeStep();
        if (input.brake()) {
            controls.forward = speed < -brakeStep - 0.001D;
            controls.backward = speed > brakeStep + 0.001D;
            controls.up = this.vehicle instanceof WheeledVehicle;
        } else {
            controls.forward = input.forward();
            controls.backward = input.back();
        }
        // Native steering follows actual motion, while the AI expresses steering in the requested gear.
        boolean backing = speed < -0.001D || Math.abs(speed) <= 0.001D && controls.backward;
        boolean swapSteering = input.back() != backing;
        controls.left = swapSteering ? input.right() : input.left();
        controls.right = swapSteering ? input.left() : input.right();
    }

    @Override
    public void stopDriving(Mob mob) {
        if (!this.level().isClientSide() && this.vehicle.getDriver() == mob) {
            this.vehicle.controlUnit.reset();
            this.vehicle.controlUnit.up = this.vehicle instanceof WheeledVehicle;
        }
    }

    @Override
    public void initializeGeneratedSupplies() {
        this.vehicle.setEnergy(this.vehicle.energyInfo.energyCapacity);
        this.weapons.stockGeneratedWeapons();
    }

    @Override
    public List<WeaponHandle> weapons(Mob mob) {
        return this.weapons.handles(mob);
    }

    @Override
    public WeaponStatus weaponStatus(Mob mob, WeaponHandle weapon) {
        return this.weapons.status(mob, weapon);
    }

    @Override
    public boolean hasAnyWeaponAmmo(Mob mob) {
        return this.weapons.hasVehicleAmmo();
    }

    @Override
    public boolean selectWeapon(Mob mob, WeaponHandle weapon) {
        return this.weapons.select(mob, weapon);
    }

    @Override
    public Vec3 aimPoint(Mob mob, Entity target, WeaponHandle weapon) {
        return this.weapons.aimPoint(mob, weapon, this.aimPoint(mob, target));
    }

    @Override
    public boolean aimAt(Mob mob, Entity target, Vec3 aimPoint) {
        return this.weapons.aim(mob, aimPoint);
    }

    @Override
    public boolean hasLineOfSight(Mob mob, Entity target, Vec3 aimPoint) {
        return target != null && this.weapons.hasLineOfSight(mob, target, this.aimPoint(mob, target));
    }

    @Override
    public WeaponFireWindow fireWindow(Mob mob, Entity target, Vec3 aimPoint, WeaponHandle weapon) {
        return this.weapons.fireWindow(mob, weapon, aimPoint);
    }

    @Override
    public boolean isAimedAt(Mob mob, Entity target, Vec3 aimPoint, WeaponHandle weapon) {
        return this.weapons.fireWindow(mob, weapon, aimPoint).state() == WeaponFireWindowState.READY_NOW;
    }

    @Override
    public void stopWeaponControl(Mob mob) {
        this.weapons.stop();
        // The common dismount hook runs after Entity.vehicle is cleared but before LV clears its seat.
        if (!this.level().isClientSide() && mob.getVehicle() != this.vehicle && this.vehicle.getDriver() == mob) {
            this.vehicle.controlUnit.reset();
        }
    }

    @Override
    public boolean fire(Mob mob, Entity target) {
        return this.weapons.fireSelected(mob, target, this.aimPoint(mob, target));
    }

    @Override
    public boolean fire(Mob mob, Entity target, Vec3 aimPoint, WeaponHandle weapon) {
        return this.weapons.fire(mob, target, aimPoint, this.aimPoint(mob, target), weapon);
    }

    @Override
    public boolean reload(Mob mob) {
        return this.weapons.reload(mob);
    }
}
