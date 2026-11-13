package com.scarasol.vindicatorsrumble.gametest.profile.mixin;

import com.scarasol.vindicatorsrumble.gametest.profile.GroundDriveProfile;
import com.scarasol.vindicatorsrumble.util.VehiclePathContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(PathNavigation.class)
public abstract class GroundNavigationTimingMixin {
    @Shadow @Final
    protected Mob mob;

    @Unique
    private boolean vindicatorsRumble$profilingPath;

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"))
    private void beginPath(Set<BlockPos> targets, int offset, boolean upward, int accuracy, float range,
                           CallbackInfoReturnable<Path> callback) {
        this.vindicatorsRumble$profilingPath = VehiclePathContext.dimensionsFor(this.mob).isPresent();
        if (!this.vindicatorsRumble$profilingPath) return;
        GroundDriveProfile.begin(VehiclePathContext.isNarrowFor(this.mob) ? "narrowPath" : "coarsePath");
    }

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("RETURN"))
    private void endPath(Set<BlockPos> targets, int offset, boolean upward, int accuracy, float range,
                         CallbackInfoReturnable<Path> callback) {
        if (this.vindicatorsRumble$profilingPath) {
            this.vindicatorsRumble$profilingPath = false;
            GroundDriveProfile.end();
        }
    }
}
