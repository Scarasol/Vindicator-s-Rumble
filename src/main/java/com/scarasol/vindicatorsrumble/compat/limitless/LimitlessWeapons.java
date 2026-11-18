package com.scarasol.vindicatorsrumble.compat.limitless;

import com.scarasol.vindicatorsrumble.api.vehicle.WeaponAimMode;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponFireMode;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponFireWindow;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponFireWindowState;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponPayloadType;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponStatus;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.util.AllegianceUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.ItemHandlerHelper;
import org.ywzj.vehicle.custom.weapon.data.VehicleCannonWeaponData;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;
import org.ywzj.vehicle.util.VectorUtil;
import org.ywzj.vehicle.vehicle.PhysicsEngine;
import org.ywzj.vehicle.vehicle.part.PartUnit;
import org.ywzj.vehicle.vehicle.part.WeaponUnit;
import org.ywzj.vehicle.vehicle.pojo.AimContext;
import org.ywzj.vehicle.vehicle.pojo.Explosion;
import org.ywzj.vehicle.vehicle.weapon.AbstractVehicleWeapon;
import org.ywzj.vehicle.vehicle.weapon.VehicleCannon;
import org.ywzj.vehicle.vehicle.weapon.VehicleMultiWeapons;
import org.ywzj.vehicle.vehicle.weapon.VehicleRocket;

import java.util.ArrayList;
import java.util.List;

final class LimitlessWeapons {
    private static final WeaponStatus UNAVAILABLE = new WeaponStatus(false, false, false, false, false, false, 0);
    private final AbstractVehicle vehicle;
    private WeaponHandle selected;
    private WeaponUnit selectedStation;

    LimitlessWeapons(AbstractVehicle vehicle) {
        this.vehicle = vehicle;
    }

    static boolean hasWeapons(PartUnit<?> part) {
        return part instanceof WeaponUnit station && !station.isDestroyed() && !station.isDetached()
                && !guns(station).isEmpty();
    }

    private WeaponUnit station(Mob mob) {
        if (this.vehicle.level().isClientSide() || mob.getVehicle() != this.vehicle
                || this.vehicle.isRemoved() || this.vehicle.isDestroyed()) {
            return null;
        }
        return this.vehicle.getOwnOperatorUnit(mob) instanceof WeaponUnit unit && unit.getOwner() == mob
                && !unit.isDestroyed() && !unit.isDetached() ? unit : null;
    }

    private static List<Gun> guns(WeaponUnit station) {
        List<Gun> guns = new ArrayList<>();
        List<AbstractVehicleWeapon<?>> indexed = station.getIndexedWeapons();
        for (int index = 0; index < indexed.size(); index++) {
            AbstractVehicleWeapon<?> slot = indexed.get(index);
            if (slot instanceof VehicleMultiWeapons multi) {
                for (int variant = 0; variant < multi.getSubWeapons().size(); variant++) {
                    Gun gun = createGun(station, multi.getSubWeapons().get(variant), index, variant);
                    if (gun != null) {
                        guns.add(gun);
                    }
                }
            } else {
                Gun gun = createGun(station, slot, index, -1);
                if (gun != null) {
                    guns.add(gun);
                }
            }
        }
        return guns;
    }

    private static Gun createGun(WeaponUnit station, AbstractVehicleWeapon<?> weapon, int index, int variant) {
        WeaponUnit mount = weapon.getWeaponUnit();
        boolean useParent = mount.isParentWeaponUnitAim()
                || mount.getParentWeaponUnit() != null && mount.getYRotSpeed() <= 0.0F;
        WeaponUnit aim = useParent ? mount.getRootParentWeaponUnit() : mount;
        if (!aim.rotByAim || aim.xTurnGroup == null || mount.getBolts().isEmpty()) {
            return null;
        }
        if (weapon instanceof VehicleCannon cannon && !cannon.getData().isArtillery() || weapon instanceof VehicleRocket) {
            return new Gun(station, aim, weapon, index, variant);
        }
        return null;
    }

    List<WeaponHandle> handles(Mob mob) {
        WeaponUnit station = this.station(mob);
        return station == null ? List.of() : guns(station).stream().filter(Gun::available).map(Gun::handle).toList();
    }

    private Gun resolve(Mob mob, WeaponHandle handle) {
        WeaponUnit station = this.station(mob);
        if (station == null || handle == null) {
            return null;
        }
        int index = handle.id() >>> 16;
        int variant = (handle.id() & 0xffff) - 1;
        List<AbstractVehicleWeapon<?>> indexed = station.getIndexedWeapons();
        if (index >= indexed.size()) {
            return null;
        }
        AbstractVehicleWeapon<?> weapon = indexed.get(index);
        if (weapon instanceof VehicleMultiWeapons multi) {
            if (variant < 0 || variant >= multi.getSubWeapons().size()) {
                return null;
            }
            weapon = multi.getSubWeapons().get(variant);
        } else if (variant != -1) {
            return null;
        }
        Gun gun = createGun(station, weapon, index, variant);
        return gun != null && gun.available() ? gun : null;
    }

    private boolean selected(Gun gun) {
        return this.selectedStation == gun.station && this.selected != null && this.selected.id() == gun.id()
                && (gun.variant < 0 || gun.station.getIndexedWeapons().get(gun.index) instanceof VehicleMultiWeapons multi
                && multi.getSelectedIndex() == gun.variant);
    }

    WeaponStatus status(Mob mob, WeaponHandle handle) {
        Gun gun = this.resolve(mob, handle);
        if (gun == null) {
            return UNAVAILABLE;
        }
        AbstractVehicleWeapon<?> weapon = gun.weapon;
        boolean freeAmmo = !CommonConfig.VEHICLE_AI_NEEDS_AMMO.get();
        boolean loaded = freeAmmo || weapon.hasAmmo();
        boolean reloading = !freeAmmo && weapon.isReloading();
        boolean reload = !loaded && weapon.canReload();
        boolean canFire = loaded && !weapon.isReloading() && !weapon.isCoolingDown();
        return new WeaponStatus(this.selected(gun), canFire, reload, reloading, false, loaded || reload || reloading, 0);
    }

    boolean select(Mob mob, WeaponHandle handle) {
        Gun gun = this.resolve(mob, handle);
        if (gun == null) {
            return false;
        }
        var slot = gun.station.getIndexedWeapons().get(gun.index);
        int primary = gun.station.weapons.indexOf(slot);
        int secondary = gun.station.secondaryWeapons.indexOf(slot);
        if (primary >= 0 && primary != gun.station.getCurrentWeaponIndex()) {
            gun.station.selectWeaponIndex(false, primary, false);
        } else if (secondary >= 0 && secondary != gun.station.getCurrentSecondaryWeaponIndex()) {
            gun.station.selectWeaponIndex(true, secondary, false);
        }
        if (slot instanceof VehicleMultiWeapons multi && gun.variant >= 0) {
            multi.setSelectedIndex(gun.variant);
        }
        this.selected = handle;
        this.selectedStation = gun.station;
        return true;
    }

    void stop() {
        this.selected = null;
        this.selectedStation = null;
    }

    boolean hasVehicleAmmo() {
        if (!CommonConfig.VEHICLE_AI_NEEDS_AMMO.get()) {
            return true;
        }
        for (var seat : this.vehicle.seats) {
            if (seat.partUnit instanceof WeaponUnit station && !station.isDestroyed() && !station.isDetached()) {
                for (Gun gun : guns(station)) {
                    if (gun.available() && (gun.weapon.hasAmmo() || gun.weapon.hasStorageAmmo())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    boolean reload(Mob mob) {
        Gun gun = this.resolve(mob, this.selected);
        if (gun == null || !CommonConfig.VEHICLE_AI_NEEDS_AMMO.get() || gun.weapon.hasAmmo() || !gun.weapon.canReload()) {
            return false;
        }
        gun.weapon.startReload();
        return true;
    }

    void stockGeneratedWeapons() {
        if (!CommonConfig.VEHICLE_AI_NEEDS_AMMO.get()) {
            return;
        }
        for (var seat : this.vehicle.seats) {
            if (seat.partUnit instanceof WeaponUnit station) {
                for (Gun gun : guns(station)) {
                    gun.weapon.setRemainAmmo(gun.weapon.getMaxCapacity());
                    ItemStack[] ammo = gun.weapon.getData().getReload().getAmmo().getItems();
                    if (ammo.length > 0) {
                        ItemStack reserve = ammo[0].copyWithCount(Math.min(ammo[0].getMaxStackSize(), gun.weapon.getMaxCapacity()));
                        this.vehicle.getCapability(ForgeCapabilities.ITEM_HANDLER)
                                .ifPresent(inventory -> ItemHandlerHelper.insertItemStacked(inventory, reserve, false));
                    }
                }
            }
        }
    }

    Vec3 aimPoint(Mob mob, WeaponHandle handle, Vec3 target) {
        Gun gun = this.resolve(mob, handle);
        if (gun == null || !(gun.weapon instanceof VehicleCannon cannon)) {
            return target;
        }
        // The cannon round inherits vehicle motion and loses speed each native tick.
        Vec3 muzzle = cannon.getWeaponUnit().aimContext().from;
        double speed = cannon.getData().getVelocity();
        if (speed <= 0.0D) {
            return target;
        }
        double drag = Mth.clamp(cannon.getData().getFriction(), 0.0D, 0.99D);
        double time = muzzle.distanceTo(target) / speed;
        Vec3 point = target;
        for (int i = 0; i < 4; i++) {
            double travel = drag < 1.0E-5D ? time
                    : -Math.expm1(time * Math.log1p(-drag)) / drag;
            if (travel <= 1.0E-5D) {
                break;
            }
            double gravity = drag < 1.0E-5D ? time * (time - 1.0D) * 0.5D : (time - travel) / drag;
            point = target.subtract(this.vehicle.getDeltaMovement().scale(travel))
                    .add(0.0D, PhysicsEngine.G * Math.max(0.0D, gravity), 0.0D);
            double distance = muzzle.distanceTo(point);
            time = drag < 1.0E-5D ? distance / speed
                    : Math.log1p(-Math.min(0.99D, distance * drag / speed)) / Math.log1p(-drag);
            time = Math.min(time, cannon.getData().getLife());
        }
        return point;
    }

    boolean aim(Mob mob, Vec3 aimPoint) {
        Gun gun = this.resolve(mob, this.selected);
        if (gun == null || !this.selected(gun)) {
            return false;
        }
        gun.aim.aim(aimPoint);
        return true;
    }

    boolean hasLineOfSight(Mob mob, Entity target, Vec3 center) {
        Gun gun = this.resolve(mob, this.selected);
        // The driving Goal owns a separate controller with no weapon selection.
        return gun == null ? mob.hasLineOfSight(target)
                : this.clear(gun.weapon.getWeaponUnit().aimContext().from, center);
    }

    WeaponFireWindow fireWindow(Mob mob, WeaponHandle handle, Vec3 aimPoint) {
        Gun gun = this.resolve(mob, handle);
        if (gun == null) {
            return WeaponFireWindow.impossible(0.0D, 0.0D, 0.0D);
        }
        AimContext muzzle = gun.weapon.getWeaponUnit().aimContext();
        Vec3 delta = aimPoint.subtract(muzzle.from);
        Vec3 direction = VectorUtil.rotToVec(muzzle.direction.x, muzzle.direction.y);
        Vec2 demand = gun.aim.aimRot(aimPoint);
        float pitch = Mth.wrapDegrees(demand.x - gun.aim.xBarrelSelfRot);
        float yaw = Mth.wrapDegrees(demand.y - gun.aim.yBarrelSelfRot);
        double pitchError = Math.abs(Mth.wrapDegrees(pitch - gun.aim.getXRot()));
        double yawError = Math.abs(Mth.wrapDegrees(yaw - gun.aim.getYRot()));
        double miss = delta.cross(direction).length();
        if (delta.lengthSqr() > handle.maxRange() * handle.maxRange()
                || pitch < gun.aim.xRotMin - 1.0F || pitch > gun.aim.xRotMax + 1.0F
                || (gun.aim.yRotMax - gun.aim.yRotMin < 359.0F
                && (yaw < gun.aim.yRotMin - 1.0F || yaw > gun.aim.yRotMax + 1.0F))) {
            return WeaponFireWindow.impossible(yawError, pitchError, miss);
        }
        if (delta.dot(direction) > 0.0D && miss < 0.55D) {
            return WeaponFireWindow.readyNow(yawError, pitchError, miss);
        }
        return WeaponFireWindow.inWindow(yawError, pitchError, miss);
    }

    boolean fireSelected(Mob mob, Entity target, Vec3 center) {
        return this.fire(mob, target, this.aimPoint(mob, this.selected, center), center, this.selected);
    }

    boolean fire(Mob mob, Entity target, Vec3 aimPoint, Vec3 center, WeaponHandle handle) {
        Gun gun = this.resolve(mob, handle);
        if (gun == null || target == null || !target.isAlive() || !this.selected(gun)
                || !this.status(mob, handle).canFire()
                || this.fireWindow(mob, handle, aimPoint).state() != WeaponFireWindowState.READY_NOW
                || AllegianceUtil.shouldIgnoreFriendlyDamage(target, mob)) {
            return false;
        }
        WeaponUnit mount = gun.weapon.getWeaponUnit();
        List<AimContext> shots = new ArrayList<>(switch (mount.getFiringMode()) {
            case RIPPLE -> List.of(mount.aimContext());
            case SALVO, FULL_SALVO -> mount.aimContexts();
        });
        if (shots.isEmpty()) {
            return false;
        }
        for (AimContext shot : shots) {
            if (!this.clear(shot.from, center) || this.hasFriendInShot(mob, shot.from, center, gun.blastRadius())) {
                return false;
            }
        }
        boolean freeAmmo = !CommonConfig.VEHICLE_AI_NEEDS_AMMO.get();
        int original = gun.weapon.getRemainAmmo();
        int available = freeAmmo ? Math.max(original, shots.size()) : original;
        if (available != original) {
            gun.weapon.setRemainAmmo(available);
        }
        try {
            gun.station.shoot(gun.index, shots, mob);
            return gun.weapon.getRemainAmmo() < available;
        } finally {
            if (freeAmmo) {
                gun.weapon.setRemainAmmo(original);
            }
        }
    }

    private boolean clear(Vec3 from, Vec3 to) {
        return this.vehicle.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this.vehicle)).getType() == HitResult.Type.MISS;
    }

    private boolean hasFriendInShot(Mob mob, Vec3 from, Vec3 to, double blastRadius) {
        AABB area = new AABB(from, to).inflate(Math.max(0.5D, blastRadius));
        AABB blast = new AABB(to, to).inflate(blastRadius);
        for (Entity entity : this.vehicle.level().getEntities(this.vehicle, area,
                entity -> entity.isAlive() && entity.getRootVehicle() != this.vehicle)) {
            AABB bounds = entity.getBoundingBox();
            if ((bounds.inflate(0.35D).clip(from, to).isPresent() || blastRadius > 0 && bounds.intersects(blast))
                    && AllegianceUtil.shouldIgnoreFriendlyDamage(entity, mob)) {
                return true;
            }
        }
        return false;
    }

    private record Gun(WeaponUnit station, WeaponUnit aim, AbstractVehicleWeapon<?> weapon, int index, int variant) {
        int id() {
            return index << 16 | variant + 1;
        }

        boolean available() {
            return !this.weapon.getWeaponUnit().isDestroyed() && !this.weapon.getWeaponUnit().isDetached()
                    && !this.aim.isDetached() && !this.aim.isDestroyed()
                    && (!this.aim.needPower || this.station.getVehicle().hasPower());
        }

        WeaponHandle handle() {
            var data = this.weapon.getData();
            double cadence = Math.max(1.0D, this.weapon.getShootInterval());
            double range = 96.0D;
            WeaponPayloadType payload = WeaponPayloadType.ROCKET;
            if (data instanceof VehicleCannonWeaponData cannon) {
                double drag = Mth.clamp(cannon.getFriction(), 0.0D, 0.99D);
                double travel = drag <= 1.0E-5D ? data.getLife()
                        : -Math.expm1(data.getLife() * Math.log1p(-drag)) / drag;
                range = Mth.clamp(data.getVelocity() * travel * 0.8D, 1.0D, 128.0D);
                payload = cannon.getCaliber() != null && cannon.getCaliber() >= 20.0F
                        ? WeaponPayloadType.SHELL : WeaponPayloadType.BULLET;
            }
            return new WeaponHandle(this.id(), this.weapon.getDisplayName().getString(), this.blastRadius(), range,
                    this.index, WeaponAimMode.INDEPENDENT, payload, WeaponFireMode.AUTOMATIC, 60_000.0D / cadence, 0.0D);
        }

        double blastRadius() {
            Explosion explosion = this.weapon.getData() instanceof VehicleCannonWeaponData cannon
                    ? cannon.getExplosion() : ((VehicleRocket) this.weapon).getData().getExplosion();
            return explosion != null && explosion.explode ? explosion.radius : 0.0D;
        }
    }
}
