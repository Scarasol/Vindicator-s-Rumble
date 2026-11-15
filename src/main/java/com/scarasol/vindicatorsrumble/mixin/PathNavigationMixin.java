package com.scarasol.vindicatorsrumble.mixin;

import com.scarasol.vindicatorsrumble.api.vehicle.DriveIntent;
import com.scarasol.vindicatorsrumble.api.vehicle.DriveRequest;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.util.VehicleAiLogger;
import com.scarasol.vindicatorsrumble.util.VehicleAiMemory;
import com.scarasol.vindicatorsrumble.util.VehicleAiTuning;
import com.scarasol.vindicatorsrumble.util.VehiclePathContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.Set;

@Mixin(PathNavigation.class)
public abstract class PathNavigationMixin {
    @Shadow
    @Final
    protected Mob mob;

    @Unique
    private VehiclePathContext.Scope vindicatorsRumble$vehiclePathScope;

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true)
    private void vindicatorsRumble$pushVehiclePathDimensions(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange, CallbackInfoReturnable<Path> cir) {
        if (this.vindicatorsRumble$shouldBlockExternalPathCreation(targets)) {
            VehicleAiMemory.clearNavigationDriveRequest(this.mob);
            if (VehicleAiLogger.shouldLog(this.mob, 20)) {
                VehicleAiLogger.debugEvery(this.mob, 20,
                        "Blocked external vehicle path creation targets={} minDistance={} flight={} combatTarget={}",
                        targets.size(),
                        CommonConfig.VEHICLE_NAVIGATION_MIN_DISTANCE.get(),
                        this.vindicatorsRumble$vehicleController().map(VehicleController::supportsFlightControl).orElse(false),
                        this.vindicatorsRumble$hasCombatTarget());
            }
            cir.setReturnValue(null);
            return;
        }
        this.vindicatorsRumble$drivingController().ifPresent(controller ->
                this.vindicatorsRumble$vehiclePathScope = VehiclePathContext.push(this.mob,
                        controller.pathingDimensions(), VehiclePathContext.isNarrowFor(this.mob)));
    }

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("RETURN"))
    private void vindicatorsRumble$popVehiclePathDimensions(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange, CallbackInfoReturnable<Path> cir) {
        if (this.vindicatorsRumble$vehiclePathScope != null) {
            this.vindicatorsRumble$vehiclePathScope.close();
            this.vindicatorsRumble$vehiclePathScope = null;
        }
    }

    @Inject(method = "moveTo(DDDD)Z", at = @At("HEAD"), cancellable = true)
    private void vindicatorsRumble$blockVehicleCoordinateNavigation(double x, double y, double z, double speedModifier, CallbackInfoReturnable<Boolean> cir) {
        this.vindicatorsRumble$blockExternalNavigationTo(new Vec3(x, y, z), "coordinate", speedModifier, cir);
    }

    @Inject(method = "moveTo(Lnet/minecraft/world/entity/Entity;D)Z", at = @At("HEAD"), cancellable = true)
    private void vindicatorsRumble$blockVehicleEntityNavigation(Entity target, double speedModifier, CallbackInfoReturnable<Boolean> cir) {
        if (target != null) {
            this.vindicatorsRumble$blockExternalNavigationTo(target.position(), "entity", speedModifier, cir);
        }
    }

    @Inject(method = "moveTo(Lnet/minecraft/world/level/pathfinder/Path;D)Z", at = @At("HEAD"), cancellable = true)
    private void vindicatorsRumble$submitVehicleNavigation(Path path, double speedModifier, CallbackInfoReturnable<Boolean> cir) {
        if (path == null) {
            return;
        }
        if (this.vindicatorsRumble$isVehicleAiPathing()) {
            return;
        }
        Optional<VehicleController> vehicle = this.vindicatorsRumble$vehicleController();
        if (vehicle.isEmpty()) {
            return;
        }
        VehicleController controller = vehicle.get();
        if (controller.supportsFlightControl()) {
            VehicleAiMemory.clearNavigationDriveRequest(this.mob);
            VehicleAiLogger.debugEvery(this.mob, 20,
                    "Blocked external flight vehicle path moveTo target={} nodes={} speed={}",
                    path.getTarget(),
                    path.getNodeCount(),
                    speedModifier);
            cir.setReturnValue(false);
            return;
        }
        if (!controller.canDrive(this.mob) || !controller.usesGroundNavigation() || this.vindicatorsRumble$hasCombatTarget()) {
            if (this.vindicatorsRumble$hasCombatTarget()) {
                VehicleAiMemory.clearNavigationDriveRequest(this.mob);
                VehicleAiLogger.debugEvery(this.mob, 20,
                        "Blocked external combat vehicle path moveTo target={} nodes={} speed={}",
                        path.getTarget(),
                        path.getNodeCount(),
                        speedModifier);
                cir.setReturnValue(false);
            }
            return;
        }

        Vec3 target = Vec3.atBottomCenterOf(path.getTarget());
        if (this.vindicatorsRumble$isShortGroundNavigation(controller, target)) {
            VehicleAiMemory.clearNavigationDriveRequest(this.mob);
            if (VehicleAiLogger.shouldLog(this.mob, 20)) {
                VehicleAiLogger.debugEvery(this.mob, 20,
                        "Blocked short vehicle path moveTo target={} horizontalDistance={} minDistance={} nodes={} speed={}",
                        path.getTarget(),
                        Math.sqrt(this.vindicatorsRumble$horizontalDistanceSqr(controller.position(), target)),
                        CommonConfig.VEHICLE_NAVIGATION_MIN_DISTANCE.get(),
                        path.getNodeCount(),
                        speedModifier);
            }
            cir.setReturnValue(false);
            return;
        }
        path.setNextNodeIndex(0);
        VehicleAiMemory.setNavigationDriveRequest(this.mob, DriveRequest.moveTo(target, path));
        if (VehicleAiLogger.isDebugEnabled()) {
            VehicleAiLogger.debug(this.mob,
                    "Captured vehicle navigation path target={} targetVec={} vehiclePos={} horizontalDistance={} nodes={} nextIndex={} firstNode={} nextNode={} lastNode={} speed={} dimensions={}",
                    path.getTarget(),
                    target,
                    controller.position(),
                    Math.sqrt(this.vindicatorsRumble$horizontalDistanceSqr(controller.position(), target)),
                    path.getNodeCount(),
                    path.getNextNodeIndex(),
                    this.vindicatorsRumble$nodeSummary(path, 0),
                    this.vindicatorsRumble$nextNodeSummary(path),
                    this.vindicatorsRumble$nodeSummary(path, path.getNodeCount() - 1),
                    speedModifier,
                    controller.pathingDimensions());
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "isDone", at = @At("HEAD"), cancellable = true)
    private void vindicatorsRumble$reportDelegatedNavigation(CallbackInfoReturnable<Boolean> cir) {
        if (VehicleAiTuning.isDelegatingGroundNavigation(this.mob)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void vindicatorsRumble$clearVehicleNavigation(CallbackInfo ci) {
        if (this.mob.isPassenger()
                && VehicleAiMemory.isVehicleAiEnabled(this.mob)) {
            DriveRequest request = VehicleAiMemory.getNavigationDriveRequest(this.mob);
            if (VehicleAiLogger.isDebugEnabled()
                    && request.intent() != DriveIntent.NONE) {
                VehicleAiLogger.debug(this.mob,
                        "Clearing vehicle navigation request due PathNavigation.stop intent={} targetPos={} targetEntity={} pathNodes={}",
                        request.intent(),
                        request.targetPosition().orElse(null),
                        request.targetEntity().map(Entity::getId).orElse(-1),
                        request.path().map(Path::getNodeCount).orElse(0));
            }
            VehicleAiMemory.clearNavigationDriveRequest(this.mob);
        }
    }

    @Unique
    private Optional<VehicleController> vindicatorsRumble$vehicleController() {
        if (!this.mob.isPassenger() || !VehicleAiMemory.isVehicleAiEnabled(this.mob)) {
            return Optional.empty();
        }
        return VehicleControllers.forMob(this.mob).filter(VehicleController::isValid);
    }

    @Unique
    private Optional<VehicleController> vindicatorsRumble$drivingController() {
        Optional<VehicleController> controller = this.vindicatorsRumble$vehicleController();
        if (controller.isEmpty() || !controller.get().canDrive(this.mob) || !controller.get().usesGroundNavigation()) {
            return Optional.empty();
        }
        return controller;
    }

    @Unique
    private boolean vindicatorsRumble$hasCombatTarget() {
        return VehicleAiTuning.isValidCombatTarget(this.mob, this.mob.getTarget());
    }

    @Unique
    private boolean vindicatorsRumble$isVehicleAiPathing() {
        return VehiclePathContext.dimensionsFor(this.mob).isPresent();
    }

    @Unique
    private void vindicatorsRumble$blockExternalNavigationTo(Vec3 target, String source, double speedModifier, CallbackInfoReturnable<Boolean> cir) {
        if (this.vindicatorsRumble$isVehicleAiPathing()) {
            return;
        }
        Optional<VehicleController> vehicle = this.vindicatorsRumble$vehicleController();
        if (vehicle.isEmpty()) {
            return;
        }
        VehicleController controller = vehicle.get();
        if (controller.supportsFlightControl()) {
            VehicleAiMemory.clearNavigationDriveRequest(this.mob);
            VehicleAiLogger.debugEvery(this.mob, 20,
                    "Blocked external flight vehicle {} navigation target={} speed={}",
                    source,
                    target,
                    speedModifier);
            cir.setReturnValue(false);
            return;
        }
        boolean hasCombatTarget = this.vindicatorsRumble$hasCombatTarget();
        if (shouldBlockExternalGroundNavigation(
                false,
                controller.supportsFlightControl(),
                controller.canDrive(this.mob),
                controller.usesGroundNavigation(),
                hasCombatTarget,
                this.vindicatorsRumble$isShortGroundNavigation(controller, target))) {
            VehicleAiMemory.clearNavigationDriveRequest(this.mob);
            if (VehicleAiLogger.shouldLog(this.mob, 20)) {
                String reason = hasCombatTarget ? "combat target" : "short request";
                VehicleAiLogger.debugEvery(this.mob, 20,
                        "Blocked external vehicle {} navigation reason={} target={} horizontalDistance={} minDistance={} speed={}",
                        source,
                        reason,
                        target,
                        Math.sqrt(this.vindicatorsRumble$horizontalDistanceSqr(controller.position(), target)),
                        CommonConfig.VEHICLE_NAVIGATION_MIN_DISTANCE.get(),
                        speedModifier);
            }
            cir.setReturnValue(false);
        }
    }

    @Unique
    private boolean vindicatorsRumble$shouldBlockExternalPathCreation(Set<BlockPos> targets) {
        if (targets.isEmpty() || this.vindicatorsRumble$isVehicleAiPathing()) {
            return false;
        }
        Optional<VehicleController> vehicle = this.vindicatorsRumble$vehicleController();
        if (vehicle.isEmpty()) {
            return false;
        }
        VehicleController controller = vehicle.get();
        boolean allTargetsShort = targets.stream()
                .map(Vec3::atBottomCenterOf)
                .allMatch(target -> this.vindicatorsRumble$isShortGroundNavigation(controller, target));
        return shouldBlockExternalPathCreation(
                !targets.isEmpty(),
                false,
                controller.supportsFlightControl(),
                controller.canDrive(this.mob),
                controller.usesGroundNavigation(),
                this.vindicatorsRumble$hasCombatTarget(),
                allTargetsShort);
    }

    private static boolean shouldBlockExternalPathCreation(boolean hasTargets, boolean vehicleAiPathing,
                                                           boolean supportsFlightControl, boolean canDrive,
                                                           boolean usesGroundNavigation, boolean hasCombatTarget,
                                                           boolean allTargetsShort) {
        if (!hasTargets || vehicleAiPathing) {
            return false;
        }
        if (supportsFlightControl) {
            return true;
        }
        if (!canDrive || !usesGroundNavigation) {
            return false;
        }
        return hasCombatTarget || allTargetsShort;
    }

    private static boolean shouldBlockExternalGroundNavigation(boolean vehicleAiPathing, boolean supportsFlightControl,
                                                               boolean canDrive, boolean usesGroundNavigation,
                                                               boolean hasCombatTarget, boolean shortGroundNavigation) {
        if (vehicleAiPathing) {
            return false;
        }
        if (supportsFlightControl) {
            return true;
        }
        return canDrive && usesGroundNavigation && (hasCombatTarget || shortGroundNavigation);
    }

    @Unique
    private boolean vindicatorsRumble$isShortGroundNavigation(VehicleController controller, Vec3 target) {
        double minDistance = CommonConfig.VEHICLE_NAVIGATION_MIN_DISTANCE.get();
        return minDistance > 0.0D
                && this.vindicatorsRumble$horizontalDistanceSqr(controller.position(), target) < minDistance * minDistance;
    }

    @Unique
    private double vindicatorsRumble$horizontalDistanceSqr(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return dx * dx + dz * dz;
    }

    @Unique
    private Object vindicatorsRumble$nextNodeSummary(Path path) {
        if (path == null || path.isDone() || path.getNodeCount() <= 0) {
            return null;
        }
        return this.vindicatorsRumble$nodeSummary(path, path.getNextNodeIndex());
    }

    @Unique
    private Object vindicatorsRumble$nodeSummary(Path path, int index) {
        if (path == null || index < 0 || index >= path.getNodeCount()) {
            return null;
        }
        Node node = path.getNode(index);
        return new BlockPos(node.x, node.y, node.z);
    }
}
