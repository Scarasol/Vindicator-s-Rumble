package com.scarasol.vindicatorsrumble.mixin;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleDimensions;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground.GroundPathCollision;
import com.scarasol.vindicatorsrumble.util.VehiclePathContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WalkNodeEvaluator.class)
public abstract class VehicleWalkNodeEvaluatorMixin extends NodeEvaluator {
    @Unique
    private VehicleDimensions vindicatorsRumble$dimensions;

    @Shadow
    protected abstract Node getStartNode(BlockPos pos);

    @Inject(method = "prepare", at = @At("TAIL"))
    private void vindicatorsRumble$captureDimensions(PathNavigationRegion level, Mob mob, CallbackInfo ci) {
        this.vindicatorsRumble$dimensions = VehiclePathContext.isNarrowFor(mob)
                ? VehiclePathContext.dimensionsFor(mob).orElse(null) : null;
    }

    @Inject(method = "done", at = @At("HEAD"))
    private void vindicatorsRumble$clearDimensions(CallbackInfo ci) {
        this.vindicatorsRumble$dimensions = null;
    }

    @Inject(method = "getStart", at = @At("HEAD"), cancellable = true)
    private void vindicatorsRumble$startAtVehicle(CallbackInfoReturnable<Node> cir) {
        if (this.vindicatorsRumble$dimensions == null || !this.mob.isPassenger()) return;
        var vehicle = this.mob.getRootVehicle();
        // Seats are offset from the vehicle origin and can lie outside a narrow road's center line.
        cir.setReturnValue(this.getStartNode(new BlockPos(Mth.floor(vehicle.getX()),
                Mth.floor(vehicle.getY() + 0.5D), Mth.floor(vehicle.getZ()))));
    }

    @ModifyVariable(method = "getBlockPathTypes", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int vindicatorsRumble$centerVehicleWidth(int x) {
        return this.vindicatorsRumble$dimensions != null ? x - (this.entityWidth - 1) / 2 : x;
    }

    @ModifyVariable(method = "getBlockPathTypes", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int vindicatorsRumble$centerVehicleDepth(int z) {
        return this.vindicatorsRumble$dimensions != null ? z - (this.entityDepth - 1) / 2 : z;
    }

    @Inject(method = "getNeighbors", at = @At("RETURN"), cancellable = true)
    private void vindicatorsRumble$checkVehicleSegments(Node[] neighbors, Node current, CallbackInfoReturnable<Integer> cir) {
        if (this.vindicatorsRumble$dimensions == null) return;
        int accepted = 0;
        Vec3 from = new Vec3(current.x + 0.5D, current.y, current.z + 0.5D);
        for (int i = 0; i < cir.getReturnValueI(); i++) {
            Node next = neighbors[i];
            if (GroundPathCollision.isClear(this.level, this.mob, from,
                    new Vec3(next.x + 0.5D, next.y, next.z + 0.5D), this.vindicatorsRumble$dimensions)) {
                neighbors[accepted++] = next;
            }
        }
        cir.setReturnValue(accepted);
    }
}
