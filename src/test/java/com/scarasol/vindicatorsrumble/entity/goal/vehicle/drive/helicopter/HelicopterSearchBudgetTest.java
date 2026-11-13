package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.helicopter;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelicopterSearchBudgetTest {
    @Test
    void fleetWorkIsBoundedAndEveryQueuedAircraftGetsATurn() throws Exception {
        Budget budget = new Budget();
        List<UUID> fleet = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            fleet.add(new UUID(0L, i));
        }
        Set<UUID> served = new HashSet<>();
        for (long tick = 100L; tick < 140L; tick++) {
            int searches = 0;
            for (UUID aircraft : fleet) {
                if (budget.acquire(aircraft, tick)) {
                    searches++;
                    served.add(aircraft);
                }
            }
            assertTrue(searches <= 8, "A fleet must not execute more than eight full searches in one world tick");
        }
        assertTrue(served.containsAll(fleet), "Entity tick order must not starve aircraft at the back of the queue");
    }

    @Test
    void departedAircraftCannotKeepBlockingTheQueue() throws Exception {
        Budget budget = new Budget();
        for (int i = 0; i < 30; i++) {
            budget.acquire(new UUID(0L, i), 100L);
        }
        UUID survivor = new UUID(1L, 0L);
        assertFalse(budget.acquire(survivor, 100L));
        assertTrue(budget.acquire(survivor, 104L), "Abandoned requests must expire without retaining an aircraft");
    }

    @Test
    void clockRollbackStartsANewBudget() throws Exception {
        Budget budget = new Budget();
        for (int i = 0; i < 30; i++) {
            budget.acquire(new UUID(0L, i), 100L);
        }
        assertTrue(budget.acquire(new UUID(1L, 0L), 1L));
    }

    private static final class Budget {
        private final Object instance;
        private final Method acquire;

        Budget() throws Exception {
            Class<?> type = Class.forName(HelicopterVisibilityRecovery.class.getName() + "$SearchBudget");
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            this.instance = constructor.newInstance();
            this.acquire = type.getDeclaredMethod("acquire", UUID.class, long.class);
            this.acquire.setAccessible(true);
        }

        boolean acquire(UUID aircraft, long tick) throws Exception {
            return (boolean) this.acquire.invoke(this.instance, aircraft, tick);
        }
    }
}
