package com.scarasol.vindicatorsrumble.gametest.profile.mixin;

import com.scarasol.vindicatorsrumble.gametest.profile.GroundRadarProfile;
import com.scarasol.vindicatorsrumble.gametest.profile.GroundDriveProfile;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleDimensions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground.GroundPathCollision", remap = false)
public abstract class GroundPathTimingMixin {
    @Inject(method = "isClear", at = @At("HEAD"), remap = false)
    private static void begin(CollisionGetter level, Entity entity, Vec3 from, Vec3 to, VehicleDimensions dimensions,
                              CallbackInfoReturnable<Boolean> callback) {
        GroundDriveProfile.corridor(level, entity, from, to, dimensions);
        GroundRadarProfile.beginCorridor();
    }

    @Inject(method = "isClear", at = @At("RETURN"), remap = false)
    private static void end(CallbackInfoReturnable<Boolean> callback) {
        GroundRadarProfile.end();
    }
}
