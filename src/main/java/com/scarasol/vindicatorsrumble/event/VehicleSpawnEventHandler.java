package com.scarasol.vindicatorsrumble.event;

import com.scarasol.vindicatorsrumble.VindicatorsRumbleMod;
import com.scarasol.vindicatorsrumble.api.vehicle.SeatHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleMovementType;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.init.ModTags;
import com.scarasol.vindicatorsrumble.util.VehicleAiLogger;
import com.scarasol.vindicatorsrumble.util.WeightedRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = VindicatorsRumbleMod.MODID)
public class VehicleSpawnEventHandler {
    private static final ResourceLocation GUARD_VILLAGER = new ResourceLocation("guardvillagers", "guard");
    private static final String RECRUITS_NAMESPACE = "recruits";
    private static final int VILLAGE_SEARCH_SECTIONS = 2;
    private static final int SPAWN_ATTEMPTS = 16;
    private static final double MIN_SPAWN_RADIUS = 3.0D;
    private static final double SPAWN_RADIUS_RANDOM = 6.0D;
    private static final Set<UUID> SUPPRESSED_VEHICLE_SPAWN = new HashSet<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || event.loadedFromDisk()
                || !CommonConfig.ENABLE_VEHICLE_AI.get()
                || !(event.getEntity() instanceof Mob entity)
                || entity.isPassenger()) {
            return;
        }
        if (SUPPRESSED_VEHICLE_SPAWN.remove(entity.getUUID())) {
            return;
        }

        if (entity instanceof Raider raider && raider.getCurrentRaid() != null) {
            if (isEligibleRaidVindicator(entity)) {
                trySpawnConfiguredVehicle(level, entity, CommonConfig.VINDICATOR_RAID_VEHICLE_CHANCE.get(), CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.get());
            }
            return;
        }

        if (entity instanceof PatrollingMonster && entity.getSpawnType() == MobSpawnType.PATROL) {
            trySpawnConfiguredVehicle(level, entity, CommonConfig.PATROL_VEHICLE_CHANCE.get(), CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.get());
            return;
        }

        if (isVillageDefender(entity) && level.isCloseToVillage(entity.blockPosition(), VILLAGE_SEARCH_SECTIONS)) {
            trySpawnConfiguredVehicle(level, entity, CommonConfig.VILLAGE_DEFENDER_VEHICLE_CHANCE.get(), CommonConfig.VILLAGE_DEFENDER_VEHICLE_TYPES.get());
            return;
        }

        if (entity.getType().is(ModTags.EntityTypes.VEHICLE_AI_USERS)) {
            trySpawnConfiguredVehicle(level, entity, CommonConfig.NATURAL_VEHICLE_CHANCE.get(), CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.get());
        }
    }

    private static boolean isEligibleRaidVindicator(LivingEntity entity) {
        if (entity.getType() != EntityType.VINDICATOR || !(entity instanceof Raider raider)) {
            return false;
        }
        Raid raid = raider.getCurrentRaid();
        return raid != null
                && raider.getWave() >= CommonConfig.VINDICATOR_RAID_MIN_WAVE.get()
                && raid.getBadOmenLevel() >= CommonConfig.VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL.get();
    }

    private static boolean isVillageDefender(LivingEntity entity) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return GUARD_VILLAGER.equals(key) || key != null && RECRUITS_NAMESPACE.equals(key.getNamespace());
    }

    private static void trySpawnConfiguredVehicle(ServerLevel level, LivingEntity owner, double chance, List<? extends String> configuredTypes) {
        if (chance <= 0.0D || configuredTypes.isEmpty() || level.random.nextDouble() >= chance) {
            return;
        }

        Optional<EntityType<?>> type = chooseVehicleType(level.random, configuredTypes);
        if (type.isEmpty()) {
            VehicleAiLogger.debug(owner, "Vehicle spawn skipped: no configured vehicle entity type is registered");
            return;
        }

        spawnVehicleNear(level, owner, type.get());
    }

    private static Optional<EntityType<?>> chooseVehicleType(RandomSource random, List<? extends String> configuredTypes) {
        List<WeightedRandom.Entry<EntityType<?>>> validTypes = new ArrayList<>();
        for (String configuredType : configuredTypes) {
            Optional<WeightedRandom.Entry<EntityType<?>>> entry = parseVehicleType(configuredType);
            if (entry.isPresent()) {
                validTypes.add(entry.get());
            }
        }
        return WeightedRandom.pick(random, validTypes);
    }

    private static Optional<WeightedRandom.Entry<EntityType<?>>> parseVehicleType(String configuredType) {
        String[] parts = configuredType.trim().split(",");
        if (parts.length == 0) {
            return Optional.empty();
        }

        ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
        if (id == null) {
            return Optional.empty();
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null) {
            return Optional.empty();
        }

        double weight = 1.0D;
        if (parts.length >= 2) {
            try {
                weight = Double.parseDouble(parts[1].trim());
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
            if (weight <= 0.0D || !Double.isFinite(weight)) {
                return Optional.empty();
            }
        }
        return Optional.of(WeightedRandom.entry(type, weight));
    }

    private static void spawnVehicleNear(ServerLevel level, LivingEntity owner, EntityType<?> vehicleType) {
        Entity vehicle = vehicleType.create(level);
        if (vehicle == null) {
            VehicleAiLogger.debug(owner, "Vehicle spawn skipped: failed to create {}", ForgeRegistries.ENTITY_TYPES.getKey(vehicleType));
            return;
        }
        Optional<VehicleController> optionalController = VehicleControllers.create(vehicle);
        if (optionalController.isEmpty() || !optionalController.get().isControllable()) {
            return;
        }
        VehicleController controller = optionalController.get();
        boolean flying = controller.supportsFlightControl()
                || controller.movementType() == VehicleMovementType.HELICOPTER
                || controller.movementType() == VehicleMovementType.AIRCRAFT;
        boolean airborneHover = controller.movementType() == VehicleMovementType.HELICOPTER && controller.supportsFlightControl();
        if (flying && (level.dimensionType().hasCeiling() || !isOpenSky(level, owner.blockPosition()))) {
            VehicleAiLogger.debug(owner, "Vehicle spawn skipped: aircraft requires an open-sky owner position");
            return;
        }

        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            Vec3 position = randomSpawnPosition(level, owner.blockPosition(), level.random, vehicle, airborneHover);
            if (position == null) {
                continue;
            }
            controller.placeGenerationCandidate(position, level.random.nextFloat() * 360.0F);
            double radius = controller.horizontalFootprintRadius();
            AABB clearance = vehicle.getBoundingBox().minmax(new AABB(
                    position.x - radius, vehicle.getBoundingBox().minY, position.z - radius,
                    position.x + radius, vehicle.getBoundingBox().maxY, position.z + radius));
            if (clearance.minY < level.getMinBuildHeight() || clearance.maxY >= level.getMaxBuildHeight()
                    || !level.getWorldBorder().isWithinBounds(clearance)
                    || !level.hasChunksAt(BlockPos.containing(clearance.minX, clearance.minY, clearance.minZ),
                    BlockPos.containing(clearance.maxX, clearance.maxY, clearance.maxZ))
                    || !level.noCollision(vehicle, clearance)
                    || (controller.movementType() != VehicleMovementType.SHIP && level.containsAnyLiquid(clearance))
                    || (flying && !hasOpenSky(level, clearance, owner.getY()))
                    || (!flying && !hasGroundSupport(level, clearance, controller.movementType() == VehicleMovementType.SHIP))) {
                continue;
            }
            boolean added;
            SUPPRESSED_VEHICLE_SPAWN.add(vehicle.getUUID());
            try {
                added = level.addFreshEntity(vehicle);
            } finally {
                SUPPRESSED_VEHICLE_SPAWN.remove(vehicle.getUUID());
            }
            if (added) {
                VehicleAiLogger.debug(owner, "Spawned vehicle {} near generated mob airborneHover={}", ForgeRegistries.ENTITY_TYPES.getKey(vehicleType), airborneHover);
                boardGeneratedVehicle(level, owner, vehicle);
                return;
            }
            return;
        }

        VehicleAiLogger.debug(owner, "Vehicle spawn skipped: no collision-free nearby position for {}", ForgeRegistries.ENTITY_TYPES.getKey(vehicleType));
    }

    private static void boardGeneratedVehicle(ServerLevel level, LivingEntity owner, Entity vehicle) {
        if (!(owner instanceof Mob ownerMob)) {
            return;
        }

        Optional<VehicleController> controller = VehicleControllers.create(vehicle);
        if (controller.isEmpty()) {
            return;
        }

        VehicleController vehicleController = controller.get();
        vehicleController.initializeGeneratedSupplies();
        Optional<SeatHandle> driverSeat = vehicleController.seats().stream()
                .filter(SeatHandle::canDrive)
                .findFirst();
        Optional<SeatHandle> ownerSeat = driverSeat
                .or(() -> vehicleController.seats().stream().filter(SeatHandle::hasWeapon).findFirst())
                .or(() -> vehicleController.seats().stream().filter(seat -> !seat.occupied()).findFirst());
        if (ownerSeat.isEmpty() || !vehicleController.mount(ownerMob)) {
            VehicleAiLogger.debug(owner, "Generated vehicle boarding skipped: no usable owner seat on vehicle {}", vehicle.getId());
            return;
        }

        if (!vehicleController.moveToSeat(ownerMob, ownerSeat.get())) {
            VehicleAiLogger.debug(owner, "Generated vehicle boarding skipped: failed to move mob {} to seat {}", owner.getId(), ownerSeat.get().id());
            return;
        }
        if (ownerSeat.get().canDrive()
                && vehicleController.movementType() == VehicleMovementType.HELICOPTER
                && vehicleController.supportsFlightControl()) {
            vehicleController.initializeAirborneHover(ownerMob);
        }
        VehicleAiLogger.debug(owner, "Boarded generated vehicle {} seat {}", vehicle.getId(), ownerSeat.get().id());

        if (driverSeat.isPresent() && !driverSeat.get().hasWeapon()) {
            vehicleController.seats().stream()
                    .filter(SeatHandle::hasWeapon)
                    .filter(seat -> !seat.occupied())
                    .findFirst()
                    .ifPresent(seat -> spawnGeneratedPassenger(level, ownerMob, vehicleController, seat));
        }

        List<SeatHandle> passengerSeats = vehicleController.seats().stream()
                .filter(seat -> !seat.canDrive() && !seat.occupied())
                .sorted((first, second) -> Boolean.compare(second.hasWeapon(), first.hasWeapon()))
                .toList();
        double passengerChance = CommonConfig.GENERATED_VEHICLE_EXTRA_PASSENGER_CHANCE.get();
        for (SeatHandle seat : passengerSeats) {
            if (level.random.nextDouble() >= passengerChance
                    || !spawnGeneratedPassenger(level, ownerMob, vehicleController, seat)) {
                break;
            }
        }
    }

    private static boolean spawnGeneratedPassenger(ServerLevel level, Mob owner, VehicleController controller, SeatHandle seat) {
        Entity entity = owner.getType().create(level);
        if (!(entity instanceof Mob passenger)) {
            VehicleAiLogger.debug(owner, "Generated vehicle passenger skipped: failed to create same mob type for seat {}", seat.id());
            return false;
        }

        passenger.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), owner.getXRot());
        passenger.finalizeSpawn(level, level.getCurrentDifficultyAt(passenger.blockPosition()), MobSpawnType.EVENT, null, null);

        boolean added;
        SUPPRESSED_VEHICLE_SPAWN.add(passenger.getUUID());
        try {
            added = level.addFreshEntity(passenger);
        } finally {
            SUPPRESSED_VEHICLE_SPAWN.remove(passenger.getUUID());
        }
        if (!added) {
            VehicleAiLogger.debug(owner, "Generated vehicle passenger skipped: addFreshEntity failed for seat {}", seat.id());
            return false;
        }

        attachToOwnerRaid(owner, passenger);
        if (controller.mount(passenger) && controller.moveToSeat(passenger, seat)) {
            VehicleAiLogger.debug(owner, "Spawned generated vehicle passenger {} seat {}", passenger.getId(), seat.id());
            return true;
        }
        VehicleAiLogger.debug(owner, "Generated vehicle passenger {} failed to mount seat {}", passenger.getId(), seat.id());
        passenger.discard();
        return false;
    }

    private static void attachToOwnerRaid(Mob owner, Mob gunner) {
        if (!(owner instanceof Raider ownerRaider) || !(gunner instanceof Raider gunnerRaider)) {
            return;
        }
        Raid raid = ownerRaider.getCurrentRaid();
        if (raid == null) {
            return;
        }

        int wave = ownerRaider.getWave();
        raid.joinRaid(wave, gunnerRaider, null, true);
        gunnerRaider.applyRaidBuffs(wave, false);
    }

    private static Vec3 randomSpawnPosition(ServerLevel level, BlockPos origin, RandomSource random,
                                            Entity vehicle, boolean airborneHover) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = MIN_SPAWN_RADIUS + random.nextDouble() * SPAWN_RADIUS_RANDOM;
        int x = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
        int z = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);
        if (!level.hasChunkAt(new BlockPos(x, origin.getY(), z))) {
            return null;
        }
        // Search the owner's local floor, not the surface above an indoor or underground spawn.
        BlockHitResult floor = level.clip(new ClipContext(
                new Vec3(x + 0.5D, origin.getY() + 1.0D, z + 0.5D),
                new Vec3(x + 0.5D, origin.getY() - 4.0D, z + 0.5D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, vehicle));
        if (floor.getType() != HitResult.Type.BLOCK || floor.getDirection() != Direction.UP) {
            return null;
        }
        double y = floor.getLocation().y + 0.05D;
        if (airborneHover) {
            double maxY = level.getMaxBuildHeight() - Math.max(vehicle.getBbHeight(), 2.0F) - 1.0D;
            double spawnY = Math.max(level.getMinBuildHeight() + 1.0D,
                    Math.min(maxY, y + CommonConfig.HELICOPTER_HOVER_HEIGHT.get()));
            return new Vec3(x + 0.5D, spawnY, z + 0.5D);
        }
        return new Vec3(x + 0.5D, y, z + 0.5D);
    }

    private static boolean hasOpenSky(ServerLevel level, AABB clearance, double ownerY) {
        for (BlockPos column : BlockPos.betweenClosed(
                BlockPos.containing(clearance.minX, ownerY, clearance.minZ),
                BlockPos.containing(clearance.maxX, ownerY, clearance.maxZ))) {
            if (!isOpenSky(level, column)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOpenSky(ServerLevel level, BlockPos position) {
        return level.canSeeSky(position)
                && level.getHeight(Heightmap.Types.MOTION_BLOCKING, position.getX(), position.getZ()) <= position.getY();
    }

    private static boolean hasGroundSupport(ServerLevel level, AABB clearance, boolean allowWater) {
        for (double x : new double[]{clearance.minX + 0.1D, clearance.maxX - 0.1D}) {
            for (double z : new double[]{clearance.minZ + 0.1D, clearance.maxZ - 0.1D}) {
                BlockPos floor = BlockPos.containing(x, clearance.minY - 0.1D, z);
                AABB support = new AABB(x - 0.05D, floor.getY(), z - 0.05D,
                        x + 0.05D, clearance.minY, z + 0.05D);
                if (!level.getBlockCollisions(null, support).iterator().hasNext()
                        && !(allowWater && level.getFluidState(floor).is(FluidTags.WATER))) {
                    return false;
                }
            }
        }
        return true;
    }

    private VehicleSpawnEventHandler() {
    }
}
