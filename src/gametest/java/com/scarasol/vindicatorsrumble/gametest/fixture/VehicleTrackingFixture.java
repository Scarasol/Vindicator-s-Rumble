package com.scarasol.vindicatorsrumble.gametest.fixture;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;

import java.lang.reflect.Field;

public final class VehicleTrackingFixture {
    private VehicleTrackingFixture() {
    }

    public static void roundTrip(ServerLevel level, Entity entity, Runnable whileHidden) {
        PersistentEntitySectionManager<?> manager = entityManager(level);
        ChunkPos chunk = new ChunkPos(entity.blockPosition());
        // New force-load tickets may not have promoted native entity visibility yet.
        manager.updateChunkStatus(chunk, Visibility.TICKING);
        if (level.getEntity(entity.getUUID()) != entity) {
            throw new IllegalStateException("Invalid fixture: entity was not tracked before the visibility transition");
        }
        manager.updateChunkStatus(chunk, Visibility.HIDDEN);
        try {
            whileHidden.run();
        } finally {
            manager.updateChunkStatus(chunk, Visibility.TICKING);
        }
    }

    public static void setVisibility(ServerLevel level, ChunkPos chunk, Visibility visibility) {
        entityManager(level).updateChunkStatus(chunk, visibility);
    }

    private static PersistentEntitySectionManager<?> entityManager(ServerLevel level) {
        for (Field field : ServerLevel.class.getDeclaredFields()) {
            if (field.getType() != PersistentEntitySectionManager.class) continue;
            try {
                field.setAccessible(true);
                return (PersistentEntitySectionManager<?>) field.get(level);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not access the native entity tracking fixture", failure);
            }
        }
        throw new IllegalStateException("Missing native entity manager");
    }
}
