package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground;

record GroundClearance(double front, double back, double left, double right) {
    static GroundClearance open(double range) {
        return new GroundClearance(range, range, range, range);
    }

    int openSideDirection(int fallbackDirection) {
        double difference = this.left - this.right;
        if (difference > 0.75D) {
            return -1;
        }
        if (difference < -0.75D) {
            return 1;
        }
        return fallbackDirection == 0 ? -1 : fallbackDirection;
    }

    boolean frontBlocked(double distance) {
        return this.front < distance;
    }

    boolean backBlocked(double distance) {
        return this.back < distance;
    }

    boolean prefersBack() {
        return this.back > this.front;
    }

    boolean sideSqueezed(double distance) {
        return this.left < distance || this.right < distance;
    }

    int squeezedOpenSide(int fallbackDirection, double distance) {
        if (this.left < distance && this.right > this.left + 0.75D) {
            return 1;
        }
        if (this.right < distance && this.left > this.right + 0.75D) {
            return -1;
        }
        return fallbackDirection == 0 ? this.openSideDirection(0) : fallbackDirection;
    }
}
