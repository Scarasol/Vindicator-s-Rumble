package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleMovementType;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleInput;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.common.VehicleAiGoal;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.ground.GroundDriveStrategy;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.helicopter.HelicopterDriveStrategy;
import com.scarasol.vindicatorsrumble.util.VehicleAiLogger;
import com.scarasol.vindicatorsrumble.util.VehicleSmokeDefense;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.Optional;

public class DriveVehicleGoal extends Goal implements VehicleAiGoal {
    private final Mob mob;
    private final Mode mode;
    private final GroundDriveStrategy groundStrategy;
    private final HelicopterDriveStrategy helicopterStrategy;
    private VehicleController controller;
    private DriveStrategy strategy;
    private boolean smokeOnly;

    public DriveVehicleGoal(Mob mob) {
        this(mob, Mode.COMBAT);
    }

    public DriveVehicleGoal(Mob mob, Mode mode) {
        this.mob = mob;
        this.mode = mode;
        this.groundStrategy = new GroundDriveStrategy(mob, mode);
        this.helicopterStrategy = new HelicopterDriveStrategy(mob, mode);
        if (mode == Mode.COMBAT) {
            this.setFlags(EnumSet.of(Flag.MOVE));
        } else {
            this.setFlags(EnumSet.noneOf(Flag.class));
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (!CommonConfig.ENABLE_VEHICLE_AI.get()) {
            VehicleAiLogger.debugEvery(this.mob, 40, "Drive canUse=false: vehicle AI disabled");
            return false;
        }
        if (!this.mob.isPassenger()) {
            return false;
        }
        Optional<VehicleController> current = VehicleControllers.forMob(this.mob);
        if (current.isEmpty()) {
            VehicleAiLogger.debugEvery(this.mob, 40, "Drive canUse=false: passenger vehicle has no compatible controller");
            return false;
        }
        if (!current.get().canDrive(this.mob)) {
            VehicleAiLogger.debugEvery(this.mob, 40, "Drive canUse=false: seat cannot drive on vehicle {}", current.get().entity().getId());
            return false;
        }
        this.controller = current.get();
        this.strategy = this.strategyFor(this.controller);
        this.smokeOnly = false;
        if (this.strategy.canUse(this.controller, true)) {
            return true;
        }
        this.smokeOnly = this.mode == Mode.NAVIGATION && !this.otherDriverRunning()
                && VehicleSmokeDefense.shouldDeploy(this.mob, this.controller);
        return this.smokeOnly;
    }

    @Override
    public boolean canContinueToUse() {
        if (!CommonConfig.ENABLE_VEHICLE_AI.get() || this.controller == null
                || !this.controller.isValid()
                || !this.controller.canDrive(this.mob)
                || !this.mob.isPassenger()) {
            return false;
        }
        this.strategy = this.strategyFor(this.controller);
        if (this.smokeOnly) {
            return !this.otherDriverRunning() && VehicleSmokeDefense.shouldDeploy(this.mob, this.controller);
        }
        return this.strategy.canContinueToUse(this.controller);
    }

    @Override
    public void start() {
        if (this.strategy == null && this.controller != null) {
            this.strategy = this.strategyFor(this.controller);
        }
        if (!this.smokeOnly && this.strategy != null && this.controller != null) {
            this.strategy.start(this.controller);
            VehicleAiLogger.debug(this.mob, "{} drive goal started for vehicle {} type={} flight={} groundNav={} strategy={}",
                    this.mode,
                    this.controller.entity().getId(),
                    this.controller.movementType(),
                    this.controller.supportsFlightControl(),
                    this.controller.usesGroundNavigation(),
                    this.strategy.name());
        }
    }

    @Override
    public void stop() {
        if (!this.smokeOnly && this.strategy != null && this.controller != null) {
            this.strategy.stop(this.controller);
        }
        if (!this.smokeOnly && this.controller != null) {
            this.controller.stopDriving(this.mob);
            VehicleAiLogger.debug(this.mob, "{} drive goal stopped for vehicle {}", this.mode, this.controller.entity().getId());
        }
        this.controller = null;
        this.strategy = null;
        this.smokeOnly = false;
    }

    @Override
    public void tick() {
        if (this.controller == null || this.strategy == null) {
            return;
        }
        if (this.smokeOnly) {
            if (!this.otherDriverRunning()) {
                this.controller.drive(this.mob, VehicleInput.NONE);
            }
            return;
        }
        this.strategy.tick(this.controller);
    }

    private boolean otherDriverRunning() {
        return this.mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(goal -> goal.isRunning() && goal.getGoal() instanceof DriveVehicleGoal drive
                        && drive != this && !drive.smokeOnly);
    }

    private DriveStrategy strategyFor(VehicleController controller) {
        return this.isHelicopter(controller) ? this.helicopterStrategy : this.groundStrategy;
    }

    private boolean isHelicopter(VehicleController controller) {
        return controller.movementType() == VehicleMovementType.HELICOPTER
                && controller.supportsFlightControl();
    }

    public enum Mode {
        COMBAT,
        NAVIGATION
    }
}
