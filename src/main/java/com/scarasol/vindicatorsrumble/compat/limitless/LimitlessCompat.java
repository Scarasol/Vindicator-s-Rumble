package com.scarasol.vindicatorsrumble.compat.limitless;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;
import org.ywzj.vehicle.entity.vehicle.TrackedVehicle;
import org.ywzj.vehicle.entity.vehicle.WheeledVehicle;

import java.util.List;
import java.util.Optional;

public final class LimitlessCompat {
    private LimitlessCompat() {
    }

    public static Optional<VehicleController> createController(Entity entity) {
        if (entity instanceof AbstractVehicle vehicle && !vehicle.uav && !vehicle.remote
                && (vehicle instanceof WheeledVehicle || vehicle instanceof TrackedVehicle)) {
            return Optional.of(new LimitlessVehicleController(vehicle));
        }
        return Optional.empty();
    }

    public static List<VehicleController> findControllers(Level level, AABB area) {
        return level.getEntitiesOfClass(AbstractVehicle.class, area).stream()
                .map(LimitlessCompat::createController)
                .flatMap(Optional::stream)
                .toList();
    }
}
