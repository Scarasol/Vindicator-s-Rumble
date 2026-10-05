package com.scarasol.vindicatorsrumble.mixin.sbw;

import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleThreats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MissileProjectile.class)
public abstract class MissileThreatMixin {
    @Inject(method = "tick()V", at = @At("TAIL"))
    private void vindicatorsRumble$refreshIncoming(CallbackInfo ci) {
        this.vindicatorsRumble$reportIncoming(ci);
    }

    @Inject(method = {"setTargetUUID(Ljava/lang/String;)V", "setLost(Z)V",
            "setLostTarget(Z)V", "setDistracted(Z)V"}, at = @At("TAIL"), remap = false)
    private void vindicatorsRumble$reportIncoming(CallbackInfo ci) {
        MissileProjectile missile = (MissileProjectile) (Object) this;
        if (!(missile.level() instanceof ServerLevel)) return;
        Entity target = missile.isAlive() && !missile.isLost() && !missile.isLostTarget()
                && !missile.isDistracted() && missile.getOwner() != null
                ? EntityFindUtil.findEntity(missile.level(), missile.getTargetUUID()) : null;
        if (target == null) {
            VehicleThreats.clearSource(missile);
        } else {
            VehicleThreats.report(missile, target, VehicleThreats.Stage.INCOMING, 2);
        }
    }
}
