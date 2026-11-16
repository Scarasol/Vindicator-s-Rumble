package com.scarasol.vindicatorsrumble.mixin.sbw;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class VehiclePassengerRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void vindicatorsRumble$hideSbwPassenger(LivingEntity entity, float entityYaw, float partialTicks,
                                                   PoseStack poseStack, MultiBufferSource source, int light,
                                                   CallbackInfo ci) {
        if (entity.getVehicle() instanceof VehicleEntity vehicle && vehicle.hidePassenger(entity)) {
            ci.cancel();
        }
    }
}
