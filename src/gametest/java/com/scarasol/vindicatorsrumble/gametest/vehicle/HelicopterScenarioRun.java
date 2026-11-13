package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwGameTestVehicle;
import com.scarasol.sona.init.SonaMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class HelicopterScenarioRun {
    private static final int SET_BLOCK_FLAGS = 2 | 16;
    private static final int WARMUP_TICKS = 10;
    private static final int ACQUISITION_TIMEOUT_TICKS = 200;
    private static final int OBSERVATION_TICKS = 400;
    private static final double ACQUISITION_DISTANCE = 8.0D;
    private static final int REQUIRED_VISIBLE_TICKS = 8;
    static final int POST_RECOVERY_TICKS = 120;
    private static final double MOVING_SPEED = 0.04D;
    private static final double STALL_SPEED = 0.025D;
    private static final int PAUSE_TICKS = 8;
    private static final int DIAGNOSTIC_WINDOW_TICKS = 40;
    private static final double DIAGNOSTIC_CLEARANCE_MARGIN = 2.0D;
    private static final double DIAGNOSTIC_PROGRESS_RATE = 0.035D;
    private static final double YAW_RESPONSE_DEGREES = 3.0D;
    private static final double ALIGNED_YAW_DEGREES = 20.0D;
    private static final double UNSTABLE_ATTITUDE_DEGREES = 45.0D;

    private final ServerLevel level;
    private final HelicopterScenario scene;
    private final ResourceLocation helicopterId;
    private Entity vehicle;
    private Mob driver;
    private Mob target;
    private VehicleController controller;
    private Vec3 previousPosition;
    private int acquisitionTicks;
    private boolean obstacleActive;
    private int elapsedTicks;
    private int recoveredAt = -1;
    private int visibleTicks;
    private int postRecoveryOcclusions;
    private int currentPostRecoveryBlockedTicks;
    private int longestPostRecoveryBlockedTicks;
    private int firstMovementAt = -1;
    private int yawResponseTicks = -1;
    private int yawAlignmentTicks = -1;
    private int yawVisibleTicks;
    private double recoveryYawError = Double.NaN;
    private double yawEpisodeStartError = Double.NaN;
    private double bestPostRecoveryYawError = Double.POSITIVE_INFINITY;
    private int currentStall;
    private int longestStall;
    private int pauseCount;
    private boolean pauseRecorded;
    private int measuredTicks;
    private double horizontalTravel;
    private double totalTravel;
    private double altitudeSum;
    private double maxAltitude;
    private double finalAltitude;
    private double maxPitch;
    private double maxRoll;
    private double pitchSum;
    private double rollSum;
    private int attitudeSamples;
    private int unstableAttitudeTicks;
    private int adInputTicks;
    private int deltaRotTicks;
    private int opposedRollTicks;
    private double mouseRollDemandSum;
    private double adRollDemandSum;
    private double netRollDemandSum;
    private double maxMouseRollDemand;
    private double maxAdRollDemand;
    private double maxTargetDistance;
    private double maxTargetDistance3d;
    private double maxForwardCoordinate = Double.NEGATIVE_INFINITY;
    private double minLateralCoordinate = Double.POSITIVE_INFINITY;
    private double maxLateralCoordinate = Double.NEGATIVE_INFINITY;
    private double maxSideClearance;
    private double maxFarClearance;
    private double maxTopClearance = Double.NEGATIVE_INFINITY;
    private int collisionTicks;
    private boolean targetLost;
    private int targetLostAt = -1;
    private double targetLostDistance = Double.NaN;
    private double targetLostDistance3d = Double.NaN;
    private double targetLostForward = Double.NaN;
    private double targetLostLateral = Double.NaN;
    private double targetLostSideClearance = Double.NaN;
    private double targetLostFarClearance = Double.NaN;
    private double targetLostTopClearance = Double.NaN;
    private boolean targetVisibleAtLoss;
    private String targetAfterLoss = "not-lost";
    private boolean dismounted;
    private boolean wrecked;
    private boolean complete;
    private String invalidReason;
    private String firstBlocker = "none";
    private String lastBlocker = "none";
    private final List<String> trace = new ArrayList<>(OBSERVATION_TICKS);
    private final List<GeometrySample> geometrySamples = new ArrayList<>(OBSERVATION_TICKS);
    private double lastForwardCoordinate;
    private double lastLateralCoordinate;
    private double lastSideClearance;
    private double lastFarClearance;
    private double lastTopClearance;
    private double previousTraceForward = Double.NaN;
    private double previousTraceLateral = Double.NaN;
    private double previousTraceSide = Double.NaN;
    private double previousTraceFar = Double.NaN;
    private double previousTraceTop = Double.NaN;

    private record GeometrySample(int tick, double horizontalSpeed, double totalSpeed,
                                  double forward, double lateral, double side,
                                  double far, double top, double targetDistance,
                                  double targetDistance3d) {
    }

    HelicopterScenarioRun(ServerLevel level, HelicopterScenario scene, ResourceLocation helicopterId) {
        this.level = level;
        this.scene = scene;
        this.helicopterId = helicopterId;
    }

    void spawn() {
        this.writeObstacle(Blocks.AIR.defaultBlockState());
        EntityType<?> vehicleType = BuiltInRegistries.ENTITY_TYPE.get(this.helicopterId);
        this.vehicle = this.createEntity(vehicleType, this.scene.start(), false);
        this.driver = (Mob) this.createEntity(EntityType.VINDICATOR, this.scene.start(), false);
        this.target = (Mob) this.createEntity(EntityType.VILLAGER, this.acquisitionPosition(), true);
        this.vehicle.setInvulnerable(true);
        this.driver.setInvulnerable(true);
        this.driver.setPersistenceRequired();
        this.target.setPersistenceRequired();
        this.target.setNoGravity(true);
        this.target.addEffect(new MobEffectInstance(
                SonaMobEffects.EXPOSURE.get(), ACQUISITION_TIMEOUT_TICKS, 3, false, false));
        this.target.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255, false, false));
        this.vehicle.setYRot(this.scene.initialYaw());
        this.controller = VehicleControllers.create(this.vehicle).orElse(null);
        if (this.controller == null) {
            this.invalidate("no ControllerAPI for " + this.helicopterId);
            return;
        }
        if (!this.controller.mount(this.driver) || !this.controller.canDrive(this.driver)) {
            this.invalidate("driver did not enter the driving seat");
            return;
        }
        SbwGameTestVehicle.prepareUnarmedAirborne(this.vehicle);
    }

    void observeServerTick() {
        if (this.complete) {
            return;
        }
        if (!this.entitiesRemainUsable()) {
            this.complete = true;
            return;
        }
        if (!this.obstacleActive) {
            this.observeTargetAcquisition();
            return;
        }
        this.elapsedTicks++;

        Vec3 position = this.vehicle.position();
        Vec3 center = this.vehicle.getBoundingBox().getCenter();
        Vec3 movement = position.subtract(this.previousPosition);
        double horizontalSpeed = Math.hypot(movement.x, movement.z);
        double totalSpeed = movement.length();
        this.previousPosition = position;
        if (this.recoveredAt < 0) {
            this.measuredTicks++;
            this.horizontalTravel += horizontalSpeed;
            this.totalTravel += totalSpeed;
        }
        if (this.firstMovementAt < 0 && totalSpeed >= MOVING_SPEED) {
            this.firstMovementAt = this.elapsedTicks;
        }

        double altitude = position.y - this.scene.groundY();
        this.finalAltitude = altitude;
        double pitch = Math.abs(this.controller.pitch());
        double roll = Math.abs(this.controller.roll());
        if (this.recoveredAt < 0) {
            this.altitudeSum += altitude;
            this.maxAltitude = Math.max(this.maxAltitude, altitude);
            this.maxPitch = Math.max(this.maxPitch, pitch);
            this.maxRoll = Math.max(this.maxRoll, roll);
            this.pitchSum += pitch;
            this.rollSum += roll;
            this.attitudeSamples++;
            if (pitch > UNSTABLE_ATTITUDE_DEGREES || roll > UNSTABLE_ATTITUDE_DEGREES) {
                this.unstableAttitudeTicks++;
            }
        }
        SbwGameTestVehicle.FlightControl flightControl = SbwGameTestVehicle.flightControl(this.vehicle);
        double mouseRollDemand = 0.25D * flightControl.mouseX() * flightControl.rotor();
        double adRollDemand = flightControl.deltaRot();
        if (flightControl.left() || flightControl.right()) {
            this.adInputTicks++;
        }
        if (Math.abs(adRollDemand) > 0.01D) {
            this.deltaRotTicks++;
            this.mouseRollDemandSum += Math.abs(mouseRollDemand);
            this.adRollDemandSum += Math.abs(adRollDemand);
            this.netRollDemandSum += Math.abs(mouseRollDemand + adRollDemand);
            this.maxMouseRollDemand = Math.max(this.maxMouseRollDemand, Math.abs(mouseRollDemand));
            this.maxAdRollDemand = Math.max(this.maxAdRollDemand, Math.abs(adRollDemand));
            if (mouseRollDemand * adRollDemand < 0.0D) {
                this.opposedRollTicks++;
            }
        }
        Vec3 targetCenter = this.target.getBoundingBox().getCenter();
        this.maxTargetDistance = Math.max(this.maxTargetDistance,
                horizontalDistance(position, targetCenter));
        this.maxTargetDistance3d = Math.max(this.maxTargetDistance3d,
                position.distanceTo(targetCenter));
        this.observeGeometry(center);
        if (this.level.getBlockCollisions(this.vehicle,
                this.vehicle.getBoundingBox().deflate(0.02D)).iterator().hasNext()) {
            this.collisionTicks++;
        }
        this.wrecked |= this.controller.isWreck();
        this.dismounted |= !this.driver.isPassenger() || this.driver.getVehicle() != this.vehicle;

        HitResult sight = this.lineHit();
        if (this.targetLostAt < 0 && this.driver.getTarget() != this.target) {
            this.targetLost = true;
            this.targetLostAt = this.elapsedTicks;
            this.targetLostDistance = horizontalDistance(position, targetCenter);
            this.targetLostDistance3d = position.distanceTo(targetCenter);
            this.recordTargetLossGeometry(center);
            this.targetVisibleAtLoss = sight.getType() == HitResult.Type.MISS;
            Entity replacement = this.driver.getTarget();
            this.targetAfterLoss = replacement == null
                    ? "none"
                    : BuiltInRegistries.ENTITY_TYPE.getKey(replacement.getType()).toString();
        }

        this.lastBlocker = sight instanceof BlockHitResult blockHit
                ? blockHit.getBlockPos().toShortString()
                : sight.getType().name();
        if (this.firstBlocker.equals("none") && sight instanceof BlockHitResult) {
            this.firstBlocker = this.lastBlocker;
        }
        if (sight.getType() == HitResult.Type.MISS) {
            this.visibleTicks++;
            if (this.visibleTicks >= REQUIRED_VISIBLE_TICKS && this.recoveredAt < 0) {
                this.recoveredAt = this.elapsedTicks;
                this.recoveryYawError = this.yawErrorToTarget(position);
                this.bestPostRecoveryYawError = this.recoveryYawError;
            }
        } else {
            this.visibleTicks = 0;
        }

        if (this.elapsedTicks >= WARMUP_TICKS && this.recoveredAt < 0) {
            this.observeStall(totalSpeed);
        } else {
            this.closePause();
        }
        if (this.recoveredAt >= 0) {
            this.observePostRecoveryVisibility(sight);
            if (sight.getType() == HitResult.Type.MISS) {
                this.observePostRecoveryYaw(position);
            } else {
                this.yawVisibleTicks = 0;
            }
        }
        this.appendTrace(position, movement, horizontalSpeed, totalSpeed, altitude,
                this.controller.pitch(), roll,
                flightControl, sight, targetCenter);
        if (this.recoveredAt >= 0 && this.elapsedTicks >= this.recoveredAt + POST_RECOVERY_TICKS) {
            this.complete = true;
        }
        if (this.elapsedTicks >= OBSERVATION_TICKS) {
            this.complete = true;
        }
        if (this.wrecked || this.dismounted || this.targetLost) {
            this.complete = true;
        }
    }

    void cleanup() {
        this.closePause();
        if (this.vehicle != null) {
            this.vehicle.discard();
        }
        if (this.driver != null) {
            this.driver.discard();
        }
        if (this.target != null) {
            this.target.discard();
        }
        this.writeObstacle(Blocks.AIR.defaultBlockState());
    }

    private boolean entitiesRemainUsable() {
        if (this.vehicle == null || this.driver == null || this.target == null || this.controller == null) {
            this.invalidReason = this.invalidReason == null ? "fixture entity missing" : this.invalidReason;
            return false;
        }
        if (this.vehicle.isRemoved() || this.driver.isRemoved() || this.target.isRemoved()) {
            this.invalidReason = "fixture entity removed before completion";
            return false;
        }
        return true;
    }

    private void observeTargetAcquisition() {
        this.acquisitionTicks++;
        if (this.driver.getTarget() == this.target) {
            this.activateObstacleRun();
            return;
        }
        if (this.acquisitionTicks >= ACQUISITION_TIMEOUT_TICKS) {
            this.invalidate("exposed visible villager was not acquired");
        }
    }

    private void activateObstacleRun() {
        this.target.removeEffect(SonaMobEffects.EXPOSURE.get());
        Vec3 targetPosition = this.scene.target();
        this.target.moveTo(targetPosition.x, targetPosition.y, targetPosition.z, 0.0F, 0.0F);
        BlockPos targetBlock = BlockPos.containing(targetPosition);
        int targetGroundY = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                targetBlock.getX(), targetBlock.getZ());
        if (targetGroundY != this.scene.groundY()) {
            this.invalidate("target terrain height differs from scenario ground");
            return;
        }
        if (!this.level.noCollision(this.target)) {
            this.invalidate("target destination intersects terrain");
            return;
        }
        this.writeObstacle(Blocks.STONE_BRICKS.defaultBlockState());
        this.previousPosition = this.vehicle.position();
        this.obstacleActive = true;
        HitResult initialSight = this.lineHit();
        if (initialSight.getType() == HitResult.Type.MISS) {
            this.invalidate("fixture did not block LOS after natural target acquisition");
        }
    }

    private Vec3 acquisitionPosition() {
        Vec3 offset = this.scene.target().subtract(this.scene.start());
        Vec3 horizontal = new Vec3(offset.x, 0.0D, offset.z).normalize().scale(ACQUISITION_DISTANCE);
        return this.scene.start().add(horizontal);
    }

    private void observeStall(double speed) {
        if (speed < STALL_SPEED) {
            this.currentStall++;
            this.longestStall = Math.max(this.longestStall, this.currentStall);
            if (this.currentStall >= PAUSE_TICKS && !this.pauseRecorded) {
                this.pauseCount++;
                this.pauseRecorded = true;
            }
            return;
        }
        this.currentStall = 0;
        this.pauseRecorded = false;
    }

    private void closePause() {
        this.currentStall = 0;
        this.pauseRecorded = false;
    }

    private void observeGeometry(Vec3 center) {
        double forward = this.scene.forwardCoordinate(center);
        double lateral = this.scene.lateralCoordinate(center);
        double side = this.sideClearance(lateral);
        double far = forward - this.scene.obstacleForwardMax();
        double top = center.y - this.scene.obstacleTopY();
        this.lastForwardCoordinate = forward;
        this.lastLateralCoordinate = lateral;
        this.lastSideClearance = side;
        this.lastFarClearance = far;
        this.lastTopClearance = top;
        this.maxForwardCoordinate = Math.max(this.maxForwardCoordinate, forward);
        this.minLateralCoordinate = Math.min(this.minLateralCoordinate, lateral);
        this.maxLateralCoordinate = Math.max(this.maxLateralCoordinate, lateral);
        this.maxSideClearance = Math.max(this.maxSideClearance, side);
        this.maxFarClearance = Math.max(this.maxFarClearance, far);
        this.maxTopClearance = Math.max(this.maxTopClearance, top);
    }

    private void recordTargetLossGeometry(Vec3 center) {
        this.targetLostForward = this.scene.forwardCoordinate(center);
        this.targetLostLateral = this.scene.lateralCoordinate(center);
        this.targetLostSideClearance = this.sideClearance(this.targetLostLateral);
        this.targetLostFarClearance = this.targetLostForward - this.scene.obstacleForwardMax();
        this.targetLostTopClearance = center.y - this.scene.obstacleTopY();
    }

    private double sideClearance(double lateral) {
        return Math.max(this.scene.obstacleLateralMin() - lateral,
                lateral - this.scene.obstacleLateralMax());
    }

    private void appendTrace(Vec3 position, Vec3 movement, double horizontalSpeed, double totalSpeed,
                             double altitude, double signedPitch, double roll,
                             SbwGameTestVehicle.FlightControl flightControl,
                             HitResult sight, Vec3 targetCenter) {
        double dForward = Double.isNaN(this.previousTraceForward)
                ? 0.0D : this.lastForwardCoordinate - this.previousTraceForward;
        double dLateral = Double.isNaN(this.previousTraceLateral)
                ? 0.0D : this.lastLateralCoordinate - this.previousTraceLateral;
        double dSide = Double.isNaN(this.previousTraceSide)
                ? 0.0D : this.lastSideClearance - this.previousTraceSide;
        double dFar = Double.isNaN(this.previousTraceFar)
                ? 0.0D : this.lastFarClearance - this.previousTraceFar;
        double dTop = Double.isNaN(this.previousTraceTop)
                ? 0.0D : this.lastTopClearance - this.previousTraceTop;
        double targetDistance = horizontalDistance(position, targetCenter);
        double targetDistance3d = position.distanceTo(targetCenter);
        this.geometrySamples.add(new GeometrySample(this.elapsedTicks, horizontalSpeed, totalSpeed,
                this.lastForwardCoordinate, this.lastLateralCoordinate, this.lastSideClearance,
                this.lastFarClearance, this.lastTopClearance, targetDistance, targetDistance3d));
        this.trace.add(String.format(Locale.ROOT,
                "t=%03d state=%s decision=%s los=%s target=%s h=%.3f v=%.3f move=(%.2f,%.2f,%.2f) f=%.1f lat=%.1f side=%.1f far=%.1f top=%.1f dF=%.2f dLat=%.2f dSide=%.2f dFar=%.2f dTop=%.2f dist=%.1f/%.1f stop=%.1f hover=%s yawErr=%.1f alt=%.1f pitch=%.1f roll=%.1f ctrl=mx%.3f my%.3f dr%.3f rotor%.2f F=%s B=%s L=%s R=%s U=%s D=%s S=%s",
                this.elapsedTicks, this.traceState(sight),
                this.traceDecision(totalSpeed, dForward, dLateral, dSide, dFar, dTop),
                this.traceSight(sight), this.traceTarget(),
                horizontalSpeed, totalSpeed, movement.x, movement.y, movement.z,
                this.lastForwardCoordinate, this.lastLateralCoordinate, this.lastSideClearance,
                this.lastFarClearance, this.lastTopClearance, dForward, dLateral, dSide, dFar, dTop,
                targetDistance, targetDistance3d,
                this.controller.estimatedHorizontalStoppingDistance(), this.controller.hoverMode(),
                this.yawErrorToTarget(position), altitude, signedPitch, roll,
                flightControl.mouseX(), flightControl.mouseY(), flightControl.deltaRot(), flightControl.rotor(),
                flightControl.forward(), flightControl.back(), flightControl.left(), flightControl.right(),
                flightControl.up(), flightControl.down(), flightControl.sprint())
                + com.scarasol.vindicatorsrumble.gametest.profile.HelicopterPlanTrace.take(this.vehicle.getId()));
        this.previousTraceForward = this.lastForwardCoordinate;
        this.previousTraceLateral = this.lastLateralCoordinate;
        this.previousTraceSide = this.lastSideClearance;
        this.previousTraceFar = this.lastFarClearance;
        this.previousTraceTop = this.lastTopClearance;
    }

    private String traceState(HitResult sight) {
        if (this.targetLostAt == this.elapsedTicks) {
            return "TARGET_LOST";
        }
        if (this.driver.getTarget() != this.target) {
            return "NO_TARGET";
        }
        if (sight.getType() == HitResult.Type.MISS) {
            return this.recoveredAt >= 0 ? "RECOVERED_LOS" : "LOS_CONFIRM";
        }
        return "BLOCKED_LOS";
    }

    private String traceDecision(double totalSpeed, double dForward, double dLateral,
                                 double dSide, double dFar, double dTop) {
        if (this.targetLostAt == this.elapsedTicks) {
            return "TARGET_LOST";
        }
        if (totalSpeed < STALL_SPEED) {
            return "HOLD_OR_STALLED";
        }
        double absLateral = Math.abs(dLateral);
        double absForward = Math.abs(dForward);
        double absTop = Math.abs(dTop);
        if (dSide < -0.03D && absLateral >= absForward * 0.8D) {
            return "SIDE_BACKSLIDE";
        }
        if (dSide > 0.03D && absLateral >= absForward * 0.8D && absLateral >= absTop * 0.6D) {
            return dLateral < 0.0D ? "SIDE_LEFT" : "SIDE_RIGHT";
        }
        if (dFar > 0.03D && absForward >= absLateral * 0.8D && absForward >= absTop * 0.6D) {
            return "FORWARD";
        }
        if (dForward < -0.03D && absForward >= absLateral * 0.8D) {
            return "BACKTRACK";
        }
        if (dTop > 0.03D && absTop >= absLateral * 0.6D && absTop >= absForward * 0.6D) {
            return "CLIMB";
        }
        if (dTop < -0.03D && absTop >= absLateral * 0.6D && absTop >= absForward * 0.6D) {
            return "DESCEND";
        }
        return "MIXED";
    }

    private String traceSight(HitResult sight) {
        if (sight.getType() == HitResult.Type.MISS) {
            return HitResult.Type.MISS.name();
        }
        if (sight instanceof BlockHitResult blockHit) {
            return "BLOCK@" + blockHit.getBlockPos().toShortString();
        }
        return sight.getType().name();
    }

    private String traceTarget() {
        if (this.driver.getTarget() == this.target) {
            return "current";
        }
        Entity replacement = this.driver.getTarget();
        return replacement == null
                ? "none"
                : BuiltInRegistries.ENTITY_TYPE.getKey(replacement.getType()).toString();
    }

    private void observePostRecoveryYaw(Vec3 position) {
        double yawError = this.yawErrorToTarget(position);
        if (this.yawVisibleTicks == 0) {
            this.yawEpisodeStartError = yawError;
        }
        int visibleTicksSinceRecovery = this.yawVisibleTicks++;
        this.bestPostRecoveryYawError = Math.min(this.bestPostRecoveryYawError, yawError);
        if (this.yawResponseTicks < 0
                && (this.yawEpisodeStartError <= ALIGNED_YAW_DEGREES
                || yawError <= this.yawEpisodeStartError - YAW_RESPONSE_DEGREES)) {
            this.yawResponseTicks = visibleTicksSinceRecovery;
        }
        if (this.yawAlignmentTicks < 0 && yawError <= ALIGNED_YAW_DEGREES) {
            this.yawAlignmentTicks = visibleTicksSinceRecovery;
        }
    }

    private void observePostRecoveryVisibility(HitResult sight) {
        if (sight.getType() == HitResult.Type.MISS) {
            this.currentPostRecoveryBlockedTicks = 0;
            return;
        }
        if (this.currentPostRecoveryBlockedTicks++ == 0) {
            this.postRecoveryOcclusions++;
        }
        this.longestPostRecoveryBlockedTicks = Math.max(
                this.longestPostRecoveryBlockedTicks, this.currentPostRecoveryBlockedTicks);
    }

    private void invalidate(String reason) {
        this.invalidReason = reason;
        this.complete = true;
    }

    private Entity createEntity(EntityType<?> type, Vec3 position, boolean noAi) {
        Entity entity = type.create(this.level);
        if (entity == null) {
            throw new IllegalStateException("Failed to create " + type);
        }
        entity.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        if (entity instanceof Mob mob) {
            mob.setNoAi(noAi);
        }
        if (!this.level.addFreshEntity(entity)) {
            throw new IllegalStateException("Failed to add " + type + " to the GameTest world");
        }
        return entity;
    }

    private HitResult lineHit() {
        return this.level.clip(new ClipContext(
                this.vehicle.getBoundingBox().getCenter(),
                this.target.getBoundingBox().getCenter(),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this.vehicle));
    }

    private void writeObstacle(BlockState state) {
        int halfWidth = this.scene.width() / 2;
        int halfDepth = this.scene.depth() / 2;
        for (int forward = -halfDepth; forward <= halfDepth; forward++) {
            for (int lateral = -halfWidth; lateral <= halfWidth; lateral++) {
                for (int y = 0; y < this.scene.height(); y++) {
                    boolean shell = this.scene.kind().solid()
                            || Math.abs(forward) == halfDepth
                            || Math.abs(lateral) == halfWidth
                            || y == this.scene.height() - 1;
                    if (!shell) {
                        continue;
                    }
                    BlockPos pos = this.scene.obstacleCenter().offset(
                            this.scene.forwardX() * forward - this.scene.forwardZ() * lateral,
                            y,
                            this.scene.forwardZ() * forward + this.scene.forwardX() * lateral);
                    this.level.setBlock(pos, state, SET_BLOCK_FLAGS);
                }
            }
        }
    }

    HelicopterScenario scene() {
        return this.scene;
    }

    ResourceLocation helicopterId() {
        return this.helicopterId;
    }

    boolean complete() {
        return this.complete;
    }

    boolean valid() {
        return this.invalidReason == null;
    }

    boolean recovered() {
        return this.recoveredAt >= 0;
    }

    int recoveredAt() {
        return this.recoveredAt;
    }

    int postRecoveryOcclusions() {
        return this.postRecoveryOcclusions;
    }

    int longestPostRecoveryBlockedTicks() {
        return this.longestPostRecoveryBlockedTicks;
    }

    int firstMovementAt() {
        return this.firstMovementAt;
    }

    int longestStall() {
        return this.longestStall;
    }

    int pauseCount() {
        return this.pauseCount;
    }

    int collisionTicks() {
        return this.collisionTicks;
    }

    int yawResponseTicks() {
        return this.yawResponseTicks;
    }

    int yawAlignmentTicks() {
        return this.yawAlignmentTicks;
    }

    double recoveryYawError() {
        return this.recoveryYawError;
    }

    double bestPostRecoveryYawError() {
        return this.bestPostRecoveryYawError;
    }

    boolean targetLost() {
        return this.targetLost;
    }

    boolean dismounted() {
        return this.dismounted;
    }

    boolean wrecked() {
        return this.wrecked;
    }

    double averageHorizontalSpeed() {
        return this.horizontalTravel / Math.max(1, this.measuredTicks);
    }

    double averageTotalSpeed() {
        return this.totalTravel / Math.max(1, this.measuredTicks);
    }

    double averageAltitude() {
        return this.altitudeSum / Math.max(1, this.attitudeSamples);
    }

    double maxAltitude() {
        return this.maxAltitude;
    }

    boolean altitudeRunaway() {
        boolean correctionWindowComplete = this.recoveredAt < 0
                || this.elapsedTicks >= this.recoveredAt + POST_RECOVERY_TICKS;
        return correctionWindowComplete && this.finalAltitude > this.scene.allowedAltitude();
    }

    double maxPitch() {
        return this.maxPitch;
    }

    double maxRoll() {
        return this.maxRoll;
    }

    double maxTargetDistance() {
        return this.maxTargetDistance;
    }

    double maxTargetDistance3d() {
        return this.maxTargetDistance3d;
    }

    double maxSideClearance() {
        return this.maxSideClearance;
    }

    double maxFarClearance() {
        return this.maxFarClearance;
    }

    double maxTopClearance() {
        return this.maxTopClearance;
    }

    int targetLostAt() {
        return this.targetLostAt;
    }

    double targetLostDistance() {
        return this.targetLostDistance;
    }

    double targetLostDistance3d() {
        return this.targetLostDistance3d;
    }

    boolean targetVisibleAtLoss() {
        return this.targetVisibleAtLoss;
    }

    double averagePitch() {
        return this.pitchSum / Math.max(1, this.attitudeSamples);
    }

    double averageRoll() {
        return this.rollSum / Math.max(1, this.attitudeSamples);
    }

    double unstableAttitudeRatio() {
        return this.unstableAttitudeTicks / (double) Math.max(1, this.attitudeSamples);
    }

    int adInputTicks() {
        return this.adInputTicks;
    }

    int deltaRotTicks() {
        return this.deltaRotTicks;
    }

    int opposedRollTicks() {
        return this.opposedRollTicks;
    }

    double mouseRollDemandSum() {
        return this.mouseRollDemandSum;
    }

    double adRollDemandSum() {
        return this.adRollDemandSum;
    }

    double netRollDemandSum() {
        return this.netRollDemandSum;
    }

    double maxMouseRollDemand() {
        return this.maxMouseRollDemand;
    }

    double maxAdRollDemand() {
        return this.maxAdRollDemand;
    }

    String invalidReason() {
        return this.invalidReason;
    }

    String progressDiagnosis() {
        if (!this.valid()) {
            return "INVALID_FIXTURE";
        }
        if (this.recovered() && this.longestPostRecoveryBlockedTicks >= REQUIRED_VISIBLE_TICKS) {
            return "POST_RECOVERY_REOCCLUSION";
        }
        if (this.recovered()) {
            return "RECOVERED";
        }
        if (this.dismounted) {
            return "DISMOUNTED";
        }
        if (this.wrecked) {
            return "WRECKED";
        }
        double sideRate = this.lateSideRate();
        double farRate = this.lateFarRate();
        double topRate = this.lateTopRate();
        boolean activeProgress = Math.max(sideRate, Math.max(farRate, topRate)) >= DIAGNOSTIC_PROGRESS_RATE;
        if (this.targetLost) {
            if (activeProgress) {
                return "ACTIVE_PROGRESS_TARGET_LOST";
            }
            if (this.targetLostDistance >= this.scene.allowedTargetDistance() * 0.9D) {
                return "RANGE_EDGE_TARGET_LOST";
            }
            if (this.lateAverageTotalSpeed() < STALL_SPEED) {
                return "STALLED_TARGET_LOST";
            }
            return "TARGET_LOST_NO_CLEAR_PROGRESS";
        }
        if (activeProgress) {
            return "ACTIVE_PROGRESS_TIMEOUT";
        }
        if (this.lateAverageTotalSpeed() < STALL_SPEED) {
            return "STALLED";
        }
        if (sideRate <= -DIAGNOSTIC_PROGRESS_RATE || farRate <= -DIAGNOSTIC_PROGRESS_RATE
                || topRate <= -DIAGNOSTIC_PROGRESS_RATE) {
            return "BACKSLIDING";
        }
        if (this.lastSideClearance >= DIAGNOSTIC_CLEARANCE_MARGIN
                || this.lastFarClearance >= DIAGNOSTIC_CLEARANCE_MARGIN
                || this.lastTopClearance >= DIAGNOSTIC_CLEARANCE_MARGIN) {
            return "CLEARANCE_WITHOUT_LOS";
        }
        return "NO_CLEAR_PROGRESS";
    }

    String lateMotion() {
        double sideRate = this.lateSideRate();
        double farRate = this.lateFarRate();
        double topRate = this.lateTopRate();
        double best = Math.max(sideRate, Math.max(farRate, topRate));
        if (this.lateAverageTotalSpeed() < STALL_SPEED) {
            return "STALLED";
        }
        if (best < DIAGNOSTIC_PROGRESS_RATE) {
            return sideRate <= -DIAGNOSTIC_PROGRESS_RATE || farRate <= -DIAGNOSTIC_PROGRESS_RATE
                    ? "BACKSLIDE" : "NO_CLEAR_PROGRESS";
        }
        if (topRate > Math.max(sideRate, farRate) * 1.4D) {
            return "CLIMB_ONLY";
        }
        if (farRate > Math.max(sideRate, topRate) * 1.2D) {
            return "FORWARD";
        }
        if (sideRate > Math.max(farRate, topRate) * 1.2D) {
            return "SIDE";
        }
        return "MIXED";
    }

    private GeometrySample lastSample() {
        if (this.geometrySamples.isEmpty()) {
            return null;
        }
        return this.geometrySamples.get(this.geometrySamples.size() - 1);
    }

    private GeometrySample lateStartSample() {
        if (this.geometrySamples.isEmpty()) {
            return null;
        }
        int index = Math.max(0, this.geometrySamples.size() - 1 - DIAGNOSTIC_WINDOW_TICKS);
        return this.geometrySamples.get(index);
    }

    private int lateTickSpan() {
        GeometrySample start = this.lateStartSample();
        GeometrySample end = this.lastSample();
        if (start == null || end == null) {
            return 0;
        }
        return Math.max(1, end.tick() - start.tick());
    }

    private double lateForwardRate() {
        GeometrySample start = this.lateStartSample();
        GeometrySample end = this.lastSample();
        return start == null || end == null ? 0.0D
                : (end.forward() - start.forward()) / this.lateTickSpan();
    }

    private double lateSideRate() {
        GeometrySample start = this.lateStartSample();
        GeometrySample end = this.lastSample();
        return start == null || end == null ? 0.0D
                : (end.side() - start.side()) / this.lateTickSpan();
    }

    private double lateFarRate() {
        GeometrySample start = this.lateStartSample();
        GeometrySample end = this.lastSample();
        return start == null || end == null ? 0.0D
                : (end.far() - start.far()) / this.lateTickSpan();
    }

    private double lateTopRate() {
        GeometrySample start = this.lateStartSample();
        GeometrySample end = this.lastSample();
        return start == null || end == null ? 0.0D
                : (end.top() - start.top()) / this.lateTickSpan();
    }

    private double lateAverageHorizontalSpeed() {
        if (this.geometrySamples.isEmpty()) {
            return 0.0D;
        }
        int start = Math.max(0, this.geometrySamples.size() - DIAGNOSTIC_WINDOW_TICKS);
        double sum = 0.0D;
        for (int index = start; index < this.geometrySamples.size(); index++) {
            sum += this.geometrySamples.get(index).horizontalSpeed();
        }
        return sum / Math.max(1, this.geometrySamples.size() - start);
    }

    private double lateAverageTotalSpeed() {
        if (this.geometrySamples.isEmpty()) {
            return 0.0D;
        }
        int start = Math.max(0, this.geometrySamples.size() - DIAGNOSTIC_WINDOW_TICKS);
        double sum = 0.0D;
        for (int index = start; index < this.geometrySamples.size(); index++) {
            sum += this.geometrySamples.get(index).totalSpeed();
        }
        return sum / Math.max(1, this.geometrySamples.size() - start);
    }

    private double etaToClear(double clearance, double rate) {
        if (clearance >= DIAGNOSTIC_CLEARANCE_MARGIN) {
            return 0.0D;
        }
        if (rate <= 0.01D) {
            return Double.POSITIVE_INFINITY;
        }
        return (DIAGNOSTIC_CLEARANCE_MARGIN - clearance) / rate;
    }

    private String formatEta(double eta) {
        if (!Double.isFinite(eta)) {
            return "inf";
        }
        return String.format(Locale.ROOT, "%.0f", eta);
    }

    String describe(String reasons) {
        return String.format(Locale.ROOT,
                "helicopter=%s scene=%d direction=(%d,%d) kind=%s targetY=%d size=%dx%dx%d reasons=%s acquired=%d recovered=%d reocclusions=%d longestReocclusion=%d firstMove=%d yawAtRecovery=%.1f yawResponse=%d yawAlign=%d yawBest=%.1f avgH=%.3f avg3D=%.3f stall=%d pauses=%d collisions=%d targetLostAt=%d lostDistance=%.1f/%.1f lostVisible=%s targetAfterLoss=%s pitchAvg=%.1f pitchMax=%.1f rollAvg=%.1f rollMax=%.1f unstableRatio=%.3f avgAlt=%.1f maxAlt=%.1f/%.1f finalAlt=%.1f maxTarget=%.1f/%.1f maxTarget3D=%.1f geom=lostF/Lat/side/far/top:%.1f/%.1f/%.1f/%.1f/%.1f maxF/latMin/latMax/side/far/top:%.1f/%.1f/%.1f/%.1f/%.1f/%.1f endF/Lat/side/far/top:%.1f/%.1f/%.1f/%.1f/%.1f late%d=dF/side/far/top:%.3f/%.3f/%.3f/%.3f lateSpeed=%.3f/%.3f etaSide/Far/Top=%s/%s/%s diagnosis=%s motion=%s blockers=%s->%s invalid=%s",
                this.helicopterId, this.scene.index(), this.scene.forwardX(), this.scene.forwardZ(),
                this.scene.kind(), this.scene.targetHeight(),
                this.scene.width(), this.scene.depth(), this.scene.height(), reasons,
                this.acquisitionTicks, this.recoveredAt, this.postRecoveryOcclusions,
                this.longestPostRecoveryBlockedTicks, this.firstMovementAt, this.recoveryYawError,
                this.yawResponseTicks, this.yawAlignmentTicks, this.bestPostRecoveryYawError,
                this.averageHorizontalSpeed(), this.averageTotalSpeed(),
                this.longestStall, this.pauseCount, this.collisionTicks, this.targetLostAt,
                this.targetLostDistance, this.targetLostDistance3d,
                this.targetVisibleAtLoss, this.targetAfterLoss,
                this.averagePitch(), this.maxPitch, this.averageRoll(), this.maxRoll,
                this.unstableAttitudeRatio(), this.averageAltitude(), this.maxAltitude,
                this.scene.allowedAltitude(), this.finalAltitude,
                this.maxTargetDistance, this.scene.allowedTargetDistance(),
                this.maxTargetDistance3d,
                this.targetLostForward, this.targetLostLateral, this.targetLostSideClearance,
                this.targetLostFarClearance, this.targetLostTopClearance,
                this.maxForwardCoordinate, this.minLateralCoordinate, this.maxLateralCoordinate,
                this.maxSideClearance, this.maxFarClearance, this.maxTopClearance,
                this.lastForwardCoordinate, this.lastLateralCoordinate, this.lastSideClearance,
                this.lastFarClearance, this.lastTopClearance,
                DIAGNOSTIC_WINDOW_TICKS, this.lateForwardRate(), this.lateSideRate(),
                this.lateFarRate(), this.lateTopRate(),
                this.lateAverageHorizontalSpeed(), this.lateAverageTotalSpeed(),
                this.formatEta(this.etaToClear(this.lastSideClearance, this.lateSideRate())),
                this.formatEta(this.etaToClear(this.lastFarClearance, this.lateFarRate())),
                this.formatEta(this.etaToClear(this.lastTopClearance, this.lateTopRate())),
                this.progressDiagnosis(), this.lateMotion(),
                this.firstBlocker, this.lastBlocker,
                this.invalidReason == null ? "none" : this.invalidReason);
    }

    String trace(String reasons) {
        if (this.trace.isEmpty()) {
            return String.format(Locale.ROOT,
                    "trace helicopter=%s scene=%d reasons=%s empty%n",
                    this.helicopterId, this.scene.index(), reasons);
        }
        StringBuilder builder = new StringBuilder(this.trace.size() * 192);
        builder.append(String.format(Locale.ROOT,
                "trace helicopter=%s scene=%d vehicle=%d driver=%d reasons=%s ticks=%d%n",
                this.helicopterId, this.scene.index(),
                this.vehicle == null ? -1 : this.vehicle.getId(),
                this.driver == null ? -1 : this.driver.getId(),
                reasons, this.trace.size()));
        for (String line : this.trace) {
            builder.append("  ").append(line).append('\n');
        }
        return builder.toString();
    }

    void discardTrace() {
        this.trace.clear();
        this.geometrySamples.clear();
    }

    private static double horizontalDistance(Vec3 first, Vec3 second) {
        return Math.hypot(first.x - second.x, first.z - second.z);
    }

    private double yawErrorToTarget(Vec3 position) {
        Vec3 targetCenter = this.target.getBoundingBox().getCenter();
        double desiredYaw = Math.toDegrees(Math.atan2(targetCenter.z - position.z, targetCenter.x - position.x)) - 90.0D;
        return Math.abs(net.minecraft.util.Mth.wrapDegrees((float) (desiredYaw - this.vehicle.getYRot())));
    }
}
