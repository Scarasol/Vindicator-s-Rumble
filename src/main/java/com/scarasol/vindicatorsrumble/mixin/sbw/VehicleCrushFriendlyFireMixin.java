package com.scarasol.vindicatorsrumble.mixin.sbw;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleMotionUtils;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.scarasol.vindicatorsrumble.util.AllegianceUtil;
import com.scarasol.vindicatorsrumble.util.VehicleAiMemory;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(VehicleMotionUtils.class)
public abstract class VehicleCrushFriendlyFireMixin {
    @WrapOperation(
            method = "crushEntities(Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;)V",
            at = {
                    @At(value = "INVOKE",
                            target = "Lcom/atsuishio/superbwarfare/tools/DamageHandlerKt;forceHurt(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                            remap = false),
                    @At(value = "INVOKE",
                            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                            remap = true)
            },
            remap = false
    )
    private static boolean vindicatorsRumble$filterEntityCrushDamage(Entity target, DamageSource source,
                                                                    float amount, Operation<Boolean> original,
                                                                    VehicleEntity vehicle) {
        if (vehicle.getFirstPassenger() instanceof Mob driver && VehicleAiMemory.isVehicleAiEnabled(driver)
                && AllegianceUtil.shouldIgnoreFriendlyDamage(target, driver)) {
            return false;
        }
        return original.call(target, source, amount);
    }

    @WrapOperation(
            method = "crushEntities(Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                    remap = true),
            remap = false
    )
    private static boolean vindicatorsRumble$filterReciprocalCrushDamage(VehicleEntity target, DamageSource source,
                                                                        float amount, Operation<Boolean> original,
                                                                        VehicleEntity vehicle) {
        if (vehicle.getFirstPassenger() instanceof Mob driver && VehicleAiMemory.isVehicleAiEnabled(driver)
                && AllegianceUtil.shouldIgnoreFriendlyDamage(source.getDirectEntity(), driver)) {
            return false;
        }
        return original.call(target, source, amount);
    }
}
