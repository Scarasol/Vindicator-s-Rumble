package com.scarasol.vindicatorsrumble.gametest.profile.mixin;

import com.scarasol.vindicatorsrumble.gametest.profile.GroundDriveProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground.GroundDriveControl", remap = false)
public abstract class GroundControlTimingMixin {
    @Inject(method = "execute", at = @At("HEAD"), remap = false)
    private void beginExecution(CallbackInfoReturnable<?> callback) {
        GroundDriveProfile.begin("control");
    }

    @Inject(method = "execute", at = @At("RETURN"), remap = false)
    private void endExecution(CallbackInfoReturnable<?> callback) {
        GroundDriveProfile.end();
    }

    @Inject(method = "shouldUseDirectRam", at = @At("HEAD"), remap = false)
    private void countDirectRam(CallbackInfoReturnable<Boolean> callback) {
        GroundDriveProfile.directRamCheck();
    }
}
