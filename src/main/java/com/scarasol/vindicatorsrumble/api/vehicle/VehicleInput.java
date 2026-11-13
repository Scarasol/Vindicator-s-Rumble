package com.scarasol.vindicatorsrumble.api.vehicle;

/**
 * Ground steering follows the requested gear: left/right request negative/positive yaw
 * unless back is set, which reverses that response. Adapters account for gear-change lag.
 */
public record VehicleInput(
        boolean forward,
        boolean back,
        boolean left,
        boolean right,
        boolean brake,
        boolean sprint,
        boolean up,
        boolean down,
        boolean fire,
        boolean decoy
) {
    public static final VehicleInput NONE = new VehicleInput(false, false, false, false, false, false, false, false, false, false);
    public static final VehicleInput BRAKE = new VehicleInput(false, false, false, false, true, false, false, false, false, false);
}
