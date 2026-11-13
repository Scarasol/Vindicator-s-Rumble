package com.scarasol.vindicatorsrumble.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class VehicleClaimManager {
    private static final long CLAIM_TTL = 80L;
    private static final Map<Level, Map<Key, Claim>> LEVELS = new WeakHashMap<>();

    public static boolean claim(Mob mob, Entity vehicle, int seatId) {
        Map<Key, Claim> claims = LEVELS.computeIfAbsent(mob.level(), key -> new HashMap<>());
        Key key = new Key(vehicle.getUUID(), seatId);
        Claim existing = claims.get(key);
        long now = mob.level().getGameTime();
        if (existing != null && existing.expiresAt >= now && !existing.owner.equals(mob.getUUID())) {
            return false;
        }

        claims.put(key, new Claim(mob.getUUID(), now + CLAIM_TTL));
        return true;
    }

    public static boolean isClaimedByOther(Mob mob, Entity vehicle, int seatId) {
        Map<Key, Claim> claims = LEVELS.get(mob.level());
        if (claims == null) return false;
        Key key = new Key(vehicle.getUUID(), seatId);
        Claim claim = claims.get(key);
        if (claim != null && claim.expiresAt < mob.level().getGameTime()) {
            claims.remove(key);
            return false;
        }
        return claim != null && !claim.owner.equals(mob.getUUID());
    }

    public static void release(Mob mob, Entity vehicle, int seatId) {
        Key key = new Key(vehicle.getUUID(), seatId);
        Map<Key, Claim> claims = LEVELS.get(mob.level());
        if (claims == null) return;
        Claim claim = claims.get(key);
        if (claim != null && claim.owner.equals(mob.getUUID())) {
            claims.remove(key);
        }
    }

    public static void releaseAll(Mob mob) {
        UUID owner = mob.getUUID();
        Map<Key, Claim> claims = LEVELS.get(mob.level());
        if (claims != null) claims.values().removeIf(claim -> claim.owner.equals(owner));
    }

    public static void releaseVehicle(Entity vehicle) {
        UUID vehicleId = vehicle.getUUID();
        Map<Key, Claim> claims = LEVELS.get(vehicle.level());
        if (claims != null) claims.keySet().removeIf(key -> key.vehicle.equals(vehicleId));
    }

    public static void expire(Level level) {
        Map<Key, Claim> claims = LEVELS.get(level);
        if (claims == null) return;
        long now = level.getGameTime();
        claims.values().removeIf(claim -> claim.expiresAt < now);
        if (claims.isEmpty()) LEVELS.remove(level);
    }

    public static void clearLevel(Level level) {
        LEVELS.remove(level);
    }

    private record Key(UUID vehicle, int seat) {
    }

    private record Claim(UUID owner, long expiresAt) {
    }
}
