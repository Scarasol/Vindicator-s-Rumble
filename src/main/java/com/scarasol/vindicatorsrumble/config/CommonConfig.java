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
    public static final ForgeConfigSpec.DoubleValue VEHICLE_WEAPON_INACCURACY_MULTIPLIER;
    public static final ForgeConfigSpec.ConfigValue<Boolean> STRICT_PLAYER_ALLIANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FRIENDLY_TAG;
    public static final ForgeConfigSpec.DoubleValue VEHICLE_TARGET_RANGE;
    public static final ForgeConfigSpec.ConfigValue<Integer> VEHICLE_PATH_REPLAN_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue VEHICLE_NAVIGATION_MIN_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue HELICOPTER_HOVER_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue HELICOPTER_COMBAT_DISTANCE;
    public static final ForgeConfigSpec.IntValue SMOKE_ACQUIRING_TICKS;
    public static final ForgeConfigSpec.DoubleValue SMOKE_HEAVY_HIT_RATIO;
    public static final ForgeConfigSpec.DoubleValue SMOKE_LOW_HEALTH_RATIO;
    public static final ForgeConfigSpec.DoubleValue VINDICATOR_RAID_VEHICLE_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<Integer> VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL;
    public static final ForgeConfigSpec.IntValue VINDICATOR_RAID_MIN_WAVE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> VINDICATOR_RAID_VEHICLE_TYPES;
    public static final ForgeConfigSpec.DoubleValue VILLAGE_DEFENDER_VEHICLE_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> VILLAGE_DEFENDER_VEHICLE_TYPES;
    public static final ForgeConfigSpec.DoubleValue PATROL_VEHICLE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue NATURAL_VEHICLE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue GENERATED_VEHICLE_EXTRA_PASSENGER_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<Boolean> VEHICLE_AI_DEBUG;

    static {
        BUILDER.push("Vehicle AI");
        ENABLE_VEHICLE_AI = BUILDER.comment("Whether tagged mobs receive vehicle AI when spawned or loaded. Runtime changes do not reconfigure already loaded mobs; entity reload or server restart uses the current setting.")
                .define("Enable Vehicle AI", true);
        VEHICLE_AI_NEEDS_FUEL = BUILDER.comment("Whether vehicle AI must satisfy vehicle fuel or energy requirements when driving.")
                .define("Vehicle AI Needs Fuel", false);
        VEHICLE_AI_NEEDS_AMMO = BUILDER.comment("Whether vehicle AI must consume or provide ammo when firing vehicle weapons.")
                .define("Vehicle AI Needs Ammo", false);
        VEHICLE_WEAPON_INACCURACY_MULTIPLIER = BUILDER.comment("""
                Multiplies each weapon's native spread for vehicle AI shots. 1 preserves native spread; 0 removes random spread.
                Values below 1 are more accurate; values above 1 are less accurate. This is not a hit probability and does not affect players or handheld guns.
                When Pillager's Gun is installed, its dynamic inaccuracy event is applied after this multiplier.
                """)
                .defineInRange("Vehicle Weapon Inaccuracy Multiplier", 1.3D, 0.0D, 100.0D);
        STRICT_PLAYER_ALLIANCE = BUILDER.comment("Whether Guard Villagers and Recruits must be owned, allied, or friendly by reputation to share vehicles with players. Disable for lenient neutral-friendly behavior.")
                .define("Strict Player Alliance", true);
        FRIENDLY_TAG = BUILDER.comment("Entity type tag IDs whose members are treated as allies for vehicle ownership and firing checks.")
                .defineList("Friendly Tag", ArrayList::new, element -> element instanceof String);
        VEHICLE_TARGET_RANGE = BUILDER.comment("Target acquisition follow range sampled when entering a controllable vehicle, refreshed on remount, and removed on dismount.")
                .defineInRange("Vehicle Target Range", 64.0D, 1.0D, 256.0D);
        VEHICLE_PATH_REPLAN_INTERVAL = BUILDER.comment("Ticks between normal vehicle path replans.")
                .defineInRange("Vehicle Path Replan Interval", 20, 1, 200);
        VEHICLE_NAVIGATION_MIN_DISTANCE = BUILDER.comment("Minimum non-combat vanilla navigation distance required before vehicle AI drives a mounted vehicle.")
                .defineInRange("Vehicle Navigation Min Distance", 8.0D, 0.0D, 256.0D);
        HELICOPTER_HOVER_HEIGHT = BUILDER.comment("Preferred helicopter AI hover height above local ground.")
                .defineInRange("Helicopter Hover Height", 18.0D, 6.0D, 64.0D);
        HELICOPTER_COMBAT_DISTANCE = BUILDER.comment("Preferred horizontal distance between helicopter AI and its target.")
                .defineInRange("Helicopter Combat Distance", 42.0D, 12.0D, 160.0D);
        SMOKE_ACQUIRING_TICKS = BUILDER.comment("Continuous fresh lock-attempt ticks from the same source before an AI driver requests smoke.")
                .defineInRange("Smoke Acquiring Ticks", 20, 1, 200);
        SMOKE_HEAVY_HIT_RATIO = BUILDER.comment("Minimum actual vehicle health loss as a fraction of maximum health for enemy-hit smoke deployment.")
                .defineInRange("Smoke Heavy Hit Ratio", 0.15D, 0.01D, 1.0D);
        SMOKE_LOW_HEALTH_RATIO = BUILDER.comment("Maximum remaining vehicle health fraction for smoke on a new enemy hit. Low health alone does not trigger smoke.")
                .defineInRange("Smoke Low Health Ratio", 0.40D, 0.0D, 1.0D);
        VINDICATOR_RAID_VEHICLE_CHANCE = BUILDER.comment("Chance for a raid member in vindicators_rumble:vehicle_ai_users to spawn a nearby vehicle.")
                .defineInRange("Vindicator Raid Vehicle Chance", 0.05D, 0.0D, 1.0D);
        VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL = BUILDER.comment("Minimum raid Bad Omen level required before tagged raid members can spawn vehicles.")
                .defineInRange("Vindicator Raid Minimum Bad Omen Level", 4, 0, 5);
        VINDICATOR_RAID_MIN_WAVE = BUILDER.comment("First raid wave whose tagged members may spawn vehicles. Waves start at 1; Bad Omen and vehicle chance requirements still apply.")
                .defineInRange("Vindicator Raid Minimum Wave", 2, 1, Integer.MAX_VALUE);
        VINDICATOR_RAID_VEHICLE_TYPES = BUILDER.comment("""
                Vehicle entity type IDs or Limitless Vehicle pack IDs and their weights for generated vehicles owned by mobs implementing Minecraft's Enemy interface.
                Used by every generation rule, including raids, patrols, and ordinary spawns. Non-Enemy mobs use Village Defender Vehicle Types.
                Format: "superbwarfare:sodayo_pick_up_hmg, 5" means this vehicle has a spawn weight of 5.
                """).defineList("Vindicator Raid Vehicle Types", List.of(
                        "superbwarfare:sodayo_pick_up_hmg, 9",
                        "superbwarfare:sodayo_pick_up_tow, 1"
                ), CommonConfig::isWeightedResourceLocation);
        VILLAGE_DEFENDER_VEHICLE_CHANCE = BUILDER.comment("Chance for Guard Villagers or Recruits generated near villages to spawn a nearby vehicle.")
                .defineInRange("Village Defender Vehicle Chance", 0.0D, 0.0D, 1.0D);
        VILLAGE_DEFENDER_VEHICLE_TYPES = BUILDER.comment("""
                Vehicle entity type IDs or Limitless Vehicle pack IDs and their weights for generated vehicles owned by mobs not implementing Minecraft's Enemy interface.
                Used by every generation rule, including ordinary spawns of Guard Villagers, Recruits, and other tagged non-Enemy mobs.
                Format: "superbwarfare:sodayo_pick_up_hmg, 5" means this vehicle has a spawn weight of 5.
                """).defineList("Village Defender Vehicle Types", List.of(
                        "superbwarfare:sodayo_pick_up_hmg, 9",
                        "superbwarfare:sodayo_pick_up_tow, 1"
                ), CommonConfig::isWeightedResourceLocation);
        PATROL_VEHICLE_CHANCE = BUILDER.comment("""
                Chance per newly spawned illager patrol member to generate a vehicle; raid members are excluded. Vehicle pool is selected by the Enemy interface.
                Patrol pillagers outside vehicle_ai_users are replaced with tagged Vindicators only when a vehicle is generated.
                """)
                .defineInRange("Patrol Vehicle Chance", 0.02D, 0.0D, 1.0D);
        NATURAL_VEHICLE_CHANCE = BUILDER.comment("""
                Chance per newly generated mob in vindicators_rumble:vehicle_ai_users to generate a nearby vehicle.
                Includes eggs and commands, but not saved entities loaded from disk.
                Uses Vindicator Raid Vehicle Types for Enemy mobs, and Village Defender Vehicle Types for non-Enemy mobs.
                Raid, patrol, and village defender rules take priority; those entities do not roll again against this chance.
                """)
                .defineInRange("Natural Vehicle Chance", 0.0D, 0.0D, 1.0D);
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
