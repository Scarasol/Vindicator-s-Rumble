package com.scarasol.vindicatorsrumble.mixin.sbw;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleWeaponUtils;
import com.atsuishio.superbwarfare.init.ModDamageTypes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.scarasol.vindicatorsrumble.util.VehicleSmokeDefense;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VehicleEntity.class)
public abstract class VehicleSmokeMixin {
    @Inject(method = "processInput(S)V", at = @At("HEAD"), remap = false)
    private void vindicatorsRumble$replaceOldDriverInput(short keys, CallbackInfo ci) {
        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        VehicleSmokeDefense.inputChanged(vehicle, vehicle.getFirstPassenger());
    }

    @WrapOperation(
            method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At(value = "INVOKE",
                    target = "Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;onHurt(FLnet/minecraft/world/entity/Entity;Z)V",
                    remap = false)
    )
    private void vindicatorsRumble$reportActualEnemyHit(VehicleEntity vehicle, float damage, Entity attacker,
                                                      boolean send, Operation<Void> original,
                                                      DamageSource source, float amount) {
        float before = vehicle.getHealth();
        original.call(vehicle, damage, attacker, send);
        if (send && !source.is(ModDamageTypes.VEHICLE_STRIKE) && !source.is(ModDamageTypes.AIR_CRASH)
                && !source.is(DamageTypeTags.IS_FALL)) {
            VehicleSmokeDefense.reportHit(vehicle, attacker, before - vehicle.getHealth(),
                    vehicle.getHealth(), vehicle.getMaxHealth());
        }
    }

    @WrapOperation(
            method = "releaseSmokeDecoy(Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/atsuishio/superbwarfare/entity/vehicle/utils/VehicleWeaponUtils;releaseSmokeDecoy(Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;Lnet/minecraft/world/phys/Vec3;)V",
                    remap = false),
            remap = false
    )
    private void vindicatorsRumble$confirmSmokeDeployment(VehicleEntity vehicle, Vec3 direction,
                                                        Operation<Void> original) {
        if (!VehicleSmokeDefense.allowDeployment(vehicle)) {
            vehicle.setDecoyInputDown(false);
            return;
        }
        int before = vehicle.getDecoyCount();
        original.call(vehicle, direction);
        if (vehicle.getDecoyCount() < before) {
            VehicleSmokeDefense.deployed(vehicle);
        }
    }
}
