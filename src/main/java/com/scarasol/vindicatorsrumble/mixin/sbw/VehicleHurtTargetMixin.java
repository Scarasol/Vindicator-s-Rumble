package com.scarasol.vindicatorsrumble.mixin.sbw;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.scarasol.vindicatorsrumble.util.VehicleAiLogger;
import com.scarasol.vindicatorsrumble.util.VehicleAiMemory;
import com.scarasol.vindicatorsrumble.util.VehicleAiTuning;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VehicleEntity.class)
public abstract class VehicleHurtTargetMixin {
    @Inject(
            method = "onHurt(FLnet/minecraft/world/entity/Entity;Z)V",
            at = @At("TAIL"),
            remap = false
    )
    private void vindicatorsRumble$shareVehicleAttacker(float amount, Entity attacker, boolean send, CallbackInfo ci) {
        if (!send || !(attacker instanceof LivingEntity target)) {
            return;
        }

        VehicleEntity vehicle = (VehicleEntity) (Object) this;
        for (Entity passenger : vehicle.getPassengers()) {
            if (!(passenger instanceof Mob mob) || !VehicleAiMemory.isVehicleAiEnabled(mob)) {
                continue;
            }
            if (!VehicleAiTuning.isValidVehicleCombatTarget(mob, target)) {
                continue;
            }
            LivingEntity previousTarget = mob.getTarget();
            if (previousTarget != target) {
                VehicleAiMemory.clear(mob);
                mob.setTarget(target);
                VehicleAiLogger.debug(mob,
                        "Vehicle hurt target synced vehicle={} attacker={} amount={}",
                        vehicle.getId(),
                        target.getId(),
                        amount);
            }
        }
    }
}
