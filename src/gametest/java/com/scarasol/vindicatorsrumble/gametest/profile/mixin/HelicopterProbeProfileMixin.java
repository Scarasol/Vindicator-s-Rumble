package com.scarasol.vindicatorsrumble.gametest.profile.mixin;

import com.scarasol.vindicatorsrumble.gametest.profile.HelicopterSearchProfile;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.helicopter.HelicopterEnvironmentProbe",
        remap = false)
public abstract class HelicopterProbeProfileMixin {
    @Inject(method = "sample", at = @At("HEAD"), remap = false)
    private void beginSample(CallbackInfoReturnable<?> callback) {
        HelicopterSearchProfile.beginProbeSample();
    }

    @Inject(method = "sample", at = @At("RETURN"), remap = false)
    private void endSample(CallbackInfoReturnable<?> callback) {
        HelicopterSearchProfile.endProbeSample();
    }

    @Inject(method = "rayDistance", at = @At("HEAD"), remap = false)
    private void countRay(Level level, Entity vehicle, Vec3 start, Vec3 direction, boolean front,
                          CallbackInfoReturnable<Double> callback) {
        HelicopterSearchProfile.probeRay(start, direction, front);
    }
}
