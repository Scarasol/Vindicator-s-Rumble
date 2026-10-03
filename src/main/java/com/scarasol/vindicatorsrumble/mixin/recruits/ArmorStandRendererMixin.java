package com.scarasol.vindicatorsrumble.mixin.recruits;

import com.scarasol.vindicatorsrumble.compat.recruits.RecruitsCompat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmorStandRenderer.class)
public abstract class ArmorStandRendererMixin {
    @Inject(method = "getRenderType(Lnet/minecraft/world/entity/decoration/ArmorStand;ZZZ)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), cancellable = true)
    private void vindicatorsRumble$hideStrategicTarget(ArmorStand stand, boolean visible, boolean translucent,
                                                      boolean glowing, CallbackInfoReturnable<RenderType> ci) {
        if (RecruitsCompat.isStrategicTarget(stand)) {
            ci.setReturnValue(null);
        }
    }
}
