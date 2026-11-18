package com.scarasol.vindicatorsrumble.compat;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.compat.limitless.LimitlessCompat;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class VehicleControllers {
    private VehicleControllers() {
    }

    public static Optional<VehicleController> create(Entity entity) {
        if (ModCompat.isSbwLoaded()) {
            Optional<VehicleController> controller = SbwCompat.createController(entity);
            if (controller.isPresent()) {
                return controller;
            }
        }
        return ModCompat.isLimitlessVehicleLoaded() ? LimitlessCompat.createController(entity) : Optional.empty();
    }

    public static Optional<VehicleController> forMob(Mob mob) {
        Entity vehicle = mob.getVehicle();
        return vehicle == null ? Optional.empty() : create(vehicle);
    }

    public static List<VehicleController> find(Level level, AABB area) {
        List<VehicleController> sbw = ModCompat.isSbwLoaded() ? SbwCompat.findControllers(level, area) : List.of();
        if (!ModCompat.isLimitlessVehicleLoaded()) {
            return sbw;
        }
        List<VehicleController> limitless = LimitlessCompat.findControllers(level, area);
        if (sbw.isEmpty()) {
            return limitless;
        }
        List<VehicleController> found = new ArrayList<>(sbw);
        found.addAll(limitless);
        return found;
    }
}
