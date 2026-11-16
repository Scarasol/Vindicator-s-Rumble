package com.scarasol.vindicatorsrumble.gametest.accuracy.mixin;

import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.scarasol.vindicatorsrumble.gametest.accuracy.ShotCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GunItem.class)
public abstract class VehicleAccuracyCaptureMixin {
    @Inject(method = "shoot(Lcom/atsuishio/superbwarfare/data/gun/ShootParameters;)V",
            at = @At("HEAD"), remap = false)
    private void captureShot(ShootParameters parameters, CallbackInfo ci) {
        ShotCapture.capture(parameters);
    }
}
