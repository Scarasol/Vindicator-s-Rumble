package com.scarasol.vindicatorsrumble.entity.goal.vehicle.leave;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.common.VehicleAiGoal;
import com.scarasol.vindicatorsrumble.util.AllegianceUtil;
import com.scarasol.vindicatorsrumble.util.VehicleAiLogger;
import com.scarasol.vindicatorsrumble.util.VehicleAiMemory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.Optional;

public class LeaveVehicleGoal extends Goal implements VehicleAiGoal {
    private final Mob mob;
    private VehicleController controller;
    private int stopTicks;

    public LeaveVehicleGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!VehicleAiMemory.isVehicleAiEnabled(this.mob) || !this.mob.isPassenger()) {
            return false;
        }
        Optional<VehicleController> current = VehicleControllers.forMob(this.mob);
        if (current.isEmpty()) {
            return false;
        }
        this.controller = current.get();
        return this.controller.isWreck()
                || !this.controller.isValid()
                || AllegianceUtil.hasNonFriendlyPassenger(this.mob, this.controller.passengers());
    }

    @Override
    public boolean canContinueToUse() {
        return this.controller != null && this.mob.isPassenger() && this.stopTicks < 20;
    }

    @Override
    public void start() {
        this.stopTicks = 0;
        VehicleAiLogger.debug(this.mob, "Leave goal started for vehicle {}", this.controller.entity().getId());
    }

    @Override
    public void stop() {
        this.controller = null;
    }

    @Override
    public void tick() {
        if (this.controller == null) {
            return;
        }
        if (this.controller.canDrive(this.mob) && this.stopTicks < 5) {
            this.controller.stopDriving(this.mob);
            this.stopTicks++;
            return;
        }
        if (this.controller.dismount(this.mob)) {
            VehicleAiLogger.debug(this.mob, "Dismounted vehicle {}", this.controller.entity().getId());
            this.stopTicks = 20;
        }
    }
}
