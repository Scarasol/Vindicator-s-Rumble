package com.scarasol.vindicatorsrumble.util;

import com.scarasol.vindicatorsrumble.api.vehicle.CurrentWeaponSnapshot;
import com.scarasol.vindicatorsrumble.api.vehicle.DriveRequest;
import com.scarasol.vindicatorsrumble.api.vehicle.SeatHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class VehicleAiMemory {
    private static final Map<UUID, TimedDriveRequest> TACTICAL_DRIVE_REQUESTS = new HashMap<>();
    private static final Map<UUID, TimedDriveRequest> VEHICLE_TACTICAL_DRIVE_REQUESTS = new HashMap<>();
    private static final Map<UUID, DriveRequest> NAVIGATION_DRIVE_REQUESTS = new HashMap<>();
    private static final Map<UUID, TimedCurrentWeaponSnapshot> CURRENT_WEAPONS = new HashMap<>();
    private static final Map<UUID, TimedCurrentWeaponSnapshot> VEHICLE_CURRENT_WEAPONS = new HashMap<>();

    public static void setDriveRequest(Mob mob, DriveRequest request, int ttlTicks) {
        TACTICAL_DRIVE_REQUESTS.put(mob.getUUID(), new TimedDriveRequest(request, mob.level().getGameTime() + ttlTicks));
    }

    public static void setVehicleDriveRequest(Entity vehicle, DriveRequest request, int ttlTicks) {
        VEHICLE_TACTICAL_DRIVE_REQUESTS.put(vehicle.getUUID(), new TimedDriveRequest(request, vehicle.level().getGameTime() + ttlTicks));
    }

    public static void setNavigationDriveRequest(Mob mob, DriveRequest request) {
        NAVIGATION_DRIVE_REQUESTS.put(mob.getUUID(), request);
    }

    public static void setCurrentWeapon(Mob mob, CurrentWeaponSnapshot snapshot, int ttlTicks) {
        CURRENT_WEAPONS.put(mob.getUUID(), new TimedCurrentWeaponSnapshot(snapshot, mob.level().getGameTime() + ttlTicks));
    }

    public static void setVehicleCurrentWeapon(Entity vehicle, CurrentWeaponSnapshot snapshot, int ttlTicks) {
        VEHICLE_CURRENT_WEAPONS.put(vehicle.getUUID(), new TimedCurrentWeaponSnapshot(snapshot, vehicle.level().getGameTime() + ttlTicks));
    }

    public static DriveRequest getDriveRequest(Mob mob) {
        cleanup(mob);
        TimedDriveRequest request = TACTICAL_DRIVE_REQUESTS.get(mob.getUUID());
        return request == null ? DriveRequest.NONE : request.request;
    }

    public static DriveRequest getVehicleDriveRequest(Mob mob, VehicleController controller) {
        cleanup(mob);
        if (controller == null || controller.entity() == null) {
            return DriveRequest.NONE;
        }
        TimedDriveRequest request = VEHICLE_TACTICAL_DRIVE_REQUESTS.get(controller.entity().getUUID());
        return request == null ? DriveRequest.NONE : request.request;
    }

    public static DriveRequest getNavigationDriveRequest(Mob mob) {
        return NAVIGATION_DRIVE_REQUESTS.getOrDefault(mob.getUUID(), DriveRequest.NONE);
    }

    public static Optional<CurrentWeaponSnapshot> getCurrentWeapon(Mob mob) {
        cleanup(mob);
        TimedCurrentWeaponSnapshot snapshot = CURRENT_WEAPONS.get(mob.getUUID());
        return snapshot == null ? Optional.empty() : Optional.of(snapshot.snapshot);
    }

    public static Optional<CurrentWeaponSnapshot> getCurrentWeapon(Mob mob, VehicleController controller, Entity target) {
        if (controller == null || target == null) {
            return Optional.empty();
        }
        int seatId = controller.seatOf(mob).map(SeatHandle::id).orElse(-1);
        return getCurrentWeapon(mob)
                .filter(snapshot -> snapshot.matchesContext(controller.entity().getUUID(), seatId, target.getUUID()));
    }

    public static Optional<CurrentWeaponSnapshot> getVehicleCurrentWeapon(Mob mob, VehicleController controller, Entity target) {
        if (controller == null || controller.entity() == null || target == null) {
            return Optional.empty();
        }
        cleanup(mob);
        TimedCurrentWeaponSnapshot snapshot = VEHICLE_CURRENT_WEAPONS.get(controller.entity().getUUID());
        return snapshot == null
                ? Optional.empty()
                : Optional.of(snapshot.snapshot)
                        .filter(value -> value.matchesVehicleTarget(controller.entity().getUUID(), target.getUUID()));
    }

    public static List<CurrentWeaponSnapshot> getVehicleCurrentWeapons(Mob mob, VehicleController controller, Entity target) {
        if (controller == null || controller.entity() == null || target == null) {
            return List.of();
        }
        cleanup(mob);
        UUID vehicleUuid = controller.entity().getUUID();
        UUID targetUuid = target.getUUID();
        List<CurrentWeaponSnapshot> snapshots = new ArrayList<>();
        for (TimedCurrentWeaponSnapshot timedSnapshot : CURRENT_WEAPONS.values()) {
            CurrentWeaponSnapshot snapshot = timedSnapshot.snapshot;
            if (snapshot.matchesVehicleTarget(vehicleUuid, targetUuid)) {
                snapshots.add(snapshot);
            }
        }
        TimedCurrentWeaponSnapshot vehicleSnapshot = VEHICLE_CURRENT_WEAPONS.get(vehicleUuid);
        if (vehicleSnapshot != null && vehicleSnapshot.snapshot.matchesVehicleTarget(vehicleUuid, targetUuid)) {
            snapshots.add(vehicleSnapshot.snapshot);
        }
        return snapshots;
    }

    public static void clearNavigationDriveRequest(Mob mob) {
        NAVIGATION_DRIVE_REQUESTS.remove(mob.getUUID());
    }

    public static void clearDriveRequest(Mob mob) {
        TACTICAL_DRIVE_REQUESTS.remove(mob.getUUID());
    }

    public static void clearCurrentWeapon(Mob mob) {
        CURRENT_WEAPONS.remove(mob.getUUID());
    }

    public static void clear(Mob mob) {
        TACTICAL_DRIVE_REQUESTS.remove(mob.getUUID());
        NAVIGATION_DRIVE_REQUESTS.remove(mob.getUUID());
        TimedCurrentWeaponSnapshot currentWeapon = CURRENT_WEAPONS.remove(mob.getUUID());
        if (currentWeapon != null) {
            UUID vehicleUuid = currentWeapon.snapshot.vehicleUuid();
            TimedCurrentWeaponSnapshot vehicleWeapon = VEHICLE_CURRENT_WEAPONS.get(vehicleUuid);
            if (vehicleWeapon != null && vehicleWeapon.snapshot.seatId() == currentWeapon.snapshot.seatId()) {
                VEHICLE_CURRENT_WEAPONS.remove(vehicleUuid);
                VEHICLE_TACTICAL_DRIVE_REQUESTS.remove(vehicleUuid);
            }
        }
    }

    public static void clearVehicle(Entity vehicle) {
        UUID vehicleUuid = vehicle.getUUID();
        VEHICLE_TACTICAL_DRIVE_REQUESTS.remove(vehicleUuid);
        VEHICLE_CURRENT_WEAPONS.remove(vehicleUuid);
        CURRENT_WEAPONS.entrySet().removeIf(entry -> entry.getValue().snapshot.vehicleUuid().equals(vehicleUuid));
    }

    private static void cleanup(Mob mob) {
        long now = mob.level().getGameTime();
        Iterator<Map.Entry<UUID, TimedDriveRequest>> iterator = TACTICAL_DRIVE_REQUESTS.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt < now) {
                iterator.remove();
            }
        }
        Iterator<Map.Entry<UUID, TimedDriveRequest>> vehicleRequestIterator = VEHICLE_TACTICAL_DRIVE_REQUESTS.entrySet().iterator();
        while (vehicleRequestIterator.hasNext()) {
            if (vehicleRequestIterator.next().getValue().expiresAt < now) {
                vehicleRequestIterator.remove();
            }
        }
        Iterator<Map.Entry<UUID, TimedCurrentWeaponSnapshot>> weaponIterator = CURRENT_WEAPONS.entrySet().iterator();
        while (weaponIterator.hasNext()) {
            if (weaponIterator.next().getValue().expiresAt < now) {
                weaponIterator.remove();
            }
        }
        Iterator<Map.Entry<UUID, TimedCurrentWeaponSnapshot>> vehicleWeaponIterator = VEHICLE_CURRENT_WEAPONS.entrySet().iterator();
        while (vehicleWeaponIterator.hasNext()) {
            if (vehicleWeaponIterator.next().getValue().expiresAt < now) {
                vehicleWeaponIterator.remove();
            }
        }
    }

    private record TimedDriveRequest(DriveRequest request, long expiresAt) {
    }

    private record TimedCurrentWeaponSnapshot(CurrentWeaponSnapshot snapshot, long expiresAt) {
    }
}
