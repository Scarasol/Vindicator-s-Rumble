package com.scarasol.vindicatorsrumble.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

public class CommonConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<Boolean> ENABLE_VEHICLE_AI;
    public static final ForgeConfigSpec.ConfigValue<Boolean> VEHICLE_AI_NEEDS_FUEL;
    public static final ForgeConfigSpec.ConfigValue<Boolean> VEHICLE_AI_NEEDS_AMMO;
    public static final ForgeConfigSpec.ConfigValue<Boolean> STRICT_PLAYER_ALLIANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FRIENDLY_TAG;
    public static final ForgeConfigSpec.DoubleValue VEHICLE_TARGET_RANGE;
    public static final ForgeConfigSpec.ConfigValue<Integer> VEHICLE_PATH_REPLAN_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue VEHICLE_NAVIGATION_MIN_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue HELICOPTER_HOVER_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue HELICOPTER_COMBAT_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue VINDICATOR_RAID_VEHICLE_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<Integer> VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> VINDICATOR_RAID_VEHICLE_TYPES;
    public static final ForgeConfigSpec.DoubleValue VILLAGE_DEFENDER_VEHICLE_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> VILLAGE_DEFENDER_VEHICLE_TYPES;
    public static final ForgeConfigSpec.DoubleValue GENERATED_VEHICLE_EXTRA_PASSENGER_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<Boolean> VEHICLE_AI_DEBUG;

    static {
        BUILDER.push("Vehicle AI");
        ENABLE_VEHICLE_AI = BUILDER.comment("Whether tagged mobs receive vehicle AI goals.")
                .define("Enable Vehicle AI", true);
        VEHICLE_AI_NEEDS_FUEL = BUILDER.comment("Whether vehicle AI must satisfy vehicle fuel or energy requirements when driving.")
                .define("Vehicle AI Needs Fuel", false);
        VEHICLE_AI_NEEDS_AMMO = BUILDER.comment("Whether vehicle AI must consume or provide ammo when firing vehicle weapons.")
                .define("Vehicle AI Needs Ammo", false);
        STRICT_PLAYER_ALLIANCE = BUILDER.comment("Whether Guard Villagers and Recruits must be owned, allied, or friendly by reputation to share vehicles with players. Disable for lenient neutral-friendly behavior.")
                .define("Strict Player Alliance", true);
        FRIENDLY_TAG = BUILDER.comment("Entity type tag IDs whose members are treated as allies for vehicle ownership and firing checks.")
                .defineList("Friendly Tag", ArrayList::new, element -> element instanceof String);
        VEHICLE_TARGET_RANGE = BUILDER.comment("Target acquisition follow range used by tagged mobs while riding a controllable vehicle.")
                .defineInRange("Vehicle Target Range", 64.0D, 1.0D, 256.0D);
        VEHICLE_PATH_REPLAN_INTERVAL = BUILDER.comment("Ticks between normal vehicle path replans.")
                .defineInRange("Vehicle Path Replan Interval", 20, 1, 200);
        VEHICLE_NAVIGATION_MIN_DISTANCE = BUILDER.comment("Minimum non-combat vanilla navigation distance required before vehicle AI drives a mounted vehicle.")
                .defineInRange("Vehicle Navigation Min Distance", 8.0D, 0.0D, 256.0D);
        HELICOPTER_HOVER_HEIGHT = BUILDER.comment("Preferred helicopter AI hover height above local ground.")
                .defineInRange("Helicopter Hover Height", 18.0D, 6.0D, 64.0D);
        HELICOPTER_COMBAT_DISTANCE = BUILDER.comment("Preferred horizontal distance between helicopter AI and its target.")
                .defineInRange("Helicopter Combat Distance", 42.0D, 12.0D, 160.0D);
        VINDICATOR_RAID_VEHICLE_CHANCE = BUILDER.comment("Chance for a Vindicator spawned by a high-level raid to spawn a nearby vehicle.")
                .defineInRange("Vindicator Raid Vehicle Chance", 0.05D, 0.0D, 1.0D);
        VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL = BUILDER.comment("Minimum raid Bad Omen level required before raid Vindicators can spawn vehicles.")
                .defineInRange("Vindicator Raid Minimum Bad Omen Level", 4, 0, 5);
        VINDICATOR_RAID_VEHICLE_TYPES = BUILDER.comment("""
                Vehicle entity type IDs and their weights for eligible raid Vindicators.
                Format: "superbwarfare:sodayo_pick_up_hmg, 5" means this vehicle has a spawn weight of 5.
                """).defineList("Vindicator Raid Vehicle Types", List.of(
                        "superbwarfare:sodayo_pick_up_hmg, 9",
                        "superbwarfare:sodayo_pick_up_tow, 1"
                ), CommonConfig::isWeightedResourceLocation);
        VILLAGE_DEFENDER_VEHICLE_CHANCE = BUILDER.comment("Chance for Guard Villagers or Recruits generated near villages to spawn a nearby vehicle.")
                .defineInRange("Village Defender Vehicle Chance", 0.0D, 0.0D, 1.0D);
        VILLAGE_DEFENDER_VEHICLE_TYPES = BUILDER.comment("""
                Vehicle entity type IDs and their weights for Guard Villagers or Recruits generated near villages.
                Format: "superbwarfare:sodayo_pick_up_hmg, 5" means this vehicle has a spawn weight of 5.
                """).defineList("Village Defender Vehicle Types", List.of(
                        "superbwarfare:sodayo_pick_up_hmg, 9",
                        "superbwarfare:sodayo_pick_up_tow, 1"
                ), CommonConfig::isWeightedResourceLocation);
        GENERATED_VEHICLE_EXTRA_PASSENGER_CHANCE = BUILDER.comment("Chance to add each successive extra non-driver passenger to a vehicle generated by this mod.")
                .defineInRange("Generated Vehicle Extra Passenger Chance", 0.15D, 0.0D, 1.0D);
        VEHICLE_AI_DEBUG = BUILDER.comment("Whether vehicle AI should emit extra debug logging.")
                .define("Vehicle AI Debug", false);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private static boolean isWeightedResourceLocation(Object element) {
        if (!(element instanceof String value)) {
            return false;
        }
        String[] parts = value.trim().split(",");
        if (parts.length == 0 || ResourceLocation.tryParse(parts[0].trim()) == null) {
            return false;
        }
        if (parts.length < 2) {
            return true;
        }
        try {
            double weight = Double.parseDouble(parts[1].trim());
            return weight > 0.0D && Double.isFinite(weight);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
