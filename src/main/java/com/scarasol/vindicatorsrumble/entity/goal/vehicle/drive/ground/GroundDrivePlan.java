package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground;

import com.scarasol.vindicatorsrumble.api.vehicle.DriveIntent;
import com.scarasol.vindicatorsrumble.api.vehicle.DriveRequest;

record GroundDrivePlan(Action action, DriveRequest request, double outerDistance) {
    enum Action {
        HOLD,
        AIM,
        FOLLOW,
        APPROACH,
        RETREAT,
        RAM
    }

    boolean navigation() {
        return this.request.intent() == DriveIntent.MOVE_TO;
    }
}
