package com.scarasol.vindicatorsrumble.mixin.sbw.pillagersgun;

import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.scarasol.vindicatorsrumble.compat.sbw.PillagersGunAccuracy;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwVehicleAccuracy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SbwVehicleAccuracy.class)
public abstract class VehicleInaccuracyMixin {
    @ModifyReturnValue(
            method = "spread",
            at = @At("RETURN"),
            remap = false
    )
    private static double vindicatorsRumble$applyDynamicAccuracy(double spread, ShootParameters parameters, Mob shooter) {
        Entity target = parameters.targetEntityUUID == null ? shooter.getTarget()
                : parameters.level.getEntity(parameters.targetEntityUUID);
        return target == null ? spread : PillagersGunAccuracy.modifySpread(spread, shooter, target);
    }
}
