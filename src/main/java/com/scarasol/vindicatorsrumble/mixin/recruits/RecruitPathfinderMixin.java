package com.scarasol.vindicatorsrumble.mixin.recruits;

import com.scarasol.vindicatorsrumble.util.VehiclePathContext;
import com.talhanation.recruits.pathfinding.AsyncPathfinder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(AsyncPathfinder.class)
public abstract class RecruitPathfinderMixin extends PathFinder {
    protected RecruitPathfinderMixin(NodeEvaluator evaluator, int maxVisitedNodes) {
        super(evaluator, maxVisitedNodes);
    }

    @Inject(method = "findPath", at = @At("HEAD"), cancellable = true)
    private void vindicatorsRumble$completeVehiclePath(PathNavigationRegion region, Mob mob, Set<BlockPos> targets,
                                                     float range, int accuracy, float multiplier,
                                                     CallbackInfoReturnable<Path> cir) {
        if (VehiclePathContext.dimensionsFor(mob).isPresent()) {
            cir.setReturnValue(super.findPath(region, mob, targets, range, accuracy, multiplier));
        }
    }
}
