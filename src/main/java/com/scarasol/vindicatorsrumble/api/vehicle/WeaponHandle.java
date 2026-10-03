package com.scarasol.vindicatorsrumble.api.vehicle;

public record WeaponHandle(
        int id,
        String name,
        double minRange,
        double maxRange,
        int priority,
        WeaponAimMode aimMode,
        WeaponPayloadType payloadType,
        WeaponFireMode fireMode,
        double rpm,
        double actionCycleTicks
) {
    private static final double TICKS_PER_MINUTE = 1200.0D;
    private static final double SEMI_AUTO_MIN_DIVISOR = 2400.0D;
    private static final double SEMI_AUTO_DISTANCE_DIVISOR = 1200.0D;
    private static final double SEMI_AUTO_DISTANCE_REFERENCE = 8.0D;

    public WeaponHandle {
        if (aimMode == null) {
            aimMode = WeaponAimMode.INDEPENDENT;
        }
        if (payloadType == null) {
            payloadType = WeaponPayloadType.UNKNOWN;
        }
        if (fireMode == null) {
            fireMode = WeaponFireMode.UNKNOWN;
        }
        rpm = Math.max(1.0D, rpm);
        actionCycleTicks = Math.max(0.0D, actionCycleTicks);
    }

    public WeaponHandle(int id, String name, double minRange, double maxRange, int priority) {
        this(id, name, minRange, maxRange, priority, WeaponAimMode.INDEPENDENT);
    }

    public WeaponHandle(int id, String name, double minRange, double maxRange, int priority, WeaponAimMode aimMode) {
        this(id, name, minRange, maxRange, priority, aimMode, WeaponPayloadType.UNKNOWN);
    }

    public WeaponHandle(int id, String name, double minRange, double maxRange, int priority, WeaponAimMode aimMode, WeaponPayloadType payloadType) {
        this(id, name, minRange, maxRange, priority, aimMode, payloadType, WeaponFireMode.AUTOMATIC, TICKS_PER_MINUTE, 0.0D);
    }

    public boolean requiresVehicleAim() {
        return this.aimMode == WeaponAimMode.VEHICLE_FIXED;
    }

    public double fireStep(double distance, boolean stunned) {
        double baseStep = this.fireMode == WeaponFireMode.SEMI_AUTOMATIC || this.fireMode == WeaponFireMode.BURST
                ? this.rpm / this.semiAutoDivisor(distance, stunned)
                : this.rpm / TICKS_PER_MINUTE;
        if (this.actionCycleTicks <= 0.0D) {
            return baseStep;
        }
        return Math.min(baseStep, 1.0D / this.actionCycleTicks);
    }

    private double semiAutoDivisor(double distance, boolean stunned) {
        if (stunned) {
            return SEMI_AUTO_MIN_DIVISOR;
        }
        double distanceDivisor = SEMI_AUTO_DISTANCE_DIVISOR * (Math.max(0.0D, distance) / SEMI_AUTO_DISTANCE_REFERENCE);
        return Math.max(distanceDivisor, SEMI_AUTO_MIN_DIVISOR);
    }
}
