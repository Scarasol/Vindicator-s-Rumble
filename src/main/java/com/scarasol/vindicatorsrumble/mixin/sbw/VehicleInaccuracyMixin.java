package com.scarasol.vindicatorsrumble.mixin.sbw;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwVehicleAccuracy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GunData.class)
public abstract class VehicleInaccuracyMixin {
    @ModifyVariable(
            method = "shoot(Lcom/atsuishio/superbwarfare/data/gun/ShootParameters;)V",
            at = @At("HEAD"),
            argsOnly = true,
            remap = false
    )
    private ShootParameters vindicatorsRumble$applyVehicleAccuracy(ShootParameters parameters) {
        return SbwVehicleAccuracy.apply(parameters);
    }
}
