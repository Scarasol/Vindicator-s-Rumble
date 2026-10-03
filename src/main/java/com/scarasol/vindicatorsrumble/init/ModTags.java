package com.scarasol.vindicatorsrumble.init;

import com.scarasol.vindicatorsrumble.VindicatorsRumbleMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public class ModTags {
    public static class EntityTypes {
        public static final TagKey<EntityType<?>> VEHICLE_AI_USERS = tag("vehicle_ai_users");

        private static TagKey<EntityType<?>> tag(String name) {
            return TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(VindicatorsRumbleMod.MODID, name));
        }
    }
}
