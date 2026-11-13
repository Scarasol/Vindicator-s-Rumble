package com.scarasol.vindicatorsrumble.gametest.profile.mixin;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.gametest.profile.GroundRadarProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground.GroundObstacleRadar", remap = false)
public abstract class GroundRadarTimingMixin {
    @Inject(method = "scan", at = @At("HEAD"), remap = false)
    private static void beginScan(VehicleController controller, CallbackInfoReturnable<?> callback) {
        GroundRadarProfile.begin(false);
    }

    @Inject(method = "scan", at = @At("RETURN"), remap = false)
    private static void endScan(VehicleController controller, CallbackInfoReturnable<?> callback) {
        GroundRadarProfile.end();
    }

    @Inject(method = "hasForwardTurnSpace", at = @At("HEAD"), remap = false)
    private static void beginTurn(VehicleController controller, int turn, double yaw, CallbackInfoReturnable<?> callback) {
        GroundRadarProfile.begin(true);
    }

    @Inject(method = "hasForwardTurnSpace", at = @At("RETURN"), remap = false)
    private static void endTurn(VehicleController controller, int turn, double yaw, CallbackInfoReturnable<?> callback) {
        GroundRadarProfile.end();
    }
}
