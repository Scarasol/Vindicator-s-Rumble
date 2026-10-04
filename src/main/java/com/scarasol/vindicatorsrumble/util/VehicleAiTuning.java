package com.scarasol.vindicatorsrumble.util;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.init.ModTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class VehicleAiTuning {
    private static final double CHASE_START_FOLLOW_RANGE_RATIO = 2.0D / 3.0D;
    private static final double CHASE_STOP_FOLLOW_RANGE_RATIO = 1.0D / 3.0D;
    private static final double MIN_CHASE_DISTANCE = 4.0D;
    private static final double MIN_EFFECTIVE_FOLLOW_RANGE = 1.0E-4D;

    private VehicleAiTuning() {
    }

    public static double navigationArrivalDistance(VehicleController controller, double requestedDistance) {
        return controller.usesGroundNavigation() && requestedDistance > 0.0D
                ? Math.max(requestedDistance, controller.horizontalFootprintRadius() + 1.0D)
                : requestedDistance;
    }

    public static double chaseDistance(Mob mob) {
        return chaseStopDistance(mob);
    }

    public static double chaseStartDistance(Mob mob) {
        return chaseBandDistance(mob, CHASE_START_FOLLOW_RANGE_RATIO);
    }

    public static double chaseStopDistance(Mob mob) {
        return chaseBandDistance(mob, CHASE_STOP_FOLLOW_RANGE_RATIO);
    }

    public static boolean shouldStartChasing(Mob mob, double distanceSqr) {
        double distance = chaseStartDistance(mob);
        return distanceSqr > distance * distance;
    }

    public static boolean shouldContinueChasing(Mob mob, double distanceSqr) {
        double distance = chaseStopDistance(mob);
        return distanceSqr > distance * distance;
    }

    public static double followRange(Mob mob) {
        return mob.getAttributeValue(Attributes.FOLLOW_RANGE);
    }

    public static boolean canAcquireTargets(Mob mob) {
        return followRange(mob) > MIN_EFFECTIVE_FOLLOW_RANGE;
    }

    public static boolean isWithinFollowRange(Mob mob, LivingEntity target) {
        double range = followRange(mob);
        return range > MIN_EFFECTIVE_FOLLOW_RANGE && targetDistanceToSqr(mob, target) <= range * range;
    }

    public static double targetDistanceToSqr(Mob mob, Entity target) {
        if (!usesHorizontalTargetRange(mob)) {
            return mob.distanceToSqr(target);
        }
        return horizontalDistanceToSqr(mob, target.getX(), target.getZ());
    }

    public static double horizontalDistanceToSqr(Entity source, double targetX, double targetZ) {
        double dx = source.getX() - targetX;
        double dz = source.getZ() - targetZ;
        return dx * dx + dz * dz;
    }

    public static boolean usesHorizontalTargetRange(Mob mob) {
        return mob.isPassenger()
                && mob.getType().is(ModTags.EntityTypes.VEHICLE_AI_USERS)
                && VehicleControllers.forMob(mob)
                .map(controller -> controller.supportsFlightControl())
                .orElse(false);
    }

    public static boolean isValidCombatTarget(Mob mob, LivingEntity target) {
        return target != null
                && target.isAlive()
                && mob.canAttack(target)
                && mob.canAttackType(target.getType())
                && !AllegianceUtil.isSameSide(mob, target)
                && isWithinFollowRange(mob, target);
    }

    public static boolean isValidVehicleDriveTarget(Mob mob, LivingEntity target) {
        return isValidCombatTarget(mob, target);
    }

    public static boolean isValidVehicleCombatTarget(Mob mob, LivingEntity target) {
        return isValidCombatTarget(mob, target);
    }

    private static double chaseBandDistance(Mob mob, double ratio) {
        double followRange = followRange(mob);
        if (followRange <= MIN_EFFECTIVE_FOLLOW_RANGE) {
            return MIN_CHASE_DISTANCE;
        }
        return Mth.clamp(followRange * ratio, MIN_CHASE_DISTANCE, followRange);
    }
}
