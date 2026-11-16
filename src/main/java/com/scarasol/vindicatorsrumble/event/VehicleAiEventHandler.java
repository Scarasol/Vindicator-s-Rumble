package com.scarasol.vindicatorsrumble.event;

import com.scarasol.vindicatorsrumble.VindicatorsRumbleMod;
import com.scarasol.vindicatorsrumble.api.vehicle.SeatHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleThreats;
import com.scarasol.vindicatorsrumble.compat.ModCompat;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.compat.recruits.RecruitsCompat;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.common.VehicleAiGoal;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.DriveVehicleGoal;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.leave.LeaveVehicleGoal;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.mount.MountVehicleGoal;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.weapon.VehicleWeaponGoal;
import com.scarasol.vindicatorsrumble.init.ModTags;
import com.scarasol.vindicatorsrumble.util.AllegianceUtil;
import com.scarasol.vindicatorsrumble.util.VehicleAiLogger;
import com.scarasol.vindicatorsrumble.util.VehicleAiMemory;
import com.scarasol.vindicatorsrumble.util.VehicleAiTuning;
import com.scarasol.vindicatorsrumble.util.VehicleClaimManager;
import com.scarasol.vindicatorsrumble.util.VehicleTargetRangeManager;
import com.scarasol.vindicatorsrumble.util.VehicleSmokeDefense;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = VindicatorsRumbleMod.MODID)
public class VehicleAiEventHandler {
    private static final int DEBUG_TARGET_GLOW_DURATION = 12;
    private static final Map<Entity, Boolean> DEBUG_GLOWING_VEHICLES = new WeakHashMap<>();

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.loadedFromDisk() && ModCompat.isRecruitsLoaded()
                && RecruitsCompat.isStrategicTarget(event.getEntity())) {
            event.getEntity().discard();
            event.setCanceled(true);
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        boolean enabled = CommonConfig.ENABLE_VEHICLE_AI.get()
                && mob.getType().is(ModTags.EntityTypes.VEHICLE_AI_USERS);
        VehicleAiMemory.setVehicleAiEnabled(mob, enabled);
        if (!enabled) {
            return;
        }

        injectVehicleAiGoals(mob);
        if (mob.isPassenger() && VehicleControllers.forMob(mob).isPresent()) {
            VehicleTargetRangeManager.apply(mob);
        }
    }

    public static void releaseEntityResources(Entity entity) {
        if (entity.level().isClientSide()) {
            return;
        }

        VehicleThreats.clearEntity(entity);
        if (ModCompat.isRecruitsLoaded()) {
            RecruitsCompat.release(entity);
        }
        if (entity instanceof Mob mob) {
            clearDebugTargetGlow(mob);
            VehicleAiMemory.clear(mob);
            VehicleClaimManager.releaseAll(mob);
        }
        if (VehicleControllers.create(entity).isPresent()) {
            VehicleSmokeDefense.clear(entity);
            VehicleAiMemory.clearVehicle(entity);
            VehicleClaimManager.releaseVehicle(entity);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            VehicleThreats.clearLevel(level);
            VehicleSmokeDefense.clearLevel(level);
            if (ModCompat.isRecruitsLoaded()) RecruitsCompat.clearLevel(level);
            VehicleAiMemory.clearLevel(level);
            VehicleClaimManager.clearLevel(level);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level
                && level.getGameTime() % 20L == 0L) {
            VehicleThreats.expire(level);
            VehicleAiMemory.expire(level);
            VehicleClaimManager.expire(level);
        }
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!event.getEntity().level().isClientSide() && ModCompat.isRecruitsLoaded()) {
            RecruitsCompat.onTargetChange(event);
        }
    }

    public static void onVehicleDismounted(Entity passenger, Entity vehicle) {
        if (passenger.level().isClientSide() || !(passenger instanceof Mob mob)) {
            return;
        }
        // The rider is detached, but the old seat is still available until removePassenger runs.
        VehicleControllers.create(vehicle).ifPresent(controller -> {
            if (controller.canDrive(mob)) {
                VehicleSmokeDefense.clearDriver(vehicle);
            }
            controller.stopWeaponControl(mob);
        });
        if (ModCompat.isRecruitsLoaded()) {
            RecruitsCompat.release(mob);
        }
        setDebugVehicleGlow(vehicle, false);
        VehicleTargetRangeManager.remove(mob);
        VehicleAiMemory.clear(mob);
        VehicleClaimManager.releaseAll(mob);
    }

    public static void onVehicleMounted(Entity passenger, Entity vehicle) {
        if (passenger.level().isClientSide() || !(passenger instanceof Mob mob)
                || !VehicleAiMemory.isVehicleAiEnabled(mob)
                || mob.getVehicle() != vehicle || !vehicle.hasPassenger(mob)
                || VehicleControllers.create(vehicle).isEmpty()) {
            return;
        }
        VehicleTargetRangeManager.apply(mob);
    }

    private static void injectVehicleAiGoals(Mob mob) {
        mob.goalSelector.removeAllGoals(goal -> goal instanceof VehicleAiGoal);
        mob.goalSelector.addGoal(0, new LeaveVehicleGoal(mob));
        mob.goalSelector.addGoal(1, new DriveVehicleGoal(mob, DriveVehicleGoal.Mode.COMBAT));
        mob.goalSelector.addGoal(2, new MountVehicleGoal(mob));
        mob.goalSelector.addGoal(3, new VehicleWeaponGoal(mob));
        mob.goalSelector.addGoal(5, new DriveVehicleGoal(mob, DriveVehicleGoal.Mode.NAVIGATION));
        VehicleAiLogger.debug(mob, "Added vehicle AI goals");
    }

    @SubscribeEvent
    public static void onPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Player player = event.getEntity();
        if (player.isShiftKeyDown()
                || !hasNonFriendlyVehiclePassenger(player, event.getTarget())) {
            return;
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityMount(EntityMountEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.isMounting() && event.getEntityMounting() instanceof Player player) {
            if (hasNonFriendlyVehiclePassenger(player, event.getEntityBeingMounted())) {
                event.setCanceled(true);
            }
        }
    }

    private static boolean hasNonFriendlyVehiclePassenger(Player player, Entity vehicle) {
        return VehicleControllers.create(vehicle)
                .map(controller -> controller.passengers().stream()
                        .anyMatch(passenger -> passenger instanceof Mob mob && VehicleAiMemory.isVehicleAiEnabled(mob))
                        && AllegianceUtil.hasNonFriendlyPassenger(player, controller.passengers()))
                .orElse(false);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (!VehicleAiMemory.isVehicleAiEnabled(mob)) {
            return;
        }
        if (ModCompat.isRecruitsLoaded()) {
            RecruitsCompat.updateStrategicFire(mob);
            RecruitsCompat.updateNavigation(mob);
        }
        if (!mob.isPassenger()) {
            return;
        }

        var current = VehicleControllers.forMob(mob);
        current.filter(controller -> controller.canDrive(mob))
                .ifPresent(controller -> VehicleSmokeDefense.observe(mob, controller));

        LivingEntity target = mob.getTarget();
        boolean validTarget = VehicleAiTuning.isValidCombatTarget(mob, target);
        if (VehicleAiLogger.shouldLog(mob, 20)) {
            LivingEntity debugTarget = target;
            current.ifPresent(controller -> VehicleAiLogger.debug(mob,
                    "Vehicle passenger tick vehicle={} seat={} target={} followRange={} canAcquire={} validTarget={} validVehicleCombatTarget={} retainedDriveTarget={} distanceSqr={}",
                    controller.entity().getId(),
                    controller.seatOf(mob).map(SeatHandle::id).orElse(-1),
                    debugTarget == null ? -1 : debugTarget.getId(),
                    VehicleAiTuning.followRange(mob),
                    VehicleAiTuning.canAcquireTargets(mob),
                    validTarget,
                    validTarget,
                    validTarget,
                    debugTarget == null ? -1.0D : mob.distanceToSqr(debugTarget)));
        }

        if (validTarget) {
            applyDebugTargetGlow(mob);
            return;
        }

        clearDebugTargetGlow(mob);
        if (target != null) {
            VehicleAiLogger.debugEvery(mob, 20,
                    "Clearing invalid vehicle target {} effectiveFollowRange={} distanceSqr={} alive={}",
                    target.getId(),
                    VehicleAiTuning.followRange(mob),
                    VehicleAiTuning.targetDistanceToSqr(mob, target),
                    target.isAlive());
            mob.setTarget(null);
            VehicleAiMemory.clear(mob);
        }

        if (target != null || mob.tickCount % 20 == 0) {
            current.ifPresent(controller -> controller.stopWeaponControl(mob));
        }
    }

    private static void applyDebugTargetGlow(Mob mob) {
        if (!CommonConfig.VEHICLE_AI_DEBUG.get()) {
            return;
        }
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, DEBUG_TARGET_GLOW_DURATION, 0, true, false, false));
        setDebugVehicleGlow(mob.getVehicle(), true);
    }

    private static void clearDebugTargetGlow(Mob mob) {
        if (!CommonConfig.VEHICLE_AI_DEBUG.get()) {
            return;
        }
        setDebugVehicleGlow(mob.getVehicle(), false);
    }

    private static void setDebugVehicleGlow(Entity vehicle, boolean glowing) {
        if (vehicle == null) {
            return;
        }
        if (glowing) {
            DEBUG_GLOWING_VEHICLES.putIfAbsent(vehicle, vehicle.isCurrentlyGlowing());
            vehicle.setGlowingTag(true);
            return;
        }

        Boolean originalGlowing = DEBUG_GLOWING_VEHICLES.remove(vehicle);
        if (originalGlowing != null) {
            vehicle.setGlowingTag(originalGlowing);
        }
    }

}
