package com.scarasol.vindicatorsrumble.api.vehicle;

// rollTarget is in degrees, positive to the right; NaN leaves bank control to the key inputs.
public record VehicleFlightInput(
        VehicleInput input,
        double mouseX,
        double mouseY,
        double rollTarget
) {
    public VehicleFlightInput(VehicleInput input, double mouseX, double mouseY) {
        this(input, mouseX, mouseY, Double.NaN);
    }

    public static final VehicleFlightInput NONE = new VehicleFlightInput(VehicleInput.NONE, 0.0D, 0.0D);
}
