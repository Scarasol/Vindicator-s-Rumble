package com.scarasol.vindicatorsrumble.mixin;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathNavigationMixinTest {
    @Test
    void externalPathCreationIsBlockedDuringGroundCombat() throws Exception {
        assertTrue(shouldBlockExternalPathCreation(
                true,
                false,
                false,
                true,
                true,
                true,
                false));
    }

    @Test
    void vehicleAiScopedPathCreationIsNeverBlockedByOwnershipPolicy() throws Exception {
        assertFalse(shouldBlockExternalPathCreation(
                true,
                true,
                false,
                true,
                true,
                true,
                true));
    }

    @Test
    void shortNonCombatGroundPathCreationIsBlockedBeforePathfinding() throws Exception {
        assertTrue(shouldBlockExternalPathCreation(
                true,
                false,
                false,
                true,
                true,
                false,
                true));
    }

    @Test
    void externalFlightNavigationIsBlocked() throws Exception {
        assertTrue(shouldBlockExternalGroundNavigation(
                false,
                true,
                false,
                false,
                false,
                false));
    }

    @Test
    void nonDrivingPassengerDoesNotBlockGroundNavigation() throws Exception {
        assertFalse(shouldBlockExternalGroundNavigation(
                false,
                false,
                false,
                true,
                true,
                true));
    }

    private static boolean shouldBlockExternalPathCreation(boolean hasTargets, boolean vehicleAiPathing,
                                                           boolean supportsFlightControl, boolean canDrive,
                                                           boolean usesGroundNavigation, boolean hasCombatTarget,
                                                           boolean allTargetsShort) throws Exception {
        Method method = PathNavigationMixin.class.getDeclaredMethod(
                "shouldBlockExternalPathCreation",
                boolean.class,
                boolean.class,
                boolean.class,
                boolean.class,
                boolean.class,
                boolean.class,
                boolean.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, hasTargets, vehicleAiPathing, supportsFlightControl, canDrive,
                usesGroundNavigation, hasCombatTarget, allTargetsShort);
    }

    private static boolean shouldBlockExternalGroundNavigation(boolean vehicleAiPathing, boolean supportsFlightControl,
                                                               boolean canDrive, boolean usesGroundNavigation,
                                                               boolean hasCombatTarget,
                                                               boolean shortGroundNavigation) throws Exception {
        Method method = PathNavigationMixin.class.getDeclaredMethod(
                "shouldBlockExternalGroundNavigation",
                boolean.class,
                boolean.class,
                boolean.class,
                boolean.class,
                boolean.class,
                boolean.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, vehicleAiPathing, supportsFlightControl, canDrive,
                usesGroundNavigation, hasCombatTarget, shortGroundNavigation);
    }
}
