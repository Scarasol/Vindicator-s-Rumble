package com.scarasol.vindicatorsrumble.mixin;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleDimensions;
import com.scarasol.vindicatorsrumble.util.VehiclePathContext;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NodeEvaluator.class)
public abstract class VehicleNodeEvaluatorMixin {
    @Shadow
    protected int entityWidth;

    @Shadow
    protected int entityHeight;

    @Shadow
    protected int entityDepth;

    @Inject(method = "prepare", at = @At("TAIL"))
    private void vindicatorsRumble$useVehiclePathDimensions(PathNavigationRegion region, Mob mob, CallbackInfo ci) {
        VehiclePathContext.dimensionsFor(mob).ifPresent(dimensions ->
                this.applyVehicleDimensions(dimensions, VehiclePathContext.isNarrowFor(mob)));
    }

    private void applyVehicleDimensions(VehicleDimensions dimensions, boolean narrow) {
        // Narrow-road candidates reserve width; their complete body segments are checked separately.
        int horizontal = Math.max(1, Mth.ceil(narrow ? dimensions.width() : dimensions.conservativeHorizontalSize()));
        this.entityWidth = horizontal;
        this.entityDepth = horizontal;
        this.entityHeight = Math.max(1, Mth.ceil(dimensions.height()));
    }
}
