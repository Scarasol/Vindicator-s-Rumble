package com.scarasol.vindicatorsrumble.compat.sbw;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.entity.projectile.IglaMissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import com.atsuishio.superbwarfare.entity.projectile.WireGuideMissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.SmokeDecoyEntity;
import com.atsuishio.superbwarfare.init.ModEntities;
import com.atsuishio.superbwarfare.init.ModDamageTypes;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.tools.InventoryTool;
import com.atsuishio.superbwarfare.tools.OBB;
import net.minecraft.world.entity.LivingEntity;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

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

    public static boolean hasCreativeVehicleAmmo(Entity entity) {
        return InventoryTool.hasCreativeAmmoBoxForVehicle((VehicleEntity) entity);
    }

    public static AABB physicalCollisionBounds(Entity entity) {
        var collision = ((VehicleEntity) entity).getCollisionOBB();
        return collision == null ? entity.getBoundingBox() : OBB.getWorldAABB(collision);
    }

    public static boolean hasDecoyInput(Entity entity) {
        return ((VehicleEntity) entity).decoyInputDown();
    }

    public static void prepareSmokeFixture(Entity entity, int loaded, int spare) {
        VehicleEntity vehicle = (VehicleEntity) entity;
        for (int slot = 0; slot < vehicle.getContainerSize(); slot++) {
            vehicle.setItem(slot, ItemStack.EMPTY);
        }
        JsonObject properties = new JsonObject();
        properties.addProperty("MaxHealth", 1000.0F);
        properties.addProperty("HasDecoy", true);
        properties.addProperty("SmokeDecoy", true);
        properties.addProperty("DecoyReloadTime", 60);
        properties.addProperty("ApplyDefaultDamageModifiers", false);
        properties.addProperty("RepairAmount", 0);
        properties.add("DamageModifiers", new com.google.gson.JsonArray());
        vehicle.setOverride(properties.toString());
        vehicle.setHealth(vehicle.getMaxHealth());
        vehicle.setDecoyCount(loaded);
        vehicle.setDecoyReloadCoolDown(0);
        vehicle.setEnergy(vehicle.getMaxEnergy());
        if (spare > 0) {
            vehicle.setItem(0, new ItemStack(ModItems.VEHICLE_SMOKE_AMMO.get(), spare));
        }
        for (String weapon : List.copyOf(vehicle.getGunDataMap().keySet())) {
            vehicle.modifyGunData(weapon, data -> {
                data.ammo.set(0);
                data.virtualAmmo.set(0);
                data.backupAmmoCount.set(0);
            });
        }
    }

    public static boolean isSmoke(Entity entity) {
        return entity instanceof SmokeDecoyEntity;
    }

    public static void loseMissileGuidance(Entity missile) {
        ((MissileProjectile) missile).setLost(true);
    }

    public static boolean missileTargets(Entity missile, Entity target) {
        return ((MissileProjectile) missile).getTargetUUID().equals(target.getStringUUID());
    }

    public static int spareSmoke(Entity entity) {
        VehicleEntity vehicle = (VehicleEntity) entity;
        int count = 0;
        for (int slot = 0; slot < vehicle.getContainerSize(); slot++) {
            ItemStack stack = vehicle.getItem(slot);
            if (stack.is(ModItems.VEHICLE_SMOKE_AMMO.get())) count += stack.getCount();
        }
        return count;
    }

    public static float vehicleHealth(Entity entity) {
        return ((VehicleEntity) entity).getHealth();
    }

    public static void setVehicleHealth(Entity entity, float health) {
        ((VehicleEntity) entity).setHealth(health);
    }

    public static void enemyHit(Entity entity, LivingEntity attacker, float amount) {
        entity.hurt(entity.level().damageSources().mobAttack(attacker), amount);
    }

    public static void collisionHit(Entity entity, LivingEntity attacker, float amount) {
        entity.hurt(ModDamageTypes.causeVehicleStrikeDamage(entity.level().registryAccess(), attacker, attacker), amount);
    }

    public static void softenMobDamage(Entity entity) {
        VehicleEntity vehicle = (VehicleEntity) entity;
        JsonObject properties = JsonParser.parseString(vehicle.getOverride()).getAsJsonObject();
        properties.add("DamageModifiers", JsonParser.parseString(
                "[{\"Type\":\"Multiply\",\"Source\":\"minecraft:mob_attack\",\"Value\":0.1}]"));
        vehicle.setOverride(properties.toString());
    }

    public static Entity wireGuidedMissile(ServerLevel level, Entity owner, Entity target, Vec3 position) {
        WireGuideMissileEntity missile = new WireGuideMissileEntity(ModEntities.WIRE_GUIDE_MISSILE.get(), level);
        missile.setOwner(owner);
        missile.setTargetUUID(target.getStringUUID());
        missile.setPos(position);
        level.addFreshEntity(missile);
        return missile;
    }

    public static Entity trackedMissile(ServerLevel level, Entity owner, Entity target, Vec3 position) {
        IglaMissileEntity missile = new IglaMissileEntity(owner, level, 0.0F, 0.0F, 0.0F);
        missile.setPos(position);
        missile.setTargetUUID(target.getStringUUID());
        level.addFreshEntity(missile);
        return missile;
    }

    public static Entity missileWithGuidance(ServerLevel level, Entity owner, Entity target, Vec3 position,
                                            ResourceLocation type, int guidance) {
        MissileProjectile missile = (MissileProjectile) ForgeRegistries.ENTITY_TYPES.getValue(type).create(level);
        missile.setOwner(owner);
        missile.setGuideType(guidance);
        missile.setTargetUUID(target.getStringUUID());
        missile.setTargetPos(target.position());
        missile.setPos(position);
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

    public static void prepareStationaryAirborneTarget(Entity entity) {
        increaseCombatFixtureDurability(entity);
        VehicleEntity vehicle = (VehicleEntity) entity;
        JsonObject properties = JsonParser.parseString(vehicle.getOverride()).getAsJsonObject();
        properties.addProperty("Gravity", 0.0D);
        vehicle.setOverride(properties.toString());
        vehicle.setEngineStart(false);
        vehicle.setEngineStartOver(false);
        vehicle.setPower(0.0F);
        vehicle.setDeltaMovement(Vec3.ZERO);
    }

    public static boolean movePassengerToSeat(Entity entity, Entity passenger, int seat) {
        if (!(entity instanceof VehicleEntity vehicle)) {
            throw new IllegalArgumentException("Not an SBW vehicle: " + entity.getType());
        }
        return vehicle.changeSeat(passenger, seat);
    }

    public static void prepareContinuousGroundFire(Entity entity, boolean slowCadence) {
        VehicleEntity vehicle = (VehicleEntity) entity;
        for (int slot = 0; slot < vehicle.getContainerSize(); slot++) {
            vehicle.setItem(slot, ItemStack.EMPTY);
        }
        for (String weapon : List.copyOf(vehicle.getGunDataMap().keySet())) {
            vehicle.modifyGunData(weapon, data -> {
                JsonObject properties = new JsonObject();
                properties.addProperty("Magazine", 4096);
                properties.addProperty("HeatPerShoot", 0);
                if (slowCadence) properties.addProperty("RPM", 30);
                data.propertyOverrideString.set(properties.toString());
                data.resetStatus();
                data.ammo.set(4096);
                data.virtualAmmo.set(4096);
                data.heat.set(0.0D);
                data.overHeat.set(false);
            });
        }
        vehicle.setEnergy(vehicle.getMaxEnergy());
    }

    public static void setGroundWeaponCondition(Entity entity, LivingEntity occupant, int weaponId,
                                                GroundWeaponCondition condition) {
        VehicleEntity vehicle = (VehicleEntity) entity;
        int seat = vehicle.getSeatIndex(occupant);
        vehicle.modifyGunData(seat, weaponId, data -> {
            String override = data.propertyOverrideString.get();
            JsonObject properties = override.isBlank() ? new JsonObject()
                    : JsonParser.parseString(override).getAsJsonObject();
            properties.addProperty("EmptyReloadTime", 400);
            properties.addProperty("NaturalCooldown", 0);
            data.propertyOverrideString.set(properties.toString());
            data.resetStatus();
            if (condition == GroundWeaponCondition.RELOADING) {
                data.ammo.set(0);
                data.startReload();
            } else if (condition == GroundWeaponCondition.OVERHEATED) {
                data.heat.set(100.0D);
                data.overHeat.set(true);
            } else {
                data.ammo.set(0);
                data.virtualAmmo.set(0);
                data.backupAmmoCount.set(0);
            }
        });
    }

    public enum GroundWeaponCondition {
        RELOADING,
        OVERHEATED,
        EMPTY
    }

    public record FlightControl(float mouseX, float mouseY, float deltaRot, float rotor,
                                boolean forward, boolean back, boolean left, boolean right,
                                boolean up, boolean down, boolean sprint) {
    }
}
