package com.scarasol.vindicatorsrumble.util;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleDimensions;
import net.minecraft.world.entity.Mob;

import java.util.Optional;
import java.util.UUID;

public class VehiclePathContext {
    private static final ThreadLocal<Entry> CURRENT = new ThreadLocal<>();

    public static Scope push(Mob mob, VehicleDimensions dimensions) {
        return push(mob, dimensions, false);
    }

    public static Scope push(Mob mob, VehicleDimensions dimensions, boolean narrow) {
        Entry previous = CURRENT.get();
        CURRENT.set(new Entry(mob.getUUID(), dimensions, narrow));
        return new Scope(previous);
    }

    public static Optional<VehicleDimensions> dimensionsFor(Mob mob) {
        Entry entry = CURRENT.get();
        if (entry == null || !entry.mobId.equals(mob.getUUID())) {
            return Optional.empty();
        }
        return Optional.of(entry.dimensions);
    }

    public static boolean isNarrowFor(Mob mob) {
        Entry entry = CURRENT.get();
        return entry != null && entry.mobId.equals(mob.getUUID()) && entry.narrow;
    }

    private record Entry(UUID mobId, VehicleDimensions dimensions, boolean narrow) {
    }

    public static class Scope implements AutoCloseable {
        private final Entry previous;
        private boolean closed;

        private Scope(Entry previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            if (this.previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(this.previous);
            }
            this.closed = true;
        }
    }
}
