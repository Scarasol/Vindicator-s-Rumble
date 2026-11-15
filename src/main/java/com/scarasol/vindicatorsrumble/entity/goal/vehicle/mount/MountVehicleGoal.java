package com.scarasol.vindicatorsrumble.entity.goal.vehicle.mount;

import com.scarasol.vindicatorsrumble.api.vehicle.SeatHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.common.VehicleAiGoal;
import com.scarasol.vindicatorsrumble.util.AllegianceUtil;
import com.scarasol.vindicatorsrumble.util.VehicleAiLogger;
import com.scarasol.vindicatorsrumble.util.VehicleAiMemory;
import com.scarasol.vindicatorsrumble.util.VehicleClaimManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public class MountVehicleGoal extends Goal implements VehicleAiGoal {
    private static final double SEARCH_RANGE = 48.0D;
    private static final double BOARDING_REACH = 4.0D;
    private static final double APPROACH_OFFSET = 1.5D;
    private static final double MIN_APPROACH_RADIUS = 2.0D;
    private static final double MIN_DIRECTION_VECTOR_SQR = 1.0E-4D;
    private static final int SEARCH_RETRY_TICKS = 20;

    private final Mob mob;
    private VehicleController targetController;
    private SeatHandle targetSeat;
    private State state = State.SEARCH;
    private long nextSearchTick = Long.MIN_VALUE;
    private Entity searchedVehicle;
    private boolean switchingSeat;

    public MountVehicleGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!VehicleAiMemory.isVehicleAiEnabled(this.mob)) {
            return false;
        }
        Entity vehicle = this.mob.getVehicle();
        if (vehicle != this.searchedVehicle) {
            this.searchedVehicle = vehicle;
            this.nextSearchTick = Long.MIN_VALUE;
        }
        long now = this.mob.level().getGameTime();
        if (now < this.nextSearchTick) {
            return false;
        }
        boolean found = this.mob.isPassenger() ? this.searchSeatSwitch() : this.search();
        if (!found) {
            this.nextSearchTick = now + SEARCH_RETRY_TICKS + Math.floorMod(this.mob.getId(), SEARCH_RETRY_TICKS + 1);
        }
        return found;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.targetController == null || !this.targetController.isValid() || this.state == State.COOLDOWN) {
            return false;
        }
        if (!this.mob.isPassenger() && !this.isApproachableForMount(this.targetController)) {
            return false;
        }
        if (this.mob.isPassenger()) {
            return this.mob.getVehicle() == this.targetController.entity()
                    && (this.state == State.ASSIGN_SEAT || this.state == State.VERIFY);
        }
        return this.state == State.CLAIM || this.state == State.APPROACH || this.state == State.BOARD;
    }

    @Override
    public void start() {
        if (this.switchingSeat) {
            this.state = State.ASSIGN_SEAT;
            VehicleAiLogger.debug(this.mob, "Seat switch started for vehicle {} seat {}", this.targetController.entity().getId(), this.targetSeat.id());
        } else {
            this.state = State.CLAIM;
            VehicleAiLogger.debug(this.mob, "Mount goal started for vehicle {} seat {}", this.targetController.entity().getId(), this.targetSeat.id());
        }
    }

    @Override
    public void stop() {
        if (this.targetController != null && this.targetSeat != null) {
            VehicleClaimManager.release(this.mob, this.targetController.entity(), this.targetSeat.id());
        }
        this.targetController = null;
        this.targetSeat = null;
        this.switchingSeat = false;
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.targetController == null || this.targetSeat == null) {
            this.fail();
            return;
        }

        switch (this.state) {
            case CLAIM -> {
                if (VehicleClaimManager.claim(this.mob, this.targetController.entity(), this.targetSeat.id())) {
                    this.state = State.APPROACH;
                } else {
                    this.fail();
                }
            }
            case APPROACH -> {
                this.mob.getLookControl().setLookAt(this.targetController.entity(), 30.0F, 30.0F);
                if (this.isWithinBoardingRange(this.targetController)) {
                    this.mob.getNavigation().stop();
                    this.state = State.BOARD;
                } else {
                    Vec3 approach = this.approachPosition(this.targetController);
                    this.mob.getNavigation().moveTo(approach.x, approach.y, approach.z, 1.15D);
                }
            }
            case BOARD -> {
                if (this.targetController.mount(this.mob)) {
                    VehicleAiLogger.debug(this.mob, "Mounted vehicle {}", this.targetController.entity().getId());
                    this.state = State.ASSIGN_SEAT;
                } else {
                    this.fail();
                }
            }
            case ASSIGN_SEAT -> {
                Optional<SeatHandle> refreshed = this.refreshTargetSeat();
                if (refreshed.isPresent()) {
                    if (this.targetSeat.id() != refreshed.get().id()) {
                        VehicleClaimManager.release(this.mob, this.targetController.entity(), this.targetSeat.id());
                        if (!VehicleClaimManager.claim(this.mob, this.targetController.entity(), refreshed.get().id())) {
                            this.fail();
                            return;
                        }
                    }
                    this.targetSeat = refreshed.get();
                }
                if (this.targetController.moveToSeat(this.mob, this.targetSeat)) {
                    VehicleAiLogger.debug(this.mob, "Assigned vehicle seat {}", this.targetSeat.id());
                    this.state = State.VERIFY;
                } else {
                    this.fail();
                }
            }
            case VERIFY -> {
                Optional<SeatHandle> seat = this.targetController.seatOf(this.mob);
                if (seat.isPresent() && seat.get().id() == this.targetSeat.id()) {
                    VehicleAiLogger.debug(this.mob, "Verified vehicle seat {}", this.targetSeat.id());
                    VehicleClaimManager.release(this.mob, this.targetController.entity(), this.targetSeat.id());
                    this.targetController = null;
                    this.targetSeat = null;
                } else {
                    this.fail();
                }
            }
            case COOLDOWN, SEARCH -> {
            }
        }
    }

    private boolean search() {
        List<VehicleController> controllers = VehicleControllers.find(this.mob.level(), this.mob.getBoundingBox().inflate(SEARCH_RANGE));
        return controllers.stream()
                .filter(VehicleController::isValid)
                .filter(controller -> !controller.isWreck())
                .filter(VehicleController::isControllable)
                .filter(this::isApproachableForMount)
                .filter(controller -> !AllegianceUtil.hasNonFriendlyPassenger(this.mob, controller.passengers()))
                .flatMap(controller -> this.seatCandidates(controller).stream())
                .min(Comparator.comparingInt(Candidate::priority)
                        .thenComparingDouble(candidate -> this.distanceToBoardingRangeSqr(candidate.controller()))
                        .thenComparingInt(candidate -> candidate.seat().id()))
                .map(candidate -> {
                    this.targetController = candidate.controller;
                    this.targetSeat = candidate.seat;
                    VehicleAiLogger.debug(this.mob, "Found vehicle {} seat {} priority {}", candidate.controller.entity().getId(), candidate.seat.id(), candidate.priority());
                    return true;
                })
                .orElse(false);
    }

    private boolean isApproachableForMount(VehicleController controller) {
        return !controller.supportsFlightControl() || controller.entity().onGround();
    }

    private boolean searchSeatSwitch() {
        Optional<VehicleController> current = VehicleControllers.forMob(this.mob);
        if (current.isEmpty()
                || !current.get().isValid()
                || current.get().isWreck()
                || !current.get().isControllable()
                || AllegianceUtil.hasNonFriendlyPassenger(this.mob, current.get().passengers())) {
            return false;
        }

        Optional<SeatHandle> currentSeat = current.get().seatOf(this.mob);
        Optional<SeatHandle> desiredSeat = this.chooseSeat(current.get());
        if (currentSeat.isEmpty()
                || desiredSeat.isEmpty()
                || currentSeat.get().id() == desiredSeat.get().id()) {
            return false;
        }
        if (!VehicleClaimManager.claim(this.mob, current.get().entity(), desiredSeat.get().id())) {
            return false;
        }

        this.targetController = current.get();
        this.targetSeat = desiredSeat.get();
        this.switchingSeat = true;
        VehicleAiLogger.debug(this.mob,
                "Found better vehicle seat {} -> {} on vehicle {}",
                currentSeat.get().id(),
                desiredSeat.get().id(),
                current.get().entity().getId());
        return true;
    }

    private Optional<SeatHandle> chooseSeat(VehicleController controller) {
        return this.seatCandidates(controller).stream()
                .min(Comparator.comparingInt(Candidate::priority)
                        .thenComparingInt(candidate -> candidate.seat().id()))
                .map(Candidate::seat);
    }

    private List<Candidate> seatCandidates(VehicleController controller) {
        List<SeatHandle> seats = controller.seats();
        boolean hasDriverSeat = seats.stream().anyMatch(SeatHandle::canDrive);
        boolean hasOccupiedDriver = seats.stream()
                .filter(SeatHandle::canDrive)
                .flatMap(seat -> seat.occupant().stream())
                .anyMatch(occupant -> occupant != this.mob);

        List<Candidate> candidates = new ArrayList<>();
        if (hasOccupiedDriver || !hasDriverSeat) {
            for (SeatHandle seat : seats) {
                if (seat.hasWeapon() && this.isSeatAvailableToSelf(controller, seat)) {
                    candidates.add(new Candidate(controller, seat, 0));
                }
            }
        }

        for (SeatHandle seat : seats) {
            if (seat.canDrive() && this.isSeatAvailableToSelf(controller, seat)) {
                candidates.add(new Candidate(controller, seat, 1));
            }
        }

        for (SeatHandle seat : seats) {
            if (!seat.canDrive() && !seat.hasWeapon() && this.isSeatAvailableToSelf(controller, seat)) {
                candidates.add(new Candidate(controller, seat, 2));
            }
        }

        return candidates;
    }

    private Optional<SeatHandle> refreshTargetSeat() {
        if (this.targetController == null || this.targetSeat == null) {
            return Optional.empty();
        }

        Optional<SeatHandle> currentTarget = this.targetController.seats().stream()
                .filter(seat -> seat.id() == this.targetSeat.id())
                .findFirst();
        if (currentTarget.isPresent() && this.isSeatAvailableToSelf(this.targetController, currentTarget.get())) {
            return currentTarget;
        }

        return this.chooseSeat(this.targetController);
    }

    private boolean isSeatAvailableToSelf(VehicleController controller, SeatHandle seat) {
        return seat.occupant().map(occupant -> occupant == this.mob).orElse(true)
                && !VehicleClaimManager.isClaimedByOther(this.mob, controller.entity(), seat.id());
    }

    private boolean isWithinBoardingRange(VehicleController controller) {
        return this.mob.position().distanceToSqr(controller.position()) <= this.boardingRangeSqr(controller);
    }

    private double distanceToBoardingRangeSqr(VehicleController controller) {
        double distance = this.mob.position().distanceTo(controller.position()) - this.boardingRange(controller);
        double outside = Math.max(0.0D, distance);
        return outside * outside;
    }

    private double boardingRangeSqr(VehicleController controller) {
        double range = this.boardingRange(controller);
        return range * range;
    }

    private double boardingRange(VehicleController controller) {
        return this.footprintRadius(controller) + BOARDING_REACH;
    }

    private double footprintRadius(VehicleController controller) {
        return Math.max(controller.pathingDimensions().width(), controller.pathingDimensions().length()) * 0.5D;
    }

    private Vec3 approachPosition(VehicleController controller) {
        Vec3 center = controller.position();
        Vec3 mobPosition = this.mob.position();
        double dx = mobPosition.x - center.x;
        double dz = mobPosition.z - center.z;
        double lengthSqr = dx * dx + dz * dz;
        if (lengthSqr < MIN_DIRECTION_VECTOR_SQR) {
            dx = -Math.sin(Math.toRadians(controller.yaw()));
            dz = Math.cos(Math.toRadians(controller.yaw()));
            lengthSqr = dx * dx + dz * dz;
        }

        double length = Math.sqrt(lengthSqr);
        double radius = Math.max(MIN_APPROACH_RADIUS, this.footprintRadius(controller) + APPROACH_OFFSET);
        double x = center.x + dx / length * radius;
        double z = center.z + dz / length * radius;
        return new Vec3(x, center.y, z);
    }

    private void fail() {
        VehicleAiLogger.debug(this.mob, "Mount goal failed in state {}", this.state);
        this.nextSearchTick = this.mob.level().getGameTime() + 40L;
        this.state = State.COOLDOWN;
        this.mob.getNavigation().stop();
    }

    private enum State {
        SEARCH,
        CLAIM,
        APPROACH,
        BOARD,
        ASSIGN_SEAT,
        VERIFY,
        COOLDOWN
    }

    private record Candidate(VehicleController controller, SeatHandle seat, int priority) {
    }
}
