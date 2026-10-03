package com.scarasol.vindicatorsrumble.util;

import com.scarasol.vindicatorsrumble.config.CommonConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

public class VehicleTargetRangeManager {
    private static final UUID VEHICLE_TARGET_RANGE_MODIFIER_ID = UUID.fromString("29c5760f-7d21-4ac4-9a79-a7fcb2f8a621");
    private static final String VEHICLE_TARGET_RANGE_MODIFIER_NAME = "VindicatorsRumble vehicle target range";
    private static final double VANILLA_TARGET_SEARCH_HEIGHT = 4.0D;

    private VehicleTargetRangeManager() {
    }

    public static void apply(Mob mob) {
        AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange == null) {
            return;
        }

        followRange.removeModifier(VEHICLE_TARGET_RANGE_MODIFIER_ID);
        double targetRange = CommonConfig.VEHICLE_TARGET_RANGE.get();
        double nominalRange = nominalRangeBeforeTotalModifiers(followRange);
        double currentRange = followRange.getValue();
        double bonus = targetRange - nominalRange;
        if (bonus <= 0.0D) {
            VehicleAiLogger.debug(mob,
                    "Vehicle target range unchanged effective={} nominal={} configured={}",
                    currentRange,
                    nominalRange,
                    targetRange);
            return;
        }

        followRange.addTransientModifier(new AttributeModifier(
                VEHICLE_TARGET_RANGE_MODIFIER_ID,
                VEHICLE_TARGET_RANGE_MODIFIER_NAME,
                bonus,
                AttributeModifier.Operation.ADDITION
        ));
        VehicleAiLogger.debug(mob,
                "Applied vehicle target range effectiveBefore={} effectiveAfter={} nominal={} configured={} bonus={}",
                currentRange,
                followRange.getValue(),
                nominalRange,
                targetRange,
                bonus);
    }

    public static void remove(Mob mob) {
        AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange == null) {
            return;
        }
        if (followRange.getModifier(VEHICLE_TARGET_RANGE_MODIFIER_ID) != null) {
            followRange.removeModifier(VEHICLE_TARGET_RANGE_MODIFIER_ID);
            VehicleAiLogger.debug(mob, "Removed vehicle target range modifier; current={}", followRange.getValue());
        }
    }

    public static boolean hasVehicleTargetRange(Mob mob) {
        AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        return followRange != null && followRange.getModifier(VEHICLE_TARGET_RANGE_MODIFIER_ID) != null;
    }

    public static double targetSearchHeight(Mob mob, double vanillaHorizontalRange, boolean flightVehiclePassenger) {
        if (!mob.isPassenger()) {
            return VANILLA_TARGET_SEARCH_HEIGHT;
        }
        if (hasVehicleTargetRange(mob) || flightVehiclePassenger) {
            return Math.max(VANILLA_TARGET_SEARCH_HEIGHT,
                    Math.max(VehicleAiTuning.followRange(mob), vanillaHorizontalRange));
        }
        return VANILLA_TARGET_SEARCH_HEIGHT;
    }

    private static double nominalRangeBeforeTotalModifiers(AttributeInstance followRange) {
        double base = followRange.getBaseValue();
        for (AttributeModifier modifier : followRange.getModifiers(AttributeModifier.Operation.ADDITION)) {
            if (VEHICLE_TARGET_RANGE_MODIFIER_ID.equals(modifier.getId())) {
                continue;
            }
            base += modifier.getAmount();
        }

        double result = base;
        for (AttributeModifier modifier : followRange.getModifiers(AttributeModifier.Operation.MULTIPLY_BASE)) {
            if (VEHICLE_TARGET_RANGE_MODIFIER_ID.equals(modifier.getId())) {
                continue;
            }
            result += base * modifier.getAmount();
        }
        return Math.max(0.0D, result);
    }
}
