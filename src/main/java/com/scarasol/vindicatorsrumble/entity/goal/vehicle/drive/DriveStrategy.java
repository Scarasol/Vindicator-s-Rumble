package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;

public interface DriveStrategy {
    boolean canUse(VehicleController controller, boolean starting);

    boolean canContinueToUse(VehicleController controller);

    void start(VehicleController controller);

    void stop(VehicleController controller);

    void tick(VehicleController controller);

    String name();
}
