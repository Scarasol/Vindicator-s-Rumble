package com.scarasol.vindicatorsrumble.mixin.sbw;

import com.atsuishio.superbwarfare.data.vehicle.subdata.EngineType;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.scarasol.vindicatorsrumble.util.VehicleAiLogger;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VehicleEntity.class)
public abstract class VehiclePlayerFlightDebugMixin {
    @Inject(
            method = "baseTick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;travel()V",
                    shift = At.Shift.AFTER,
                    remap = false
            )
    )
    private void vindicatorsRumble$logPlayerHelicopterControl(CallbackInfo ci) {
        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        if (vehicle.level().isClientSide()
                || !VehicleAiLogger.shouldLog(vehicle, 40)
                || vehicle.computed().getEngineType() != EngineType.HELICOPTER
                || !(vehicle.getFirstPassenger() instanceof Player player)) {
            return;
        }

        Vec3 velocity = vehicle.getDeltaMovement();
        double yaw = Math.toRadians(vehicle.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double forwardSpeed = velocity.x * forwardX + velocity.z * forwardZ;
        double lateralSpeed = velocity.x * forwardZ - velocity.z * forwardX;
        VehicleAiLogger.debugEvery(vehicle, 40,
                "Player helicopter driver={}#{} pos={} velocity={} hSpeed={} forwardSpeed={} lateralSpeed={} yaw={} pitch={} roll={} playerYaw={} playerPitch={} keys=[F={},B={},L={},R={},U={},D={},sprint={}] mouse=[{},{}] hover={} power={} rotor={} onGround={}",
                player.getGameProfile().getName(),
                player.getId(),
                vehicle.position(),
                velocity,
                velocity.horizontalDistance(),
                forwardSpeed,
                lateralSpeed,
                vehicle.getYRot(),
                vehicle.getXRot(),
                vehicle.getRoll(),
                player.getYRot(),
                player.getXRot(),
                vehicle.forwardInputDown(),
                vehicle.backInputDown(),
                vehicle.leftInputDown(),
                vehicle.rightInputDown(),
                vehicle.upInputDown(),
                vehicle.downInputDown(),
                vehicle.sprintInputDown(),
                vehicle.getMouseMoveSpeedX(),
                vehicle.getMouseMoveSpeedY(),
                vehicle.getHoverMode(),
                vehicle.getPower(),
                vehicle.getSynchedPropellerRot(),
                vehicle.onGround());
    }

}
