package com.scarasol.vindicatorsrumble.compat.sbw;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.entity.projectile.IglaMissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class SbwGameTestVehicle {
    private SbwGameTestVehicle() {
    }

    public static void prepareUnarmedAirborne(Entity entity) {
        if (!(entity instanceof VehicleEntity vehicle)) {
            throw new IllegalArgumentException("Not an SBW vehicle: " + entity.getType());
        }
        for (int slot = 0; slot < vehicle.getContainerSize(); slot++) {
            vehicle.setItem(slot, ItemStack.EMPTY);
        }
        for (String weapon : List.copyOf(vehicle.getGunDataMap().keySet())) {
            vehicle.modifyGunData(weapon, data -> {
                data.ammo.set(0);
                data.virtualAmmo.set(0);
                data.backupAmmoCount.set(0);
            });
        }
        vehicle.setEngineStart(true);
        vehicle.setEngineStartOver(true);
        vehicle.setPower(0.12F);
        vehicle.setEnergy(vehicle.getMaxEnergy());
        vehicle.setHoverMode(false);
        vehicle.setRoll(0.0F);
        vehicle.setRollAngle(0.0F);
        vehicle.setRollVelocity(0.0F);
        vehicle.setPitchAngle(0.0F);
        vehicle.setPitchVelocity(0.0F);
    }

    public static FlightControl flightControl(Entity entity) {
        if (!(entity instanceof VehicleEntity vehicle)) {
            throw new IllegalArgumentException("Not an SBW vehicle: " + entity.getType());
        }
        return new FlightControl(
                vehicle.getMouseMoveSpeedX(),
                vehicle.getMouseMoveSpeedY(),
                vehicle.getDeltaRot(),
                vehicle.getSynchedPropellerRot(),
                vehicle.forwardInputDown(),
                vehicle.backInputDown(),
                vehicle.leftInputDown(),
                vehicle.rightInputDown(),
                vehicle.upInputDown(),
                vehicle.downInputDown(),
                vehicle.sprintInputDown());
    }

    public static void prepareArmedAirborne(Entity entity) {
        prepareUnarmedAirborne(entity);
        VehicleEntity vehicle = (VehicleEntity) entity;
        for (String weapon : List.copyOf(vehicle.getGunDataMap().keySet())) {
            vehicle.modifyGunData(weapon, data -> {
                data.ammo.set(Math.max(1, data.get(GunProp.MAGAZINE)));
                data.virtualAmmo.set(512);
            });
        }
        setHoverFixture(entity);
    }

    public static void setHoverFixture(Entity entity) {
        VehicleEntity vehicle = (VehicleEntity) entity;
        vehicle.setHoverMode(true);
        vehicle.setPower(0.09F);
    }

    public static void setDecoyStock(Entity entity, int count) {
        ((VehicleEntity) entity).setDecoyCount(count);
    }

    public static int decoyStock(Entity entity) {
        return ((VehicleEntity) entity).getDecoyCount();
    }

    public static Entity trackedMissile(ServerLevel level, Entity owner, Entity target, Vec3 position) {
        IglaMissileEntity missile = new IglaMissileEntity(owner, level, 0.0F, 0.0F, 0.0F);
        missile.setPos(position);
        missile.setTargetUUID(target.getStringUUID());
        level.addFreshEntity(missile);
        return missile;
    }

    public static void retargetMissile(Entity missile, Entity target) {
        ((MissileProjectile) missile).setTargetUUID(target.getStringUUID());
    }

    public static void loseMissileTarget(Entity missile) {
        ((MissileProjectile) missile).setLostTarget(true);
    }

    public static void increaseCombatFixtureDurability(Entity entity) {
        VehicleEntity vehicle = (VehicleEntity) entity;
        JsonObject properties = new JsonObject();
        JsonObject parts = new JsonObject();
        properties.addProperty("MaxHealth", 1_000_000.0F);
        for (String part : List.of("Turret", "LeftWheel", "RightWheel", "MainEngine", "SubEngine")) {
            parts.addProperty(part, 1_000_000.0F);
        }
        properties.add("PartHealth", parts);
        vehicle.setOverride(properties.toString());
        vehicle.setHealth(vehicle.getMaxHealth());
        vehicle.setTurretHealth(vehicle.getTurretMaxHealth());
        vehicle.setLeftWheelHealth(vehicle.getLeftWheelMaxHealth());
        vehicle.setRightWheelHealth(vehicle.getRightWheelMaxHealth());
        vehicle.setMainEngineHealth(vehicle.getMainEngineMaxHealth());
        vehicle.setSubEngineHealth(vehicle.getSubEngineMaxHealth());
    }

    public static boolean movePassengerToSeat(Entity entity, Entity passenger, int seat) {
        if (!(entity instanceof VehicleEntity vehicle)) {
            throw new IllegalArgumentException("Not an SBW vehicle: " + entity.getType());
        }
        return vehicle.changeSeat(passenger, seat);
    }

    public record FlightControl(float mouseX, float mouseY, float deltaRot, float rotor,
                                boolean forward, boolean back, boolean left, boolean right,
                                boolean up, boolean down, boolean sprint) {
    }
}
