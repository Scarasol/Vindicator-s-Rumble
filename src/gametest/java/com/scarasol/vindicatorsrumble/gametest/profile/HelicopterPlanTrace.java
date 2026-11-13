package com.scarasol.vindicatorsrumble.gametest.profile;

import java.util.HashMap;
import java.util.Map;

public final class HelicopterPlanTrace {
    private static final Map<Integer, String> PLANS = new HashMap<>();

    private HelicopterPlanTrace() {
    }

    public static void record(int vehicleId, Object state, Object plan) {
        PLANS.put(vehicleId, plan + " aircraft=" + state);
    }

    public static String take(int vehicleId) {
        String plan = PLANS.remove(vehicleId);
        return plan == null ? "" : " plan=" + plan;
    }
}
