package com.scarasol.vindicatorsrumble.mixin.sbw;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.InventoryTool;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwCompat;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryTool.class)
public abstract class SbwInventoryToolMixin {
    @Inject(method = "hasCreativeAmmoBoxForVehicle", remap = false, cancellable = true, at = @At("HEAD"))
    private static void vindicatorsRumble$aiVehiclesHaveCreativeAmmo(VehicleEntity vehicle, CallbackInfoReturnable<Boolean> cir) {
        if (!CommonConfig.VEHICLE_AI_NEEDS_AMMO.get() && SbwCompat.hasTaggedAiPassenger(vehicle)) {
            cir.setReturnValue(true);
        }
    }
}
