package com.scarasol.vindicatorsrumble.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.scarasol.vindicatorsrumble.util.VehicleAiTuning;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Mob.class)
public abstract class MobNavigationMixin {
    @WrapOperation(
            method = "serverAiStep",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;tick()V")
    )
    private void vindicatorsRumble$skipDelegatedNavigationTick(PathNavigation navigation, Operation<Void> original) {
        // Gate the call, including custom navigation implementations that override vanilla tick.
        if (!VehicleAiTuning.isDelegatingGroundNavigation((Mob) (Object) this)) {
            original.call(navigation);
        }
    }
}
