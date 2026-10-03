package com.scarasol.vindicatorsrumble.compat.guardvillagers;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import tallestegg.guardvillagers.configuration.GuardConfig;
import tallestegg.guardvillagers.entities.Guard;

public final class GuardVillagersCompat {
    private GuardVillagersCompat() {
    }

    public static boolean isPlayerAlly(Entity entity, Player player, boolean strict) {
        return entity instanceof Guard guard
                && (!strict
                || player.getUUID().equals(guard.getOwnerId())
                || player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)
                || guard.getPlayerReputation(player) >= GuardConfig.COMMON.reputationRequirement.get());
    }
}
