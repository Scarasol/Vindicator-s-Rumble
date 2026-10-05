package com.scarasol.vindicatorsrumble.mixin.sbw;

import com.atsuishio.superbwarfare.network.message.send.SeekingWeaponWarningMessage;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.SeekType;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleThreats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(SeekingWeaponWarningMessage.class)
public abstract class SeekingWeaponWarningMixin {
    @Inject(method = "handler(Ljava/util/function/Supplier;)V", at = @At("HEAD"), remap = false)
    private void vindicatorsRumble$reportLock(Supplier<NetworkEvent.Context> context, CallbackInfo ci) {
        ServerPlayer sender = context.get().getSender();
        if (sender == null || sender.isSpectator()) return;
        SeekingWeaponWarningMessage message = (SeekingWeaponWarningMessage) (Object) this;
        Entity target = sender.serverLevel().getEntity(message.getUuid());
        if (target != null && this.vindicatorsRumble$canSeek(sender, target)) {
            VehicleThreats.report(sender, target, message.getLockOn()
                    ? VehicleThreats.Stage.LOCKED : VehicleThreats.Stage.ACQUIRING, 10);
        }
    }

    private boolean vindicatorsRumble$canSeek(ServerPlayer sender, Entity target) {
        double range = 0.0D;
        if (sender.getMainHandItem().getItem() instanceof GunItem) {
            GunData data = GunData.from(sender.getMainHandItem());
            if (data.get(GunProp.SEEK_TYPE) != SeekType.NONE) {
                range = data.get(GunProp.CAN_GUIDED_BY_RADAR)
                        ? Math.max(data.get(GunProp.SEEK_RANGE), data.get(GunProp.MAX_GUIDED_RANGE))
                        : data.get(GunProp.SEEK_RANGE);
            }
        }
        if (sender.getVehicle() instanceof VehicleEntity vehicle) {
            GunData data = vehicle.getGunData(sender);
            var seek = data == null ? null : data.get(GunProp.SEEK_WEAPON_INFO);
            if (seek != null && !seek.getOnlyLockBlock() && seek.getOnlyLockEntity()) {
                range = Math.max(range, seek.getCanGuidedByRadar()
                        ? Math.max(seek.getSeekRange(), seek.getMaxGuidedRange()) : seek.getSeekRange());
            }
        }
        return range > 0.0D && sender.distanceToSqr(target) <= range * range;
    }
}
