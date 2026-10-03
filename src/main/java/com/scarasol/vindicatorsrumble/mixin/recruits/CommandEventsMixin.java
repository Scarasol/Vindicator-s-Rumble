package com.scarasol.vindicatorsrumble.mixin.recruits;

import com.scarasol.vindicatorsrumble.compat.recruits.RecruitsCompat;
import com.talhanation.recruits.CommandEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = CommandEvents.class, remap = false)
public abstract class CommandEventsMixin {
    @Inject(method = "onStrategicFireCommand", at = @At("TAIL"), remap = false)
    private static void vindicatorsRumble$commandVehicleFire(Player player, UUID owner,
                                                            AbstractRecruitEntity recruit, UUID group,
                                                            boolean shouldFire, CallbackInfo ci) {
        RecruitsCompat.onStrategicFireCommand(player, owner, recruit, group, shouldFire);
    }
}
