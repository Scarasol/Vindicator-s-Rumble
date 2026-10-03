package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground;

record GroundDirectionSample(double center, double left, double right) {
    double min() {
        return Math.min(this.center, Math.min(this.left, this.right));
    }
}
