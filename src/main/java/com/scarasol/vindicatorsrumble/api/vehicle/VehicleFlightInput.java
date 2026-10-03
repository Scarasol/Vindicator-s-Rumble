package com.scarasol.vindicatorsrumble.api.vehicle;

public record VehicleFlightInput(
        VehicleInput input,
        double mouseX,
        double mouseY
) {
    public static final VehicleFlightInput NONE = new VehicleFlightInput(VehicleInput.NONE, 0.0D, 0.0D);
}
