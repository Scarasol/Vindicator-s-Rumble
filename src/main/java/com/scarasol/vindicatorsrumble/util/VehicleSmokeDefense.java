package com.scarasol.vindicatorsrumble.util;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleInput;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleThreats;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Vehicle-owned, transient smoke requests shared by sensing and driving. */
public final class VehicleSmokeDefense {
    private static final int HIT_REQUEST_FRESHNESS_TICKS = 10;
    private static final Map<Entity, VehicleSmokeDefense> VEHICLES = new WeakHashMap<>();

    private UUID driverUuid;
    private UUID requestingDriver;
    private UUID acquiringSource;
    private long acquiringSince;
    private long observedAt = Long.MIN_VALUE;
    private long hitExpiresAt = Long.MIN_VALUE;
    private long deployedAt = Long.MIN_VALUE;
    private VehicleThreats.Stage threatStage;

    private VehicleSmokeDefense() {
    }

    public static void observe(Mob mob, VehicleController controller) {
        observe(mob, controller, false);
    }

    private static void observe(Mob mob, VehicleController controller, boolean refresh) {
        Entity vehicle = controller.entity();
        if (!eligible(mob, controller)) {
            clearDriver(vehicle);
            return;
        }
        VehicleSmokeDefense defense = VEHICLES.computeIfAbsent(vehicle, key -> new VehicleSmokeDefense());
        long now = controller.level().getGameTime();
        if (!mob.getUUID().equals(defense.driverUuid)) {
            defense.driverUuid = mob.getUUID();
            defense.acquiringSource = null;
            defense.hitExpiresAt = Long.MIN_VALUE;
            defense.observedAt = Long.MIN_VALUE;
        }
        if (!refresh && defense.observedAt == now) {
            return;
        }
        VehicleThreats.Threat threat = VehicleThreats.strongest(vehicle, report ->
                report.stage() == VehicleThreats.Stage.INCOMING
                        ? controller.isSmokeEffectiveAgainst(report.source())
                        : !AllegianceUtil.isSameSide(mob, report.source())).orElse(null);
        defense.threatStage = threat == null ? null : threat.stage();
        if (threat != null && threat.stage() == VehicleThreats.Stage.ACQUIRING) {
            UUID source = threat.source().getUUID();
            if (!source.equals(defense.acquiringSource)
                    || defense.observedAt != now && defense.observedAt != now - 1) {
                defense.acquiringSince = now;
            }
            defense.acquiringSource = source;
        } else {
            defense.acquiringSource = null;
        }
        defense.observedAt = now;
    }

    public static boolean shouldDeploy(Mob mob, VehicleController controller) {
        observe(mob, controller);
        VehicleSmokeDefense defense = VEHICLES.get(controller.entity());
        if (defense == null || !controller.canDeploySmoke()) {
            return false;
        }
        long now = controller.level().getGameTime();
        return defense.deployedAt != now && (defense.hitExpiresAt > now
                || defense.threatStage == VehicleThreats.Stage.INCOMING
                || defense.threatStage == VehicleThreats.Stage.LOCKED
                || defense.acquiringSource != null
                && now - defense.acquiringSince >= CommonConfig.SMOKE_ACQUIRING_TICKS.get());
    }

    public static VehicleInput applyInput(Mob mob, VehicleController controller, VehicleInput input) {
        if (input.decoy() || !requestDeployment(mob, controller)) {
            return input;
        }
        return new VehicleInput(input.forward(), input.back(), input.left(), input.right(), input.brake(),
                input.sprint(), input.up(), input.down(), input.fire(), true);
    }

    public static boolean requestDeployment(Mob mob, VehicleController controller) {
        if (!shouldDeploy(mob, controller)) return false;
        VEHICLES.get(controller.entity()).requestingDriver = mob.getUUID();
        return true;
    }

    /** A new driver's explicit input supersedes an old AI request. */
    public static void inputChanged(Entity vehicle, Entity driver) {
        VehicleSmokeDefense defense = VEHICLES.get(vehicle);
        if (defense != null && defense.requestingDriver != null
                && (driver == null || !driver.getUUID().equals(defense.requestingDriver))) {
            defense.requestingDriver = null;
        }
    }

    /** SBW consumes input on a later vehicle tick, so check ownership and live threats again. */
    public static boolean allowDeployment(Entity vehicle) {
        VehicleSmokeDefense defense = VEHICLES.get(vehicle);
        if (defense == null || defense.requestingDriver == null) return true;
        UUID requestedBy = defense.requestingDriver;
        defense.requestingDriver = null;
        VehicleController controller = VehicleControllers.create(vehicle).orElse(null);
        Entity passenger = controller == null ? null : controller.controllingPassenger().orElse(null);
        if (!(passenger instanceof Mob mob) || !mob.getUUID().equals(requestedBy)
                || !eligible(mob, controller)) {
            clearDriver(vehicle);
            return false;
        }
        observe(mob, controller, true);
        return shouldDeploy(mob, controller);
    }

    public static void reportHit(Entity vehicle, Entity attacker, float lostHealth, float remainingHealth,
                                 float maxHealth) {
        if (!(vehicle.level() instanceof ServerLevel) || !(attacker instanceof LivingEntity)
                || !Float.isFinite(lostHealth) || !Float.isFinite(remainingHealth) || !Float.isFinite(maxHealth)
                || lostHealth <= 0.0F || remainingHealth <= 0.0F || maxHealth <= 0.0F) {
            return;
        }
        VehicleControllers.create(vehicle).ifPresent(controller -> {
            Entity passenger = controller.controllingPassenger().orElse(null);
            if (!(passenger instanceof Mob mob) || !eligible(mob, controller)
                    || attacker.getRootVehicle() == vehicle.getRootVehicle()
                    || AllegianceUtil.isSameSide(mob, attacker)) {
                return;
            }
            if ((double) lostHealth / maxHealth < CommonConfig.SMOKE_HEAVY_HIT_RATIO.get()
                    && (double) remainingHealth / maxHealth > CommonConfig.SMOKE_LOW_HEALTH_RATIO.get()) {
                return;
            }
            observe(mob, controller);
            VEHICLES.get(vehicle).hitExpiresAt = vehicle.level().getGameTime() + HIT_REQUEST_FRESHNESS_TICKS;
        });
    }

    public static void deployed(Entity vehicle) {
        VehicleSmokeDefense defense = VEHICLES.get(vehicle);
        if (defense != null) {
            defense.deployedAt = vehicle.level().getGameTime();
            defense.hitExpiresAt = Long.MIN_VALUE;
        }
    }

    public static void clear(Entity vehicle) {
        VEHICLES.remove(vehicle);
    }

    public static void clearDriver(Entity vehicle) {
        VehicleSmokeDefense defense = VEHICLES.get(vehicle);
        if (defense != null) {
            defense.driverUuid = null;
            defense.acquiringSource = null;
            defense.threatStage = null;
            defense.hitExpiresAt = Long.MIN_VALUE;
            defense.observedAt = Long.MIN_VALUE;
        }
    }

    public static void clearLevel(ServerLevel level) {
        VEHICLES.keySet().removeIf(entity -> entity.level() == level);
    }

    private static boolean eligible(Mob mob, VehicleController controller) {
        return controller.level() instanceof ServerLevel
                && VehicleAiMemory.isVehicleAiEnabled(mob)
                && mob.isAlive() && !mob.isNoAi() && mob.getVehicle() == controller.entity()
                && controller.isValid() && controller.isControllable() && !controller.isWreck()
                && controller.supportsSmoke() && controller.canDrive(mob)
                && controller.controllingPassenger().orElse(null) == mob;
    }
}
