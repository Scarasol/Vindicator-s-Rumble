package com.scarasol.vindicatorsrumble.gametest.profile.mixin;

import com.scarasol.vindicatorsrumble.gametest.profile.HelicopterSearchProfile;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.helicopter.HelicopterHeightField",
        remap = false)
public abstract class HelicopterHeightProfileMixin {
    @Inject(method = {"corridorClear", "maxTerrainHeight", "mayHaveLineOfSight"},
            at = @At("HEAD"), remap = false)
    private void beginGeometry(CallbackInfoReturnable<?> callback) {
        HelicopterSearchProfile.beginGeometry();
    }

    @Inject(method = {"corridorClear", "maxTerrainHeight", "mayHaveLineOfSight"},
            at = @At("RETURN"), remap = false)
    private void endGeometry(CallbackInfoReturnable<?> callback) {
        HelicopterSearchProfile.endGeometry();
    }

    @Inject(method = "corridorMetrics", at = @At("HEAD"), remap = false)
    private void beginMetrics(CallbackInfo callback) {
        HelicopterSearchProfile.fullCorridorScan();
        HelicopterSearchProfile.beginGeometry();
    }

    @Inject(method = "corridorMetrics", at = @At("RETURN"), remap = false)
    private void endMetrics(CallbackInfo callback) {
        HelicopterSearchProfile.endGeometry();
    }

    @Inject(method = "height", at = @At("HEAD"), remap = false)
    private void countHeight(CallbackInfoReturnable<Integer> callback) {
        HelicopterSearchProfile.height();
    }

    @WrapOperation(method = "height", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"),
            remap = false)
    private int countWorldHeight(Level level, Heightmap.Types type, int x, int z, Operation<Integer> original) {
        HelicopterSearchProfile.worldHeight();
        return original.call(level, type, x, z);
    }
}
