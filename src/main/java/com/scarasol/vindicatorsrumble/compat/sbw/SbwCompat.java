package com.scarasol.vindicatorsrumble.compat.sbw;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.init.ModTags;
import com.scarasol.vindicatorsrumble.util.VehicleAiMemory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

public class SbwCompat {
    public static Optional<VehicleController> createController(Entity entity) {
        if (entity instanceof VehicleEntity vehicle) {
            return Optional.of(new SbwVehicleController(vehicle));
        }
        return Optional.empty();
    }

    public static Optional<VehicleController> controllerForMob(Mob mob) {
        Entity vehicle = mob.getVehicle();
        return vehicle == null ? Optional.empty() : createController(vehicle);
    }

    public static List<VehicleController> findControllers(Level level, AABB area) {
        return level.getEntitiesOfClass(VehicleEntity.class, area).stream()
                .map(vehicle -> (VehicleController) new SbwVehicleController(vehicle))
                .toList();
    }

    public static boolean hasTaggedAiPassenger(VehicleEntity vehicle) {
        return vehicle.getPassengers().stream()
                .anyMatch(entity -> entity instanceof Mob mob && (vehicle.level().isClientSide()
                        ? mob.getType().is(ModTags.EntityTypes.VEHICLE_AI_USERS)
                        : VehicleAiMemory.isVehicleAiEnabled(mob)));
    }
}
