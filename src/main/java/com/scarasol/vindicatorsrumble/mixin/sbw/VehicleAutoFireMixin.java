package com.scarasol.vindicatorsrumble.mixin.sbw;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwVehicleController;
import com.scarasol.vindicatorsrumble.init.ModTags;
import com.scarasol.vindicatorsrumble.util.VehicleAiMemory;
import com.scarasol.vindicatorsrumble.util.VehicleAiTuning;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(VehicleEntity.class)
public abstract class VehicleAutoFireMixin {
    private static final String EMPTY_AI_TARGET = "undefined";

    @WrapOperation(
            method = "baseTick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;vehicleShoot(Lnet/minecraft/world/entity/LivingEntity;Ljava/util/UUID;Lnet/minecraft/world/phys/Vec3;)V",
                    remap = false
            )
    )
    private void vindicatorsRumble$blockInvalidAiPassengerAutoFire(VehicleEntity vehicle, LivingEntity shooter, UUID targetUuid, Vec3 targetPos, Operation<Void> original) {
        if (!(shooter instanceof Mob mob) || !mob.getType().is(ModTags.EntityTypes.VEHICLE_AI_USERS)) {
            original.call(vehicle, shooter, targetUuid, targetPos);
            return;
        }

        LivingEntity target = mob.getTarget();
        if (VehicleAiTuning.isValidVehicleCombatTarget(mob, target)) {
            return;
        }

        int seatIndex = vehicle.getSeatIndex(mob);
        if (seatIndex == vehicle.getTurretControllerIndex()) {
            vehicle.setAiTurretTargetUUID(EMPTY_AI_TARGET);
        }
        if (seatIndex == vehicle.getPassengerWeaponStationControllerIndex()) {
            vehicle.setAiPassengerWeaponTargetUUID(EMPTY_AI_TARGET);
        }
        VehicleAiMemory.clear(mob);
        mob.setTarget(null);
    }

    @Inject(
            method = "vehicleShoot(Lnet/minecraft/world/entity/LivingEntity;Ljava/util/UUID;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void vindicatorsRumble$guardUuidVehicleShoot(LivingEntity shooter, UUID targetUuid, Vec3 targetPos, CallbackInfo ci) {
        if (this.vindicatorsRumble$shouldBlockAiShot((VehicleEntity) (Object) this, shooter)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "vehicleShoot(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/String;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void vindicatorsRumble$guardNamedVehicleShoot(LivingEntity shooter, String weaponName, Vec3 targetPos, CallbackInfo ci) {
        if (this.vindicatorsRumble$shouldBlockAiShot((VehicleEntity) (Object) this, shooter)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "vehicleShoot(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/String;Ljava/util/UUID;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void vindicatorsRumble$guardNamedUuidVehicleShoot(LivingEntity shooter, String weaponName, UUID targetUuid,
                                                            Vec3 targetPos, CallbackInfo ci) {
        if (this.vindicatorsRumble$shouldBlockAiShot((VehicleEntity) (Object) this, shooter)) {
            ci.cancel();
        }
    }

    private boolean vindicatorsRumble$shouldBlockAiShot(VehicleEntity vehicle, LivingEntity shooter) {
        if (!(shooter instanceof Mob mob) || !mob.getType().is(ModTags.EntityTypes.VEHICLE_AI_USERS)) {
            return false;
        }

        LivingEntity target = mob.getTarget();
        boolean valid = VehicleAiTuning.isValidVehicleCombatTarget(mob, target);
        int seatIndex = vehicle.getSeatIndex(mob);

        if (valid) {
            SbwVehicleController controller = new SbwVehicleController(vehicle);
            Vec3 aimPoint = controller.aimPoint(mob, target);
            if (controller.hasLineOfSight(mob, target, aimPoint)
                    && controller.isCurrentWeaponAimedAt(mob, target)) {
                return false;
            }
        }

        if (!valid) {
            this.vindicatorsRumble$clearSeatTargets(vehicle, mob, seatIndex);
            VehicleAiMemory.clear(mob);
            mob.setTarget(null);
        }
        return true;
    }

    private void vindicatorsRumble$clearSeatTargets(VehicleEntity vehicle, Mob mob, int seatIndex) {
        if (seatIndex == vehicle.getTurretControllerIndex()) {
            vehicle.setAiTurretTargetUUID(EMPTY_AI_TARGET);
        }
        if (seatIndex == vehicle.getPassengerWeaponStationControllerIndex()) {
            vehicle.setAiPassengerWeaponTargetUUID(EMPTY_AI_TARGET);
        }
    }
}
