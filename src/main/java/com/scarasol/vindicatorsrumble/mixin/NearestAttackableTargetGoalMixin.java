package com.scarasol.vindicatorsrumble.mixin;

import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.util.VehicleAiMemory;
import com.scarasol.vindicatorsrumble.util.VehicleTargetRangeManager;
import com.scarasol.vindicatorsrumble.util.VehicleAiTuning;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NearestAttackableTargetGoal.class)
public abstract class NearestAttackableTargetGoalMixin<T extends LivingEntity> extends TargetGoal {
    @Shadow
    protected TargetingConditions targetConditions;

    @Shadow
    @Nullable
    protected LivingEntity target;

    public NearestAttackableTargetGoalMixin(Mob mob, boolean mustSee) {
        super(mob, mustSee);
    }

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void vindicatorsRumble$blockZeroFollowRangeTargeting(CallbackInfoReturnable<Boolean> cir) {
        if (this.vindicatorsRumble$shouldHandleVehicleTargeting() && !VehicleAiTuning.canAcquireTargets(this.mob)) {
            this.target = null;
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "findTarget", at = @At("HEAD"))
    private void vindicatorsRumble$refreshFollowRangeSnapshot(CallbackInfo ci) {
        if (VehicleAiMemory.isVehicleAiEnabled(this.mob)) {
            this.targetConditions = this.targetConditions.range(this.getFollowDistance());
        }
    }

    @Inject(method = "getTargetSearchArea", at = @At("RETURN"), cancellable = true)
    private void vindicatorsRumble$expandVehicleSearchHeight(double range, CallbackInfoReturnable<AABB> cir) {
        double searchHeight = VehicleTargetRangeManager.targetSearchHeight(this.mob, range, this.vindicatorsRumble$isFlightVehiclePassenger());
        if (searchHeight > 4.0D) {
            cir.setReturnValue(this.mob.getBoundingBox().inflate(range, searchHeight, range));
        }
    }

    private boolean vindicatorsRumble$shouldHandleVehicleTargeting() {
        return this.mob.isPassenger() && VehicleAiMemory.isVehicleAiEnabled(this.mob);
    }

    private boolean vindicatorsRumble$isFlightVehiclePassenger() {
        return this.vindicatorsRumble$shouldHandleVehicleTargeting()
                && VehicleControllers.forMob(this.mob).map(controller -> controller.supportsFlightControl()).orElse(false);
    }
}
