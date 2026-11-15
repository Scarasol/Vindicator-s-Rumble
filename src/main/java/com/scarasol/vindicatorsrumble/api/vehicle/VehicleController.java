package com.scarasol.vindicatorsrumble.api.vehicle;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public interface VehicleController {
    Entity entity();

    Level level();

    AABB boundingBox();

    /** Collision bounds relative to the vehicle origin in its yaw-aligned frame. */
    default AABB localCollisionBounds() {
        AABB box = this.boundingBox();
        Vec3 offset = box.getCenter().subtract(this.position());
        double radians = Math.toRadians(this.yaw());
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = offset.x * cos + offset.z * sin;
        double z = -offset.x * sin + offset.z * cos;
        double halfWidth = (Math.abs(cos) * box.getXsize() + Math.abs(sin) * box.getZsize()) * 0.5D;
        double halfLength = (Math.abs(sin) * box.getXsize() + Math.abs(cos) * box.getZsize()) * 0.5D;
        return new AABB(x - halfWidth, box.minY - this.position().y, z - halfLength,
                x + halfWidth, box.maxY - this.position().y, z + halfLength);
    }

    /** Horizontal footprint radius about the vehicle origin, independent of yaw when available. */
    default double horizontalFootprintRadius() {
        return this.pathingDimensions().conservativeHorizontalSize() * 0.5D;
    }

    boolean isValid();

    boolean isWreck();

    boolean isControllable();

    Vec3 position();

    float yaw();

    Vec3 velocity();

    double estimatedHorizontalStoppingDistance();

    /** Full-lock turn radius at current speed; zero when no estimate is available. */
    default double estimatedGroundTurningRadius() {
        return 0.0D;
    }

    default double estimatedStabilizedHorizontalStoppingDistance() {
        return this.estimatedHorizontalStoppingDistance();
    }

    VehicleMovementType movementType();

    default boolean usesGroundNavigation() {
        return switch (this.movementType()) {
            case WHEEL, TRACK, SHIP -> true;
            default -> false;
        };
    }

    default boolean supportsFlightControl() {
        return false;
    }

    default Optional<VehicleThreats.Threat> threat() {
        return VehicleThreats.strongest(this.entity());
    }

    /** Unsupported, empty or unavailable countermeasures must return false. */
    default boolean canDeployDecoy() {
        return false;
    }

    default boolean supportsSmoke() {
        return false;
    }

    default boolean canDeploySmoke() {
        return false;
    }

    /** Whether smoke can redirect this incoming entity's actual guidance. */
    default boolean isSmokeEffectiveAgainst(Entity source) {
        return false;
    }

    default float pitch() {
        return this.entity().getXRot();
    }

    default float roll() {
        return 0.0F;
    }

    default boolean hoverMode() {
        return false;
    }

    default boolean engineStarted() {
        return true;
    }

    default float power() {
        return 0.0F;
    }

    default double altitudeAboveGround(double maxDistance) {
        AABB box = this.boundingBox();
        Vec3 start = new Vec3(box.getCenter().x, box.minY + 0.1D, box.getCenter().z);
        Vec3 end = start.add(0.0D, -maxDistance, 0.0D);
        HitResult hit = this.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this.entity()));
        return hit.getType() == HitResult.Type.MISS ? maxDistance : start.y - hit.getLocation().y;
    }

    VehicleDimensions pathingDimensions();

    List<SeatHandle> seats();

    Optional<SeatHandle> seatOf(Mob mob);

    Optional<Entity> controllingPassenger();

    List<Entity> passengers();

    boolean canMount(Mob mob);

    boolean mount(Mob mob);

    boolean moveToSeat(Mob mob, SeatHandle seat);

    boolean dismount(Mob mob);

    boolean canDrive(Mob mob);

    void drive(Mob mob, VehicleInput input);

    default void controlFlight(Mob mob, VehicleFlightInput input) {
        this.drive(mob, input.input());
    }

    default void initializeGeneratedSupplies() {
    }

    /** Place a generation candidate and synchronize native collision geometry before validation. */
    default void placeGenerationCandidate(Vec3 position, float yaw) {
        this.entity().moveTo(position.x, position.y, position.z, yaw, 0.0F);
    }

    default boolean initializeAirborneHover(Mob mob) {
        return false;
    }

    void stopDriving(Mob mob);

    List<WeaponHandle> weapons(Mob mob);

    WeaponStatus weaponStatus(Mob mob, WeaponHandle weapon);

    default boolean hasAnyWeaponAmmo(Mob mob) {
        return true;
    }

    boolean selectWeapon(Mob mob, WeaponHandle weapon);

    default Vec3 aimPoint(Mob mob, Entity target) {
        if (target == null) {
            return Vec3.ZERO;
        }
        Entity aimEntity = target.getVehicle() == null ? target : target.getVehicle();
        return aimEntity.getBoundingBox().getCenter();
    }

    default Vec3 aimPoint(Mob mob, Entity target, WeaponHandle weapon) {
        return this.aimPoint(mob, target);
    }

    boolean aimAt(Mob mob, Entity target, Vec3 aimPoint);

    default boolean hasLineOfSight(Mob mob, Entity target, Vec3 aimPoint) {
        return target != null && mob.hasLineOfSight(target);
    }

    default boolean isAimedAt(Mob mob, Entity target, Vec3 aimPoint, WeaponHandle weapon) {
        return true;
    }

    default WeaponFireWindow fireWindow(Mob mob, Entity target, Vec3 aimPoint, WeaponHandle weapon) {
        return WeaponFireWindow.unknown();
    }

    default void stopWeaponControl(Mob mob) {
    }

    boolean fire(Mob mob, Entity target);

    default boolean fire(Mob mob, Entity target, Vec3 aimPoint, WeaponHandle weapon) {
        return this.fire(mob, target);
    }

    boolean reload(Mob mob);
}
