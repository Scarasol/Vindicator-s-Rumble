package com.scarasol.vindicatorsrumble.gametest;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleMovementType;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.common.VehicleAiGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

@GameTestHolder("vindicators_rumble")
@PrefixGameTestTemplate(false)
public final class VehicleSpawnGameTests {
    private static final String TRUCK = "superbwarfare:sodayo_pick_up_hmg";
    private static final String HELICOPTER = "superbwarfare:ah_6";

    @GameTest(batch = "vehicle_spawning", template = "empty", timeoutTicks = 40)
    public static void spawnRulesAndPlacementContract(GameTestHelper helper) {
        helper.assertTrue(CommonConfig.PATROL_VEHICLE_CHANCE.getDefault() == 0.02D,
                "Patrol vehicle generation must default to two percent");
        helper.assertTrue(CommonConfig.NATURAL_VEHICLE_CHANCE.getDefault() == 0.0D,
                "Ordinary tagged mob vehicle generation must be disabled by default");
        try (Fixture fixture = new Fixture(helper)) {
            CommonConfig.NATURAL_VEHICLE_CHANCE.set(0.0D);
            fixture.spawn(EntityType.VINDICATOR, MobSpawnType.NATURAL);
            fixture.expectVehicles(0, "Zero ordinary probability must disable generation");
            fixture.clearEntities();

            CommonConfig.NATURAL_VEHICLE_CHANCE.set(1.0D);
            Mob ordinary = fixture.spawn(EntityType.VINDICATOR, MobSpawnType.NATURAL);
            fixture.expectMounted(ordinary, TRUCK);
            fixture.expectVehicles(1, "A tagged natural spawn must generate exactly one vehicle");
            helper.assertTrue(fixture.level.noCollision(ordinary.getVehicle()),
                    "Generated truck intersects world geometry");
            fixture.clearEntities();

            fixture.spawn(EntityType.COW, MobSpawnType.NATURAL);
            fixture.expectVehicles(0, "An untagged mob must not generate a vehicle");
            fixture.clearEntities();

            fixture.expectMounted(fixture.spawn(EntityType.VINDICATOR, MobSpawnType.SPAWN_EGG), TRUCK);
            fixture.clearEntities();
            fixture.expectMounted(fixture.spawn(EntityType.VINDICATOR, MobSpawnType.COMMAND), TRUCK);
            fixture.clearEntities();

            Mob loaded = fixture.create(EntityType.VINDICATOR, MobSpawnType.NATURAL);
            fixture.level.addLegacyChunkEntities(Stream.of(loaded));
            fixture.expectVehicles(0, "Saved entities must not generate vehicles when loaded");
            fixture.clearEntities();

            CommonConfig.ENABLE_VEHICLE_AI.set(false);
            fixture.spawn(EntityType.VINDICATOR, MobSpawnType.NATURAL);
            fixture.expectVehicles(0, "The master switch must disable generation");
            CommonConfig.ENABLE_VEHICLE_AI.set(true);
            fixture.clearEntities();

            // Ordinary probability remains one: a special rule must never get a second roll.
            CommonConfig.PATROL_VEHICLE_CHANCE.set(0.0D);
            fixture.spawn(EntityType.PILLAGER, MobSpawnType.PATROL);
            fixture.expectVehicles(0, "Disabled patrol generation fell through to ordinary generation");
            fixture.clearEntities();

            CommonConfig.NATURAL_VEHICLE_CHANCE.set(0.0D);
            CommonConfig.PATROL_VEHICLE_CHANCE.set(1.0D);
            fixture.spawn(EntityType.PILLAGER, MobSpawnType.COMMAND);
            fixture.expectVehicles(0, "An ordinary pillager was misidentified as a patrol member");
            fixture.clearEntities();

            Pillager follower = fixture.spawn(EntityType.PILLAGER, MobSpawnType.PATROL);
            fixture.expectMounted(follower, TRUCK);
            helper.assertTrue(follower.goalSelector.getAvailableGoals().stream()
                            .anyMatch(goal -> goal.getGoal() instanceof VehicleAiGoal),
                    "Patrol pillager did not receive vehicle AI");
            fixture.expectVehicles(1, "Patrol passengers recursively generated vehicles");
            fixture.clearEntities();

            Pillager leader = fixture.create(EntityType.PILLAGER, MobSpawnType.PATROL);
            leader.setPatrolLeader(true);
            leader.findPatrolTarget();
            helper.assertTrue(fixture.level.addFreshEntity(leader), "Could not add patrol leader fixture");
            fixture.expectMounted(leader, TRUCK);
            fixture.clearEntities();

            CommonConfig.NATURAL_VEHICLE_CHANCE.set(1.0D);
            Raid raid = new Raid(9981, fixture.level, fixture.origin);
            Raider raider = fixture.create(EntityType.VINDICATOR, MobSpawnType.EVENT);
            raider.setCurrentRaid(raid);
            helper.assertTrue(fixture.level.addFreshEntity(raider), "Could not add raid fixture");
            fixture.expectVehicles(0, "A low-level raid bypassed its threshold using ordinary probability");
            fixture.clearEntities();

            CommonConfig.VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL.set(0);
            Raider disabledRaid = fixture.create(EntityType.VINDICATOR, MobSpawnType.EVENT);
            disabledRaid.setCurrentRaid(raid);
            helper.assertTrue(fixture.level.addFreshEntity(disabledRaid), "Could not add disabled raid fixture");
            fixture.expectVehicles(0, "A disabled eligible raid received a second ordinary roll");
            fixture.clearEntities();

            CommonConfig.VINDICATOR_RAID_VEHICLE_CHANCE.set(1.0D);
            Raider eligibleRaid = fixture.create(EntityType.VINDICATOR, MobSpawnType.EVENT);
            eligibleRaid.setCurrentRaid(raid);
            helper.assertTrue(fixture.level.addFreshEntity(eligibleRaid), "Could not add eligible raid fixture");
            fixture.expectMounted(eligibleRaid, TRUCK);
            fixture.expectVehicles(1, "Raid generation duplicated vehicles");
            fixture.clearEntities();

            fixture.ceiling(Blocks.STONE.defaultBlockState());
            Mob indoors = fixture.spawn(EntityType.VINDICATOR, MobSpawnType.NATURAL);
            fixture.expectMounted(indoors, TRUCK);
            helper.assertTrue(Math.abs(indoors.getVehicle().getY() - fixture.origin.getY()) < 1.0D,
                    "An indoor truck was relocated onto the roof");
            fixture.clearEntities();

            CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(List.of(HELICOPTER));
            fixture.spawn(EntityType.VINDICATOR, MobSpawnType.NATURAL);
            fixture.expectVehicles(0, "An aircraft generated beneath a solid ceiling");
            fixture.clearEntities();
            fixture.ceiling(Blocks.GLASS.defaultBlockState());
            fixture.spawn(EntityType.VINDICATOR, MobSpawnType.NATURAL);
            fixture.expectVehicles(0, "An aircraft generated beneath a transparent ceiling");
            fixture.clearEntities();
            EntityType<?> fixedWingType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("superbwarfare:a_10a"));
            helper.assertTrue(fixedWingType != null, "Missing fixed-wing aircraft fixture");
            Entity fixedWing = fixedWingType.create(fixture.level);
            helper.assertTrue(fixedWing != null && VehicleControllers.create(fixedWing)
                            .map(controller -> controller.movementType() == VehicleMovementType.AIRCRAFT).orElse(false),
                    "Fixed-wing fixture is not an aircraft");
            CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(List.of("superbwarfare:a_10a"));
            fixture.spawn(EntityType.VINDICATOR, MobSpawnType.NATURAL);
            fixture.expectVehicles(0, "A fixed-wing aircraft generated beneath a transparent ceiling");
            fixture.clearEntities();

            fixture.ceiling(Blocks.AIR.defaultBlockState());
            CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(List.of(HELICOPTER));
            Mob outdoors = fixture.spawn(EntityType.VINDICATOR, MobSpawnType.NATURAL);
            fixture.expectMounted(outdoors, HELICOPTER);
            fixture.expectVehicles(1, "Open-air generation duplicated aircraft");
            helper.assertTrue(outdoors.getVehicle().getY() > fixture.origin.getY() + 5.0D,
                    "Generated helicopter did not start airborne");
            helper.assertTrue(fixture.level.noCollision(outdoors.getVehicle()),
                    "Generated helicopter intersects world geometry");
            fixture.clearEntities();

            // The owner is exposed, but every nearby aircraft footprint is beneath a roof.
            fixture.ceiling(Blocks.STONE.defaultBlockState());
            fixture.set(new BlockPos(0, 6, 0), Blocks.AIR.defaultBlockState());
            helper.assertTrue(fixture.level.canSeeSky(fixture.origin), "Exposed-owner fixture is covered");
            fixture.spawn(EntityType.VINDICATOR, MobSpawnType.NATURAL);
            fixture.expectVehicles(0, "Aircraft ignored coverage at its candidate position");
            fixture.clearEntities();
            fixture.ceiling(Blocks.AIR.defaultBlockState());

            CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(List.of(TRUCK));
            fixture.fill(new BlockPos(-14, 0, -14), new BlockPos(14, 4, 14), Blocks.STONE.defaultBlockState());
            fixture.fill(new BlockPos(-1, 0, -1), new BlockPos(1, 2, 1), Blocks.AIR.defaultBlockState());
            Mob cramped = fixture.spawn(EntityType.VINDICATOR, MobSpawnType.NATURAL);
            helper.assertTrue(fixture.level.noCollision(cramped), "Cramped owner fixture intersects blocks");
            fixture.expectVehicles(0, "A truck generated in insufficient space or on top of the enclosure");
            fixture.clearEntities();
            fixture.fill(new BlockPos(-14, 0, -14), new BlockPos(14, 4, 14), Blocks.AIR.defaultBlockState());

            fixture.fill(new BlockPos(-20, -1, -20), new BlockPos(20, -1, 20), Blocks.AIR.defaultBlockState());
            fixture.spawn(EntityType.VINDICATOR, MobSpawnType.NATURAL);
            fixture.expectVehicles(0, "A ground vehicle generated without local floor support");
            fixture.clearEntities();
            fixture.fill(new BlockPos(-20, -1, -20), new BlockPos(20, -1, 20), Blocks.STONE.defaultBlockState());

            fixture.fill(new BlockPos(-20, -1, -20), new BlockPos(20, -1, 20), Blocks.WATER.defaultBlockState());
            fixture.spawn(EntityType.VINDICATOR, MobSpawnType.NATURAL);
            fixture.expectVehicles(0, "A ground vehicle used water as floor support");
            fixture.clearEntities();
            CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(List.of("superbwarfare:tiny_speedboat"));
            Mob sailor = fixture.spawn(EntityType.VINDICATOR, MobSpawnType.NATURAL);
            fixture.expectMounted(sailor, "superbwarfare:tiny_speedboat");
            helper.assertTrue(Math.abs(sailor.getVehicle().getY() - fixture.origin.getY()) < 1.0D,
                    "A ship generated below the water surface");
            fixture.clearEntities();
            fixture.fill(new BlockPos(-20, -1, -20), new BlockPos(20, -1, 20), Blocks.STONE.defaultBlockState());
            CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(List.of(TRUCK));

            Consumer<EntityJoinLevelEvent> rejectVehicle = event -> {
                if (VehicleControllers.create(event.getEntity()).isPresent()) {
                    event.setCanceled(true);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, rejectVehicle);
            try {
                Mob rejected = fixture.spawn(EntityType.VINDICATOR, MobSpawnType.NATURAL);
                fixture.expectVehicles(0, "A canceled vehicle was still added");
                helper.assertTrue(!rejected.isPassenger(), "Owner boarded a vehicle rejected by another mod");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(rejectVehicle);
            }
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos origin;
        private final AABB area;
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private final boolean ai = CommonConfig.ENABLE_VEHICLE_AI.get();
        private final boolean debug = CommonConfig.VEHICLE_AI_DEBUG.get();
        private final double patrolChance = CommonConfig.PATROL_VEHICLE_CHANCE.get();
        private final double naturalChance = CommonConfig.NATURAL_VEHICLE_CHANCE.get();
        private final double raidChance = CommonConfig.VINDICATOR_RAID_VEHICLE_CHANCE.get();
        private final int omenLevel = CommonConfig.VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL.get();
        private final double extraChance = CommonConfig.GENERATED_VEHICLE_EXTRA_PASSENGER_CHANCE.get();
        private final List<? extends String> vehicleTypes = CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.get();

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.origin = helper.absolutePos(new BlockPos(0, 8, 0));
            this.area = new AABB(origin).inflate(32.0D, 80.0D, 32.0D);
            CommonConfig.ENABLE_VEHICLE_AI.set(true);
            CommonConfig.VEHICLE_AI_DEBUG.set(false);
            CommonConfig.VINDICATOR_RAID_VEHICLE_CHANCE.set(0.0D);
            CommonConfig.VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL.set(4);
            CommonConfig.GENERATED_VEHICLE_EXTRA_PASSENGER_CHANCE.set(1.0D);
            CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(List.of(TRUCK));
            fill(new BlockPos(-20, -1, -20), new BlockPos(20, -1, 20), Blocks.STONE.defaultBlockState());
        }

        private <T extends Mob> T create(EntityType<T> type, MobSpawnType reason) {
            T mob = type.create(level);
            helper.assertTrue(mob != null, "Missing mob fixture " + type);
            mob.moveTo(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D, 0.0F, 0.0F);
            ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(origin), reason, null, null);
            return mob;
        }

        private <T extends Mob> T spawn(EntityType<T> type, MobSpawnType reason) {
            T mob = create(type, reason);
            helper.assertTrue(level.addFreshEntity(mob), "Could not add mob fixture " + type);
            return mob;
        }

        private void expectMounted(Mob owner, String type) {
            Entity vehicle = owner.getVehicle();
            helper.assertTrue(vehicle != null, "Generated owner did not board " + type);
            helper.assertTrue(new ResourceLocation(type).equals(ForgeRegistries.ENTITY_TYPES.getKey(vehicle.getType())),
                    "Owner boarded the wrong vehicle type");
            VehicleController controller = VehicleControllers.create(vehicle).orElseThrow();
            helper.assertTrue(controller.canDrive(owner), "Generated owner did not occupy a driver seat");
        }

        private void expectVehicles(int expected, String message) {
            int actual = level.getEntities((Entity) null, area,
                    entity -> VehicleControllers.create(entity).isPresent()).size();
            helper.assertTrue(actual == expected, message + ": expected=" + expected + " actual=" + actual);
        }

        private void clearEntities() {
            for (Entity entity : level.getEntities((Entity) null, area, entity -> true)) {
                entity.discard();
            }
        }

        private void ceiling(BlockState state) {
            fill(new BlockPos(-20, 6, -20), new BlockPos(20, 6, 20), state);
        }

        private void fill(BlockPos from, BlockPos to, BlockState state) {
            for (BlockPos position : BlockPos.betweenClosed(from, to)) {
                set(position, state);
            }
        }

        private void set(BlockPos relative, BlockState state) {
            BlockPos absolute = origin.offset(relative);
            blocks.putIfAbsent(absolute, level.getBlockState(absolute));
            level.setBlockAndUpdate(absolute, state);
        }

        @Override
        public void close() {
            clearEntities();
            blocks.forEach(level::setBlockAndUpdate);
            CommonConfig.ENABLE_VEHICLE_AI.set(ai);
            CommonConfig.VEHICLE_AI_DEBUG.set(debug);
            CommonConfig.PATROL_VEHICLE_CHANCE.set(patrolChance);
            CommonConfig.NATURAL_VEHICLE_CHANCE.set(naturalChance);
            CommonConfig.VINDICATOR_RAID_VEHICLE_CHANCE.set(raidChance);
            CommonConfig.VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL.set(omenLevel);
            CommonConfig.GENERATED_VEHICLE_EXTRA_PASSENGER_CHANCE.set(extraChance);
            CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(vehicleTypes);
        }
    }
}
