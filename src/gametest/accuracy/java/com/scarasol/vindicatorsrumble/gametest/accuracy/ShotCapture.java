package com.scarasol.vindicatorsrumble.gametest.accuracy;

import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class ShotCapture {
    private static final Map<Entity, List<ShootParameters>> SHOTS = new IdentityHashMap<>();

    public static List<ShootParameters> observe(Entity shooter) {
        return SHOTS.computeIfAbsent(shooter, ignored -> new ArrayList<>());
    }

    public static void capture(ShootParameters parameters) {
        List<ShootParameters> shots = SHOTS.get(parameters.shooter);
        if (shots != null) shots.add(parameters);
    }

    public static void forget(Entity shooter) {
        SHOTS.remove(shooter);
    }
}
