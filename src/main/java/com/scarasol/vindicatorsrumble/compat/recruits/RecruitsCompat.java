package com.scarasol.vindicatorsrumble.compat.recruits;

import com.scarasol.vindicatorsrumble.api.vehicle.DriveRequest;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.util.VehicleAiMemory;
import com.scarasol.vindicatorsrumble.util.VehicleAiTuning;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.entities.BowmanEntity;
import com.talhanation.recruits.entities.CaptainEntity;
import com.talhanation.recruits.entities.CrossBowmanEntity;
import com.talhanation.recruits.entities.IStrategicFire;
import com.talhanation.recruits.entities.SiegeEngineerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RecruitsCompat {
    private static final String STRATEGIC_FIRE = "vindicators_rumble:vehicle_strategic_fire";
    private static final Map<AbstractRecruitEntity, ArmorStand> STRATEGIC_TARGETS = new HashMap<>();

    private RecruitsCompat() {
    }

    public static boolean isPlayerAlly(Entity entity, Player player, boolean strict) {
        return entity instanceof AbstractRecruitEntity recruit && (!strict || recruit.isOwnedBy(player));
    }

    public static boolean usesVehicleNavigation(AbstractRecruitEntity recruit) {
        return VehicleAiMemory.isVehicleAiEnabled(recruit)
                && VehicleControllers.forMob(recruit)
                .filter(controller -> controller.isValid()
                        && (controller.usesGroundNavigation() || controller.supportsFlightControl())).isPresent();
    }

    public static void updateNavigation(Mob mob) {
        if (!(mob instanceof AbstractRecruitEntity recruit) || recruit.level().isClientSide()
                || !usesVehicleNavigation(recruit)) {
            return;
        }
        VehicleController controller = VehicleControllers.forMob(recruit).orElseThrow();
        DriveRequest request = DriveRequest.NONE;
        if (controller.canDrive(recruit) && recruit.isOwned() && !recruit.getFleeing()
                && !recruit.needsToGetFood() && !recruit.getShouldMount()) {
            if (recruit.getShouldMovePos() && recruit.getMovePos() != null) {
                Vec3 destination = Vec3.atBottomCenterOf(recruit.getMovePos());
                request = DriveRequest.moveTo(destination);
                double arrivalDistance = VehicleAiTuning.navigationArrivalDistance(controller, request.desiredDistance());
                if (!VehicleAiTuning.isValidVehicleDriveTarget(recruit, recruit.getTarget())
                        && horizontalDistanceSqr(controller.position(), destination)
                        <= arrivalDistance * arrivalDistance
                        && controller.velocity().multiply(1.0D, 0.0D, 1.0D).lengthSqr() < 0.0064D) {
                    recruit.setShouldMovePos(false);
                    recruit.clearMovePos();
                    recruit.reachedMovePos = true;
                    request = DriveRequest.NONE;
                }
            } else if (recruit.getShouldHoldPos() && recruit.getHoldPos() != null) {
                request = DriveRequest.moveTo(recruit.getHoldPos());
            } else if (recruit.getShouldFollow() && recruit.getFollowState() == 1) {
                Player owner = recruit.getOwner();
                if (owner != null && owner.isAlive() && !owner.isSpectator()
                        && owner.level() == recruit.level() && owner.getRootVehicle() != recruit.getRootVehicle()) {
                    request = DriveRequest.moveTo(owner, null);
                }
            }
        }
        if (request == DriveRequest.NONE) {
            VehicleAiMemory.clearNavigationDriveRequest(recruit);
        } else {
            VehicleAiMemory.setNavigationDriveRequest(recruit, request);
        }
    }

    private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return dx * dx + dz * dz;
    }

    public static void onStrategicFireCommand(Player player, UUID owner, AbstractRecruitEntity recruit,
                                              UUID group, boolean shouldFire) {
        if (recruit.level().isClientSide() || !owner.equals(player.getUUID())
                || !recruit.isEffectedByCommand(owner, group)
                || !VehicleAiMemory.isVehicleAiEnabled(recruit)) {
            return;
        }
        if (!shouldFire) {
            cancelCommand(recruit);
            return;
        }
        if (VehicleControllers.forMob(recruit).isEmpty()) {
            recruit.getPersistentData().remove(STRATEGIC_FIRE);
            releaseTarget(recruit);
            return;
        }
        HitResult hit = player.pick(200.0D, 1.0F, true);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            cancelCommand(recruit);
            return;
        }

        Vec3 position = hit.getLocation().add(Vec3.atLowerCornerOf(blockHit.getDirection().getNormal()).scale(0.125D));
        CompoundTag command = new CompoundTag();
        command.putUUID("Owner", owner);
        if (group != null) {
            command.putUUID("Group", group);
        }
        command.putDouble("X", position.x);
        command.putDouble("Y", position.y);
        command.putDouble("Z", position.z);
        recruit.getPersistentData().put(STRATEGIC_FIRE, command);
        updateStrategicFire(recruit);
    }

    public static void updateStrategicFire(Mob mob) {
        if (!(mob instanceof AbstractRecruitEntity recruit) || !(recruit.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag command = recruit.getPersistentData().getCompound(STRATEGIC_FIRE);
        if (command.isEmpty()) {
            releaseTarget(recruit);
            return;
        }
        if (!VehicleAiMemory.isVehicleAiEnabled(recruit) || !command.hasUUID("Owner")
                || !recruit.isEffectedByCommand(command.getUUID("Owner"),
                command.hasUUID("Group") ? command.getUUID("Group") : null)
                || !nativeStrategicFireEnabled(recruit)) {
            cancelCommand(recruit);
            return;
        }

        Vec3 position = new Vec3(command.getDouble("X"), command.getDouble("Y"), command.getDouble("Z"));
        double range = VehicleAiTuning.followRange(recruit);
        double distanceSqr = VehicleAiTuning.usesHorizontalTargetRange(recruit)
                ? VehicleAiTuning.horizontalDistanceToSqr(recruit, position.x, position.z)
                : recruit.distanceToSqr(position);
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
            cancelCommand(recruit);
            return;
        }
        if (VehicleControllers.forMob(recruit).filter(controller -> controller.isValid()).isEmpty()
                || !VehicleAiTuning.canAcquireTargets(recruit) || distanceSqr > range * range
                || !level.hasChunkAt(BlockPos.containing(position))) {
            releaseTarget(recruit);
            return;
        }

        ArmorStand target = STRATEGIC_TARGETS.get(recruit);
        if (target == null || !target.isAlive() || !target.getPersistentData().getCompound(STRATEGIC_FIRE).equals(command)) {
            releaseTarget(recruit);
            target = STRATEGIC_TARGETS.values().stream()
                    .filter(existing -> existing.isAlive() && existing.level() == level
                            && existing.getPersistentData().getCompound(STRATEGIC_FIRE).equals(command))
                    .findFirst().orElse(null);
            if (target == null) {
                target = createTarget(level, position, command);
                if (!level.addFreshEntity(target)) {
                    return;
                }
            }
            STRATEGIC_TARGETS.put(recruit, target);
        }
        if (!VehicleAiTuning.isValidCombatTarget(recruit, target)) {
            cancelCommand(recruit);
        } else if (recruit.getTarget() != target) {
            recruit.setTarget(target);
        }
    }

    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof AbstractRecruitEntity recruit)) {
            return;
        }
        ArmorStand target = STRATEGIC_TARGETS.get(recruit);
        if (VehicleAiMemory.isVehicleAiEnabled(recruit) && recruit.isPassenger()
                && VehicleAiTuning.isValidCombatTarget(recruit, target)) {
            event.setNewTarget(target);
        }
    }

    public static boolean isStrategicTarget(Entity entity) {
        return entity instanceof ArmorStand && (entity.level().isClientSide()
                ? entity.hasCustomName() && STRATEGIC_FIRE.equals(entity.getCustomName().getString())
                : entity.getTags().contains(STRATEGIC_FIRE));
    }

    public static void release(Entity entity) {
        if (entity instanceof AbstractRecruitEntity recruit) {
            releaseTarget(recruit);
        } else if (isStrategicTarget(entity)) {
            boolean destroyed = entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy();
            for (AbstractRecruitEntity recruit : new ArrayList<>(STRATEGIC_TARGETS.keySet())) {
                if (STRATEGIC_TARGETS.get(recruit) == entity) {
                    if (destroyed) {
                        cancelCommand(recruit);
                    } else {
                        releaseTarget(recruit);
                    }
                }
            }
        }
    }

    public static void clearLevel(Level level) {
        for (AbstractRecruitEntity recruit : new ArrayList<>(STRATEGIC_TARGETS.keySet())) {
            if (recruit.level() == level) {
                releaseTarget(recruit);
            }
        }
    }

    private static ArmorStand createTarget(ServerLevel level, Vec3 position, CompoundTag command) {
        ArmorStand target = new ArmorStand(level, position.x, position.y, position.z);
        CompoundTag flags = new CompoundTag();
        flags.putBoolean("Marker", true);
        target.readAdditionalSaveData(flags);
        target.setInvisible(true);
        target.setNoGravity(true);
        target.getAttribute(ForgeMod.ENTITY_GRAVITY.get()).setBaseValue(0.0D);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
        target.setHealth(100.0F);
        target.setCustomName(Component.literal(STRATEGIC_FIRE));
        target.addTag(STRATEGIC_FIRE);
        target.getPersistentData().put(STRATEGIC_FIRE, command.copy());
        return target;
    }

    private static boolean nativeStrategicFireEnabled(AbstractRecruitEntity recruit) {
        if (recruit instanceof BowmanEntity bowman) return bowman.getShouldStrategicFire();
        if (recruit instanceof CrossBowmanEntity crossbowman) return crossbowman.getShouldStrategicFire();
        if (recruit instanceof CaptainEntity captain) return captain.getShouldStrategicFire();
        if (recruit instanceof SiegeEngineerEntity engineer) return engineer.getShouldStrategicFire();
        return true;
    }

    private static void cancelCommand(AbstractRecruitEntity recruit) {
        recruit.getPersistentData().remove(STRATEGIC_FIRE);
        releaseTarget(recruit);
        if (recruit instanceof IStrategicFire strategicFire) {
            strategicFire.setShouldStrategicFire(false);
        }
    }

    private static void releaseTarget(AbstractRecruitEntity recruit) {
        ArmorStand target = STRATEGIC_TARGETS.remove(recruit);
        if (target == null) {
            return;
        }
        if (recruit.getTarget() == target) {
            recruit.setTarget(null);
            VehicleAiMemory.clear(recruit);
            VehicleControllers.forMob(recruit).ifPresent(controller -> controller.stopWeaponControl(recruit));
        }
        if (!target.isRemoved() && !STRATEGIC_TARGETS.containsValue(target)) {
            target.discard();
        }
    }
}
