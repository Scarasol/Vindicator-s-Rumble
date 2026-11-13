package com.scarasol.vindicatorsrumble.gametest.profile.mixin;

import com.scarasol.vindicatorsrumble.gametest.profile.HelicopterSearchProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.helicopter.HelicopterHeightField",
        remap = false)
public abstract class HelicopterBeamWorkProfileMixin {
    @Inject(method = "corridorMetrics", at = @At("HEAD"), remap = false)
    private void countFullCorridor(CallbackInfo callback) {
        HelicopterSearchProfile.fullCorridorScan();
    }
}
