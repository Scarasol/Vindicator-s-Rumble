package com.scarasol.vindicatorsrumble.gametest.profile.mixin;

import com.scarasol.vindicatorsrumble.gametest.profile.HelicopterSearchProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.helicopter.HelicopterVisibilityRecovery",
        remap = false)
public abstract class HelicopterRecoveryProfileMixin {
    @Inject(method = "search", at = @At("HEAD"), remap = false)
    private void beginSearch(CallbackInfoReturnable<?> callback) {
        HelicopterSearchProfile.beginSearch();
    }

    @Inject(method = "search", at = @At("RETURN"), remap = false)
    private void endSearch(CallbackInfoReturnable<?> callback) {
        HelicopterSearchProfile.endSearch();
    }

    @Inject(method = "advance", at = @At("HEAD"), remap = false)
    private void countCandidate(CallbackInfoReturnable<?> callback) {
        HelicopterSearchProfile.candidate();
    }

    @Inject(method = "lineClear", at = @At("HEAD"), remap = false)
    private static void beginRay(CallbackInfoReturnable<Boolean> callback) {
        HelicopterSearchProfile.beginRay();
    }

    @Inject(method = "lineClear", at = @At("RETURN"), remap = false)
    private static void endRay(CallbackInfoReturnable<Boolean> callback) {
        HelicopterSearchProfile.endRay();
    }
}
