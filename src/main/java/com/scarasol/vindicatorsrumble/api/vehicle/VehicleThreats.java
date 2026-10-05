package com.scarasol.vindicatorsrumble.api.vehicle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/** Transient server-side reports shared by weapon and vehicle adapters. */
public final class VehicleThreats {
    private static final Map<ServerLevel, Reports> LEVELS = new WeakHashMap<>();

    private VehicleThreats() {
    }

    public enum Stage {
        ACQUIRING, LOCKED, INCOMING
    }

    public record Threat(Entity source, Stage stage, long expiresAt) {
    }

    public static void report(Entity source, Entity target, Stage stage, int freshnessTicks) {
        if (!(source.level() instanceof ServerLevel level) || target.level() != level
                || !source.isAlive() || !target.isAlive() || freshnessTicks <= 0
                || source.getRootVehicle() == target.getRootVehicle()) {
            return;
        }
        Entity receiver = target.getRootVehicle();
        Reports reports = LEVELS.computeIfAbsent(level, key -> new Reports());
        UUID previous = reports.targets.put(source.getUUID(), receiver.getUUID());
        if (previous != null && !previous.equals(receiver.getUUID())) {
            Map<UUID, Threat> old = reports.receivers.get(previous);
            if (old != null) {
                old.remove(source.getUUID());
                if (old.isEmpty()) reports.receivers.remove(previous);
            }
        }
        reports.receivers.computeIfAbsent(receiver.getUUID(), key -> new HashMap<>())
                .put(source.getUUID(), new Threat(source, stage, level.getGameTime() + freshnessTicks));
    }

    public static Optional<Threat> strongest(Entity target) {
        if (!(target.level() instanceof ServerLevel level)) return Optional.empty();
        Reports reports = LEVELS.get(level);
        Map<UUID, Threat> sources = reports == null ? null
                : reports.receivers.get(target.getRootVehicle().getUUID());
        if (sources == null) return Optional.empty();
        Threat strongest = null;
        for (var iterator = sources.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            Threat threat = entry.getValue();
            if (!fresh(level, threat)) {
                iterator.remove();
                reports.targets.remove(entry.getKey());
            } else if (strongest == null || threat.stage().ordinal() > strongest.stage().ordinal()
                    || threat.stage() == strongest.stage()
                    && threat.source().distanceToSqr(target) < strongest.source().distanceToSqr(target)) {
                strongest = threat;
            }
        }
        if (sources.isEmpty()) reports.receivers.remove(target.getRootVehicle().getUUID());
        return Optional.ofNullable(strongest);
    }

    public static void clearSource(Entity source) {
        if (!(source.level() instanceof ServerLevel level)) return;
        Reports reports = LEVELS.get(level);
        UUID target = reports == null ? null : reports.targets.remove(source.getUUID());
        Map<UUID, Threat> sources = target == null ? null : reports.receivers.get(target);
        if (sources != null) {
            sources.remove(source.getUUID());
            if (sources.isEmpty()) reports.receivers.remove(target);
        }
    }

    public static void clearEntity(Entity entity) {
        clearSource(entity);
        if (!(entity.level() instanceof ServerLevel level)) return;
        Reports reports = LEVELS.get(level);
        Map<UUID, Threat> sources = reports == null ? null : reports.receivers.remove(entity.getUUID());
        if (sources != null) sources.keySet().forEach(reports.targets::remove);
    }

    public static void expire(ServerLevel level) {
        Reports reports = LEVELS.get(level);
        if (reports == null) return;
        reports.receivers.entrySet().removeIf(receiver -> {
            receiver.getValue().entrySet().removeIf(entry -> {
                if (fresh(level, entry.getValue()) && level.getEntity(receiver.getKey()) != null) return false;
                reports.targets.remove(entry.getKey());
                return true;
            });
            return receiver.getValue().isEmpty();
        });
        if (reports.receivers.isEmpty()) LEVELS.remove(level);
    }

    public static void clearLevel(ServerLevel level) {
        LEVELS.remove(level);
    }

    private static boolean fresh(ServerLevel level, Threat threat) {
        return level.getGameTime() < threat.expiresAt() && threat.source().isAlive()
                && threat.source().level() == level
                && level.getEntity(threat.source().getUUID()) == threat.source();
    }

    private static final class Reports {
        final Map<UUID, Map<UUID, Threat>> receivers = new HashMap<>();
        final Map<UUID, UUID> targets = new HashMap<>();
    }
}
