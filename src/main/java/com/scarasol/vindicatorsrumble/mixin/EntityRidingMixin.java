package com.scarasol.vindicatorsrumble.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.scarasol.vindicatorsrumble.event.VehicleAiEventHandler;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class EntityRidingMixin {
    @WrapOperation(
            method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;addPassenger(Lnet/minecraft/world/entity/Entity;)V")
    )
    private void vindicatorsRumble$afterBoarding(Entity vehicle, Entity passenger, Operation<Void> original) {
        original.call(vehicle, passenger);
        VehicleAiEventHandler.onVehicleMounted(passenger, vehicle);
    }

    @WrapOperation(
            method = "removeVehicle",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;removePassenger(Lnet/minecraft/world/entity/Entity;)V")
    )
    private void vindicatorsRumble$afterDetaching(Entity vehicle, Entity passenger, Operation<Void> original) {
        // Cancellation has finished and Entity.vehicle is already null; seat lookup still works here.
        VehicleAiEventHandler.onVehicleDismounted(passenger, vehicle);
        original.call(vehicle, passenger);
    }
}
