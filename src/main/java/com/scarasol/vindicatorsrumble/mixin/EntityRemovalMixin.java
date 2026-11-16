package com.scarasol.vindicatorsrumble.mixin;

import com.scarasol.vindicatorsrumble.event.VehicleAiEventHandler;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityRemovalMixin {
    @Inject(method = "setRemoved", at = @At("RETURN"))
    private void vindicatorsRumble$releaseRemovedVehicleState(Entity.RemovalReason reason, CallbackInfo ci) {
        // Tracking can end without removal, and a hidden entity can be removed without another tracking event.
        VehicleAiEventHandler.releaseEntityResources((Entity) (Object) this);
    }
}
