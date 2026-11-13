package com.scarasol.vindicatorsrumble.gametest.profile.mixin;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.gametest.profile.GroundDriveProfile;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground.GroundDriveStrategy", remap = false)
public abstract class GroundDriveTimingMixin {
    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void beginTick(VehicleController controller, CallbackInfo callback) {
        GroundDriveProfile.begin("strategy");
    }

    @Inject(method = "tick", at = @At("RETURN"), remap = false)
    private void endTick(VehicleController controller, CallbackInfo callback) {
        GroundDriveProfile.end();
    }

    @Inject(method = "shouldUseDirectRam", at = @At("HEAD"), remap = false)
    private void countDirectRam(Entity target, CallbackInfoReturnable<Boolean> callback) {
        GroundDriveProfile.directRamCheck();
    }
}
