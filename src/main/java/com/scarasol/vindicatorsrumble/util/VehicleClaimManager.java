package com.scarasol.vindicatorsrumble.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class VehicleClaimManager {
    private static final long CLAIM_TTL = 80L;
    private static final Map<Key, Claim> CLAIMS = new HashMap<>();

    public static boolean claim(Mob mob, Entity vehicle, int seatId) {
        cleanup(mob.level());
        Key key = new Key(vehicle.getUUID(), seatId);
        Claim existing = CLAIMS.get(key);
        long now = mob.level().getGameTime();
        if (existing != null && existing.expiresAt >= now && !existing.owner.equals(mob.getUUID())) {
            return false;
        }

        CLAIMS.put(key, new Claim(mob.getUUID(), now + CLAIM_TTL));
        return true;
    }

    public static boolean isClaimedByOther(Mob mob, Entity vehicle, int seatId) {
        cleanup(mob.level());
        Claim claim = CLAIMS.get(new Key(vehicle.getUUID(), seatId));
        return claim != null && !claim.owner.equals(mob.getUUID());
    }

    public static void release(Mob mob, Entity vehicle, int seatId) {
        Key key = new Key(vehicle.getUUID(), seatId);
        Claim claim = CLAIMS.get(key);
        if (claim != null && claim.owner.equals(mob.getUUID())) {
            CLAIMS.remove(key);
        }
    }

    public static void releaseAll(Mob mob) {
        UUID owner = mob.getUUID();
        CLAIMS.entrySet().removeIf(entry -> entry.getValue().owner.equals(owner));
    }

    public static void releaseVehicle(Entity vehicle) {
        UUID vehicleId = vehicle.getUUID();
        CLAIMS.entrySet().removeIf(entry -> entry.getKey().vehicle.equals(vehicleId));
    }

    private static void cleanup(Level level) {
        long now = level.getGameTime();
        Iterator<Map.Entry<Key, Claim>> iterator = CLAIMS.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt < now) {
                iterator.remove();
            }
        }
    }

    private record Key(UUID vehicle, int seat) {
    }

    private record Claim(UUID owner, long expiresAt) {
    }
}
