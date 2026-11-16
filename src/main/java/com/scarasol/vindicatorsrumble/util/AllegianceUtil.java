package com.scarasol.vindicatorsrumble.util;

import com.scarasol.vindicatorsrumble.compat.ModCompat;
import com.scarasol.vindicatorsrumble.compat.guardvillagers.GuardVillagersCompat;
import com.scarasol.vindicatorsrumble.compat.recruits.RecruitsCompat;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.init.ModTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;

import java.util.ArrayList;
import java.util.List;

public class AllegianceUtil {
    private static final List<TagKey<EntityType<?>>> FRIENDLY_TAGS = new ArrayList<>();
    private static List<? extends String> cachedConfigTags = List.of();

    public static boolean isSameSide(Entity first, Entity second) {
        if (first == null || second == null) {
            return false;
        }
        if (first == second) {
            return true;
        }
        if (isActivelyHostileToPlayer(first, second)) {
            return false;
        }
        if (first.getType() == second.getType()) {
            return true;
        }
        if (first.isAlliedTo(second) || second.isAlliedTo(first)) {
            return true;
        }
        if (sharesFriendlyTag(first, second)) {
            return true;
        }
        if (first instanceof Raider && (second instanceof Raider || second instanceof Vex)
                || second instanceof Raider && (first instanceof Raider || first instanceof Vex)) {
            return true;
        }
        if (ModCompat.isGuardVillagersLoaded() && GuardVillagersCompat.isVillageAlly(first, second)) {
            return true;
        }
        return isConfiguredPlayerAlly(first, second);
    }

    public static boolean hasNonFriendlyPassenger(Entity entity, Iterable<Entity> passengers) {
        for (Entity passenger : passengers) {
            if (passenger != entity && !isSameSide(entity, passenger)) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldProjectileIgnoreTarget(Entity target, Entity shooter) {
        if (!(shooter instanceof Mob shooterMob)
                || !shooterMob.getType().is(ModTags.EntityTypes.VEHICLE_AI_USERS)) {
            return false;
        }
        return shouldIgnoreFriendlyDamage(target, shooter);
    }

    // Callers own applicability (projectile tags or admitted AI drivers).
    public static boolean shouldIgnoreFriendlyDamage(Entity target, Entity attacker) {
        if (target == null || attacker == null) {
            return false;
        }
        if (target == attacker || target == attacker.getVehicle()
                || target.getRootVehicle() == attacker.getRootVehicle()) {
            return true;
        }
        if (isSameSide(attacker, target)) {
            return true;
        }
        Entity rootVehicle = target.getRootVehicle();
        return hasOnlyFriendlyLivingPassengers(attacker, target)
                || rootVehicle != target && hasOnlyFriendlyLivingPassengers(attacker, rootVehicle);
    }

    private static boolean hasOnlyFriendlyLivingPassengers(Entity shooter, Entity vehicle) {
        boolean foundFriendly = false;
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger == shooter) {
                return true;
            }
            if (!isSameSide(shooter, passenger)) {
                return false;
            }
            foundFriendly = true;
        }
        return foundFriendly;
    }

    private static boolean isActivelyHostileToPlayer(Entity first, Entity second) {
        Player player = first instanceof Player firstPlayer ? firstPlayer
                : second instanceof Player secondPlayer ? secondPlayer : null;
        Mob mob = first instanceof Mob firstMob ? firstMob
                : second instanceof Mob secondMob ? secondMob : null;
        return player != null && mob != null
                && (mob.getTarget() == player
                || mob instanceof NeutralMob neutralMob && neutralMob.isAngryAt(player));
    }

    private static boolean isConfiguredPlayerAlly(Entity first, Entity second) {
        Player player = first instanceof Player firstPlayer ? firstPlayer
                : second instanceof Player secondPlayer ? secondPlayer : null;
        Entity companion = player == first ? second : first;
        if (player == null) {
            return false;
        }

        boolean strict = CommonConfig.STRICT_PLAYER_ALLIANCE.get();
        return (ModCompat.isGuardVillagersLoaded()
                && GuardVillagersCompat.isPlayerAlly(companion, player, strict))
                || (ModCompat.isRecruitsLoaded()
                && RecruitsCompat.isPlayerAlly(companion, player, strict));
    }

    private static boolean sharesFriendlyTag(Entity first, Entity second) {
        refreshFriendlyTags();
        for (TagKey<EntityType<?>> tag : FRIENDLY_TAGS) {
            if (first.getType().is(tag) && second.getType().is(tag)) {
                return true;
            }
        }
        return false;
    }

    private static void refreshFriendlyTags() {
        List<? extends String> configTags = CommonConfig.FRIENDLY_TAG.get();
        if (configTags.equals(cachedConfigTags)) {
            return;
        }

        cachedConfigTags = List.copyOf(configTags);
        FRIENDLY_TAGS.clear();
        for (String tag : configTags) {
            try {
                FRIENDLY_TAGS.add(TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(tag)));
            } catch (RuntimeException ignored) {
            }
        }
    }
}
