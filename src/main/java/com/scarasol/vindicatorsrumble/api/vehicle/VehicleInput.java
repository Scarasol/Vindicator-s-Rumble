package com.scarasol.vindicatorsrumble.api.vehicle;

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
