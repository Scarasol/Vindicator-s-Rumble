package com.scarasol.vindicatorsrumble.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.scarasol.vindicatorsrumble.util.VehicleAiTuning;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import javax.annotation.Nullable;

@Mixin(TargetingConditions.class)
public abstract class TargetingConditionsMixin {
    @WrapOperation(
            method = "test",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;distanceToSqr(DDD)D"
            )
    )
    private double vindicatorsRumble$useHorizontalFlightTargetRange(LivingEntity source,
                                                                     double targetX,
                                                                     double targetY,
                                                                     double targetZ,
                                                                     Operation<Double> original,
                                                                     @Nullable LivingEntity attacker,
                                                                     LivingEntity target) {
        if (!(attacker instanceof Mob mob) || !VehicleAiTuning.usesHorizontalTargetRange(mob)) {
            return original.call(source, targetX, targetY, targetZ);
        }
        return VehicleAiTuning.horizontalDistanceToSqr(source, targetX, targetZ);
    }
}
