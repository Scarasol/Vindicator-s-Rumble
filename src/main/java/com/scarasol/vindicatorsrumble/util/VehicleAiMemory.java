package com.scarasol.vindicatorsrumble.util;

import com.scarasol.vindicatorsrumble.api.vehicle.CurrentWeaponSnapshot;
import com.scarasol.vindicatorsrumble.api.vehicle.DriveRequest;
import com.scarasol.vindicatorsrumble.api.vehicle.SeatHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class VehicleAiMemory {
    private static final Map<Level, Memory> LEVELS = new WeakHashMap<>();

    public static void setVehicleAiEnabled(Mob mob, boolean enabled) {
        if (enabled) {
            memory(mob.level()).vehicleAiUsers.add(mob);
        } else {
            Memory memory = LEVELS.get(mob.level());
            if (memory != null) memory.vehicleAiUsers.remove(mob);
        }
    }

    public static boolean isVehicleAiEnabled(Mob mob) {
        Memory memory = LEVELS.get(mob.level());
        return memory != null && memory.vehicleAiUsers.contains(mob);
    }

    public static void setDriveRequest(Mob mob, DriveRequest request, int ttlTicks) {
        memory(mob.level()).driveRequests.put(mob.getUUID(), new Timed<>(request, mob.level().getGameTime() + ttlTicks));
    }

    public static void setVehicleDriveRequest(Entity vehicle, DriveRequest request, int ttlTicks) {
        memory(vehicle.level()).vehicleDriveRequests.put(vehicle.getUUID(), new Timed<>(request, vehicle.level().getGameTime() + ttlTicks));
    }

    public static void setNavigationDriveRequest(Mob mob, DriveRequest request) {
        memory(mob.level()).navigationRequests.put(mob.getUUID(), request);
    }

    public static void setCurrentWeapon(Mob mob, CurrentWeaponSnapshot snapshot, int ttlTicks) {
        memory(mob.level()).weapons.put(mob.getUUID(), new Timed<>(snapshot, mob.level().getGameTime() + ttlTicks));
    }

    public static void setVehicleCurrentWeapon(Entity vehicle, CurrentWeaponSnapshot snapshot, int ttlTicks) {
        memory(vehicle.level()).vehicleWeapons.put(vehicle.getUUID(), new Timed<>(snapshot, vehicle.level().getGameTime() + ttlTicks));
    }

    public static DriveRequest getDriveRequest(Mob mob) {
        Memory memory = LEVELS.get(mob.level());
        return memory == null ? DriveRequest.NONE
                : live(memory.driveRequests, mob.getUUID(), mob.level()).orElse(DriveRequest.NONE);
    }

    public static DriveRequest getVehicleDriveRequest(Mob mob, VehicleController controller) {
        Memory memory = LEVELS.get(mob.level());
        if (memory == null || controller == null || controller.entity() == null
                || controller.level() != mob.level()) {
            return DriveRequest.NONE;
        }
        return live(memory.vehicleDriveRequests, controller.entity().getUUID(), mob.level()).orElse(DriveRequest.NONE);
    }

    public static DriveRequest getNavigationDriveRequest(Mob mob) {
        Memory memory = LEVELS.get(mob.level());
        return memory == null ? DriveRequest.NONE : memory.navigationRequests.getOrDefault(mob.getUUID(), DriveRequest.NONE);
    }

    public static Optional<CurrentWeaponSnapshot> getCurrentWeapon(Mob mob) {
        Memory memory = LEVELS.get(mob.level());
        return memory == null ? Optional.empty() : live(memory.weapons, mob.getUUID(), mob.level());
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
        Memory memory = LEVELS.get(mob.level());
        if (memory == null || controller == null || controller.entity() == null || target == null
                || controller.level() != mob.level()) {
            return Optional.empty();
        }
        return live(memory.vehicleWeapons, controller.entity().getUUID(), mob.level())
                .filter(value -> value.matchesVehicleTarget(controller.entity().getUUID(), target.getUUID()));
    }

    public static List<CurrentWeaponSnapshot> getVehicleCurrentWeapons(Mob mob, VehicleController controller, Entity target) {
        if (controller == null || controller.entity() == null || target == null
                || controller.level() != mob.level()) {
            return List.of();
        }
        UUID vehicleUuid = controller.entity().getUUID();
        UUID targetUuid = target.getUUID();
        List<CurrentWeaponSnapshot> snapshots = new ArrayList<>();
        // Passenger membership is the vehicle-local index; never scan other vehicles' snapshots.
        for (Entity passenger : controller.passengers()) {
            if (passenger instanceof Mob passengerMob) {
                getCurrentWeapon(passengerMob)
                        .filter(snapshot -> snapshot.matchesVehicleTarget(vehicleUuid, targetUuid))
                        .ifPresent(snapshots::add);
            }
        }
        getVehicleCurrentWeapon(mob, controller, target).ifPresent(snapshots::add);
        return snapshots;
    }

    public static void clearNavigationDriveRequest(Mob mob) {
        Memory memory = LEVELS.get(mob.level());
        if (memory != null) memory.navigationRequests.remove(mob.getUUID());
    }

    public static void clearDriveRequest(Mob mob) {
        Memory memory = LEVELS.get(mob.level());
        if (memory != null) memory.driveRequests.remove(mob.getUUID());
    }

    public static void clearCurrentWeapon(Mob mob) {
        Memory memory = LEVELS.get(mob.level());
        if (memory != null) memory.weapons.remove(mob.getUUID());
    }

    public static void clear(Mob mob) {
        Memory memory = LEVELS.get(mob.level());
        if (memory == null) return;
        memory.driveRequests.remove(mob.getUUID());
        memory.navigationRequests.remove(mob.getUUID());
        Timed<CurrentWeaponSnapshot> currentWeapon = memory.weapons.remove(mob.getUUID());
        if (currentWeapon != null) {
            UUID vehicleUuid = currentWeapon.value.vehicleUuid();
            Timed<CurrentWeaponSnapshot> vehicleWeapon = memory.vehicleWeapons.get(vehicleUuid);
            if (vehicleWeapon != null && vehicleWeapon.value.seatId() == currentWeapon.value.seatId()) {
                memory.vehicleWeapons.remove(vehicleUuid);
                memory.vehicleDriveRequests.remove(vehicleUuid);
            }
        }
    }

    public static void clearVehicle(Entity vehicle) {
        Memory memory = LEVELS.get(vehicle.level());
        if (memory == null) return;
        UUID vehicleUuid = vehicle.getUUID();
        memory.vehicleDriveRequests.remove(vehicleUuid);
        memory.vehicleWeapons.remove(vehicleUuid);
        memory.weapons.values().removeIf(snapshot -> snapshot.value.vehicleUuid().equals(vehicleUuid));
    }

    public static void expire(Level level) {
        Memory memory = LEVELS.get(level);
        if (memory == null) return;
        long now = level.getGameTime();
        memory.driveRequests.values().removeIf(value -> value.expiresAt < now);
        memory.vehicleDriveRequests.values().removeIf(value -> value.expiresAt < now);
        memory.weapons.values().removeIf(value -> value.expiresAt < now);
        memory.vehicleWeapons.values().removeIf(value -> value.expiresAt < now);
    }

    public static void clearLevel(Level level) {
        LEVELS.remove(level);
    }

    private static Memory memory(Level level) {
        return LEVELS.computeIfAbsent(level, key -> new Memory());
    }

    private static <T> Optional<T> live(Map<UUID, Timed<T>> entries, UUID id, Level level) {
        Timed<T> value = entries.get(id);
        if (value != null && value.expiresAt < level.getGameTime()) {
            entries.remove(id);
            return Optional.empty();
        }
        return value == null ? Optional.empty() : Optional.of(value.value);
    }

    private record Timed<T>(T value, long expiresAt) {
    }

    private static final class Memory {
        // Admission is sampled on entity load, not cleared with tactical or mounted state.
        final Set<Mob> vehicleAiUsers = Collections.newSetFromMap(new WeakHashMap<>());
        final Map<UUID, Timed<DriveRequest>> driveRequests = new HashMap<>();
        final Map<UUID, Timed<DriveRequest>> vehicleDriveRequests = new HashMap<>();
        final Map<UUID, DriveRequest> navigationRequests = new HashMap<>();
        final Map<UUID, Timed<CurrentWeaponSnapshot>> weapons = new HashMap<>();
        final Map<UUID, Timed<CurrentWeaponSnapshot>> vehicleWeapons = new HashMap<>();
    }
}
