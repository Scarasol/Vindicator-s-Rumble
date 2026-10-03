package com.scarasol.vindicatorsrumble.mixin.recruits;

import com.scarasol.vindicatorsrumble.compat.recruits.RecruitsCompat;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.entities.ai.RecruitFollowOwnerGoal;
import com.talhanation.recruits.entities.ai.RecruitHoldPosGoal;
import com.talhanation.recruits.entities.ai.RecruitMoveToPosGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {RecruitFollowOwnerGoal.class, RecruitHoldPosGoal.class, RecruitMoveToPosGoal.class}, remap = false)
public abstract class RecruitMovementGoalMixin extends Goal {
    @Shadow(remap = false)
    private AbstractRecruitEntity recruit;

    @Inject(method = {"canUse", "canContinueToUse"}, at = @At("HEAD"), cancellable = true, remap = true)
    private void vindicatorsRumble$delegateMountedMovement(CallbackInfoReturnable<Boolean> cir) {
        if (RecruitsCompat.usesVehicleNavigation(this.recruit)) {
            cir.setReturnValue(false);
        }
    }
}
