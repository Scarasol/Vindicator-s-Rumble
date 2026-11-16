package com.scarasol.vindicatorsrumble.compat.sbw;

import com.atsuishio.superbwarfare.data.gun.ShootParameters;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.util.VehicleAiMemory;
import net.minecraft.world.entity.Mob;

public final class SbwVehicleAccuracy {
    private SbwVehicleAccuracy() {
    }

    public static ShootParameters apply(ShootParameters parameters) {
        if (!(parameters.ammoSupplier instanceof VehicleEntity vehicle)
                || !(parameters.shooter instanceof Mob shooter)
                || shooter.getVehicle() != vehicle
                || !VehicleAiMemory.isVehicleAiEnabled(shooter)) {
            return parameters;
        }
        double spread = spread(parameters, shooter);
        if (spread == parameters.spread) {
            return parameters;
        }
        return new ShootParameters(parameters.ammoSupplier, parameters.shooter, parameters.level,
                parameters.shootPosition, parameters.shootDirection, parameters.data, spread,
                parameters.zoom, parameters.targetEntityUUID, parameters.targetPos);
    }

    // The optional gun compatibility decorates this result before the shot parameters are copied.
    public static double spread(ShootParameters parameters, Mob shooter) {
        return parameters.spread * CommonConfig.VEHICLE_WEAPON_INACCURACY_MULTIPLIER.get();
    }
}
