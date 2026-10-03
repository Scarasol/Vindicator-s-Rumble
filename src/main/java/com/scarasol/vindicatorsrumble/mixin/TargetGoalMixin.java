package com.scarasol.vindicatorsrumble.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleMovementType;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.init.ModTags;
import com.scarasol.vindicatorsrumble.util.VehicleAiTuning;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TargetGoal.class)
public abstract class TargetGoalMixin {
    @Unique
    private static final int HELICOPTER_UNSEEN_MEMORY_TICKS = 200;

    @Shadow
    @Final
    protected Mob mob;

    @WrapOperation(
            method = "canContinueToUse",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/ai/goal/target/TargetGoal;unseenMemoryTicks:I"
            )
    )
    private int vindicatorsRumble$extendHelicopterUnseenMemory(TargetGoal goal, Operation<Integer> original) {
        int unseenMemoryTicks = original.call(goal);
        if (!this.mob.isPassenger() || !this.mob.getType().is(ModTags.EntityTypes.VEHICLE_AI_USERS)) {
            return unseenMemoryTicks;
        }
        return VehicleControllers.forMob(this.mob)
                .filter(controller -> controller.movementType() == VehicleMovementType.HELICOPTER)
                .map(controller -> Math.max(unseenMemoryTicks, HELICOPTER_UNSEEN_MEMORY_TICKS))
                .orElse(unseenMemoryTicks);
    }

    @WrapOperation(
            method = "canContinueToUse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Mob;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
            )
    )
    private double vindicatorsRumble$useHorizontalFlightTargetRange(Mob mob, Entity target,
                                                                     Operation<Double> original) {
        if (!VehicleAiTuning.usesHorizontalTargetRange(mob)) {
            return original.call(mob, target);
        }
        return VehicleAiTuning.horizontalDistanceToSqr(mob, target.getX(), target.getZ());
    }
}
