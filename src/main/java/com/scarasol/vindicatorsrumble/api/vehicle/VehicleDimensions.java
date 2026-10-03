package com.scarasol.vindicatorsrumble.api.vehicle;

public record VehicleDimensions(double width, double height, double length) {
    public static final VehicleDimensions DEFAULT = new VehicleDimensions(2.0D, 2.0D, 2.0D);

    public double conservativeHorizontalSize() {
        return Math.sqrt(this.width * this.width + this.length * this.length);
    }
}
