package com.scarasol.vindicatorsrumble.compat;

import com.scarasol.vindicatorsrumble.compat.limitless.LimitlessSpawning;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.function.BiFunction;

public final class VehicleSpawnCandidates {
    private VehicleSpawnCandidates() {
    }

    public static Optional<Candidate> resolve(ResourceLocation id) {
        if (ModCompat.isLimitlessVehicleLoaded()) {
            Optional<Candidate> candidate = LimitlessSpawning.resolve(id);
            if (candidate.isPresent() || ModCompat.LIMITLESS_VEHICLE.equals(id.getNamespace())) {
                return candidate;
            }
        }
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
            return Optional.empty();
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        return Optional.of(new Candidate(id, (level, origin) -> type.create(level)));
    }

    public static boolean deferBoarding(Entity vehicle, Runnable board) {
        return ModCompat.isLimitlessVehicleLoaded() && LimitlessSpawning.deferBoarding(vehicle, board);
    }

    public record Candidate(ResourceLocation id, BiFunction<ServerLevel, Vec3, Entity> factory) {
        public Entity create(ServerLevel level, Vec3 origin) {
            return this.factory.apply(level, origin);
        }
    }
}
