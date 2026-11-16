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

    @Inject(method = "shouldUseRamming(Lcom/scarasol/vindicatorsrumble/api/vehicle/VehicleController;)Z", at = @At("HEAD"), remap = false)
    private void beginRamEligibility(CallbackInfoReturnable<Boolean> callback) {
        GroundDriveProfile.begin("ramEligibility");
    }

    @Inject(method = "shouldUseRamming(Lcom/scarasol/vindicatorsrumble/api/vehicle/VehicleController;)Z", at = @At("RETURN"), remap = false)
    private void endRamEligibility(CallbackInfoReturnable<Boolean> callback) {
        GroundDriveProfile.end();
    }

    @Inject(method = "shouldUseRamming(Lcom/scarasol/vindicatorsrumble/api/vehicle/VehicleController;)Z", at = @At(value = "INVOKE",
            target = "Lcom/scarasol/vindicatorsrumble/api/vehicle/VehicleController;seats()Ljava/util/List;"),
            remap = false)
    private void countRamSeatQuery(CallbackInfoReturnable<Boolean> callback) {
        GroundDriveProfile.ramSeatQuery();
    }

    @Inject(method = "shouldUseRamming(Lcom/scarasol/vindicatorsrumble/api/vehicle/VehicleController;)Z", at = @At(value = "INVOKE",
            target = "Lcom/scarasol/vindicatorsrumble/api/vehicle/VehicleController;hasAnyWeaponAmmo(Lnet/minecraft/world/entity/Mob;)Z"),
            remap = false)
    private void countRamAmmoQuery(CallbackInfoReturnable<Boolean> callback) {
        GroundDriveProfile.ramAmmoQuery();
    }
}
