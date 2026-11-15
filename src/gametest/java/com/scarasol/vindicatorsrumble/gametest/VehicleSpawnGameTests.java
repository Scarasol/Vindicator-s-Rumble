package com.scarasol.vindicatorsrumble.gametest;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleMovementType;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwGameTestVehicle;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.common.VehicleAiGoal;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
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
import java.util.ArrayList;
import java.util.stream.Stream;

@GameTestHolder("vindicators_rumble")
@PrefixGameTestTemplate(false)
public final class VehicleSpawnGameTests {
    private static final String TRUCK = "superbwarfare:sodayo_pick_up_hmg";
    private static final String HELICOPTER = "superbwarfare:ah_6";
    private static final List<Runnable> PENDING_CLEANUP = new ArrayList<>();

    @AfterBatch(batch = "tank_spawn")
    public static void cleanTankSpawning(ServerLevel level) { cleanFixtures(); }

    @AfterBatch(batch = "tank_combat")
    public static void cleanTankCombat(ServerLevel level) { cleanFixtures(); }

    @AfterBatch(batch = "raid_ground_travel")
    public static void cleanGroundRaid(ServerLevel level) { cleanFixtures(); }

    @AfterBatch(batch = "raid_flight_travel")
    public static void cleanFlightRaid(ServerLevel level) { cleanFixtures(); }

    @AfterBatch(batch = "raid_navigation_status")
    public static void cleanRaidNavigationStatus(ServerLevel level) { cleanFixtures(); }

    @AfterBatch(batch = "raid_village_arrival")
    public static void cleanVillageArrival(ServerLevel level) { cleanFixtures(); }

    private static void cleanFixtures() {
        List.copyOf(PENDING_CLEANUP).forEach(Runnable::run);
        PENDING_CLEANUP.clear();
    }

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
            fixture.spawnRaidVindicator(raid, 2);
            fixture.expectVehicles(0, "A low-level raid bypassed its threshold using ordinary probability");
            fixture.clearEntities();

            CommonConfig.VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL.set(0);
            fixture.spawnRaidVindicator(raid, 2);
            fixture.expectVehicles(0, "A disabled eligible raid received a second ordinary roll");
            fixture.clearEntities();

            CommonConfig.VINDICATOR_RAID_VEHICLE_CHANCE.set(1.0D);
            Raider eligibleRaid = fixture.spawnRaidVindicator(raid, 2);
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

    @GameTest(batch = "vehicle_spawning", template = "empty", timeoutTicks = 40)
    public static void raidVehicleMinimumWaveContract(GameTestHelper helper) {
        helper.assertTrue(CommonConfig.VINDICATOR_RAID_MIN_WAVE.getDefault() == 2,
                "Raid vehicles must become eligible starting with the second wave by default");
        try (Fixture fixture = new Fixture(helper)) {
            CommonConfig.VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL.set(0);
            CommonConfig.VINDICATOR_RAID_VEHICLE_CHANCE.set(1.0D);
            CommonConfig.NATURAL_VEHICLE_CHANCE.set(1.0D);
            CommonConfig.PATROL_VEHICLE_CHANCE.set(1.0D);
            Raid raid = new Raid(9982, fixture.level, fixture.origin);

            fixture.spawnRaidVindicator(raid, 1);
            fixture.expectVehicles(0, "The first wave bypassed the default threshold or received an ordinary roll");
            fixture.clearEntities();

            for (int wave : List.of(2, 3)) {
                fixture.expectMounted(fixture.spawnRaidVindicator(raid, wave), TRUCK);
                fixture.expectVehicles(1, "An eligible raid wave must generate exactly one vehicle");
                fixture.clearEntities();
            }

            CommonConfig.VINDICATOR_RAID_MIN_WAVE.set(1);
            fixture.expectMounted(fixture.spawnRaidVindicator(raid, 1), TRUCK);
            fixture.expectVehicles(1, "A configured first-wave start must allow vehicle generation");
            fixture.clearEntities();

            CommonConfig.VINDICATOR_RAID_MIN_WAVE.set(4);
            fixture.spawnRaidVindicator(raid, 3);
            fixture.expectVehicles(0, "A custom start wave was ignored");
            fixture.clearEntities();
            fixture.expectMounted(fixture.spawnRaidVindicator(raid, 4), TRUCK);
            fixture.expectVehicles(1, "The configured start wave must be included");
            fixture.clearEntities();

            fixture.expectMounted(fixture.spawn(EntityType.VINDICATOR, MobSpawnType.NATURAL), TRUCK);
            fixture.expectVehicles(1, "The raid wave threshold must not restrict ordinary generation");
            fixture.clearEntities();
            fixture.expectMounted(fixture.spawn(EntityType.PILLAGER, MobSpawnType.PATROL), TRUCK);
            fixture.expectVehicles(1, "The raid wave threshold must not restrict patrol generation");
            fixture.clearEntities();

            CommonConfig.VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL.set(4);
            fixture.spawnRaidVindicator(raid, 4);
            fixture.expectVehicles(0, "Meeting the wave threshold must not bypass the Bad Omen threshold");
        }
        helper.succeed();
    }

    @GameTest(batch = "tank_spawn", template = "empty", timeoutTicks = 100)
    public static void generatedTanksStayAboveSolidGround(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        PENDING_CLEANUP.add(fixture::close);
        fixture.fill(new BlockPos(-20, -5, -20), new BlockPos(20, -1, 20), Blocks.STONE.defaultBlockState());
        CommonConfig.NATURAL_VEHICLE_CHANCE.set(1.0D);
        List<Entity> tanks = new ArrayList<>();
        String[] types = {"yx_100", "m_1a_2", "t_90a"};
        int[] index = {0};
        Runnable spawn = () -> {
            fixture.clearEntities();
            tanks.clear();
            CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(List.of("superbwarfare:" + types[index[0]]));
            fixture.level.random.setSeed(48219L + index[0]);
            Mob owner = fixture.spawn(EntityType.VINDICATOR, MobSpawnType.NATURAL);
            helper.assertTrue(owner.isPassenger(), "Could not generate tank fixture " + types[index[0]]);
            Entity tank = owner.getVehicle();
            tanks.add(tank);
            tank.getPassengers().forEach(passenger -> {
                if (passenger instanceof Mob mob) mob.setNoAi(true);
            });
            LogUtils.getLogger().info("Generated tank initial type={} position={} box={}",
                    types[index[0]], tank.position(), tank.getBoundingBox());
        };
        spawn.run();
        helper.onEachTick(() -> {
            Entity tank = tanks.get(0);
            helper.assertTrue(tank.getY() >= fixture.origin.getY() - 0.2D,
                    "Generated tank sank into solid ground: type=" + types[index[0]] + " position=" + tank.position());
            if (helper.getTick() % 25 != 24) return;
            helper.assertTrue(!fixture.level.getBlockCollisions(tank, tank.getBoundingBox().deflate(0.05D)).iterator().hasNext(),
                    "Generated tank intersects solid ground after settling: " + types[index[0]]);
            if (++index[0] == types.length) {
                fixture.close();
                helper.succeed();
            } else {
                spawn.run();
            }
        });
    }

    @GameTest(batch = "tank_spawn_initial", template = "empty", timeoutTicks = 40)
    public static void freshTankPhysicalCollisionIsAtItsGeneratedPosition(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            CommonConfig.NATURAL_VEHICLE_CHANCE.set(1.0D);
            CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(List.of("superbwarfare:yx_100"));
            Mob owner = fixture.spawn(EntityType.VINDICATOR, MobSpawnType.NATURAL);
            helper.assertTrue(owner.isPassenger(), "Could not generate initial tank fixture");
            Entity tank = owner.getVehicle();
            AABB collision = SbwGameTestVehicle.physicalCollisionBounds(tank);
            helper.assertTrue(collision.intersects(tank.getBoundingBox()),
                    "Generated tank physics still refers to an old position: collision=" + collision + " entity=" + tank.getBoundingBox());
            helper.assertTrue(!fixture.level.getBlockCollisions(tank, collision).iterator().hasNext(),
                    "Freshly generated tank's actual collision shape intersects terrain");
        }
        helper.succeed();
    }

    @GameTest(batch = "tank_armor_aim", template = "empty", timeoutTicks = 40)
    public static void heavyWeaponTargetsArmorBodyRatherThanGroundUnderneath(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            CommonConfig.NATURAL_VEHICLE_CHANCE.set(1.0D);
            CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(List.of("superbwarfare:yx_100"));
            Mob driver = fixture.spawn(EntityType.VINDICATOR, MobSpawnType.NATURAL);
            helper.assertTrue(driver.isPassenger(), "Could not generate armor aim fixture");
            VehicleController controller = VehicleControllers.forMob(driver).orElseThrow();
            Entity targetVehicle = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(TRUCK)).create(fixture.level);
            helper.assertTrue(targetVehicle != null, "Could not create armor target");
            Mob target = EntityType.VILLAGER.create(fixture.level);
            helper.assertTrue(target != null, "Could not create armor passenger");
            try {
                targetVehicle.moveTo(fixture.origin.getX(), fixture.origin.getY() + 0.05D,
                        fixture.origin.getZ() + 32.0D, 0.0F, 0.0F);
                targetVehicle.setOnGround(true);
                fixture.level.addFreshEntity(targetVehicle);
                target.moveTo(targetVehicle.position());
                fixture.level.addFreshEntity(target);
                helper.assertTrue(VehicleControllers.create(targetVehicle).orElseThrow().mount(target),
                        "Could not mount armor passenger");
                var shell = controller.weapons(driver).stream()
                        .filter(weapon -> weapon.payloadType().isHeavyTargetPreferred()).findFirst().orElseThrow();
                Vec3 point = controller.aimPoint(driver, target, shell);
                helper.assertTrue(point.distanceTo(targetVehicle.getBoundingBox().getCenter()) < 0.1D,
                        "Armor shot aimed below the visible vehicle body: aim=" + point + " body=" + targetVehicle.getBoundingBox());
            } finally {
                target.discard();
                targetVehicle.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(batch = "tank_combat", template = "empty", timeoutTicks = 300)
    public static void tankCannonFiresAtAGroundedVehicle(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        PENDING_CLEANUP.add(fixture::close);
        fixture.fill(new BlockPos(-20, -5, -20), new BlockPos(20, -1, 65), Blocks.STONE.defaultBlockState());
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(true);
        CommonConfig.NATURAL_VEHICLE_CHANCE.set(1.0D);
        CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(List.of("superbwarfare:yx_100"));
        Mob driver = fixture.spawn(EntityType.VINDICATOR, MobSpawnType.NATURAL);
        helper.assertTrue(driver.isPassenger(), "Could not generate attacking tank");
        Entity tank = driver.getVehicle();
        CommonConfig.NATURAL_VEHICLE_CHANCE.set(0.0D);
        Entity targetVehicle = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(TRUCK)).create(fixture.level);
        helper.assertTrue(targetVehicle != null, "Could not create target truck");
        targetVehicle.moveTo(fixture.origin.getX() + 0.5D, fixture.origin.getY() + 0.05D,
                fixture.origin.getZ() + 38.5D, 0.0F, 0.0F);
        fixture.level.addFreshEntity(targetVehicle);
        Mob target = EntityType.VILLAGER.create(fixture.level);
        helper.assertTrue(target != null, "Could not create hostile-to-raiders target");
        target.moveTo(targetVehicle.position());
        target.setNoAi(true);
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 1000, 10, false, false));
        fixture.level.addFreshEntity(target);
        helper.assertTrue(VehicleControllers.create(targetVehicle).orElseThrow().mount(target), "Could not mount target truck");
        List<Entity> shots = new ArrayList<>();
        Consumer<EntityJoinLevelEvent> observer = event -> {
            if (event.getEntity() instanceof Projectile projectile && projectile.getOwner() == driver
                    && ForgeRegistries.ENTITY_TYPES.getKey(projectile.getType()).getPath().contains("shell")) {
                shots.add(projectile);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(observer);
        PENDING_CLEANUP.add(() -> {
            MinecraftForge.EVENT_BUS.unregister(observer);
            shots.forEach(Entity::discard);
            target.discard();
            targetVehicle.discard();
        });
        int[] acquired = {-1};
        helper.onEachTick(() -> {
            if (driver.getTarget() == target && acquired[0] < 0) acquired[0] = (int) helper.getTick();
            if (acquired[0] < 0) {
                helper.assertTrue(helper.getTick() < 80, "Tank never naturally acquired the visible vehicle passenger");
                return;
            }
            if (!shots.isEmpty()) {
                LogUtils.getLogger().info("Tank cannon combat shotTick={} tankY={} targetY={}",
                        helper.getTick(), tank.getY(), targetVehicle.getY());
                helper.succeed();
                return;
            }
            helper.assertTrue(helper.getTick() - acquired[0] < 180, "Tank cannon never fired at a visible grounded vehicle");
        });
    }

    @GameTest(batch = "raid_ground_travel", template = "empty", timeoutTicks = 400)
    public static void raidTankAdvancesTowardVillageWithoutACombatTarget(GameTestHelper helper) {
        verifyRaidTravel(helper, "yx_100", false);
    }

    @GameTest(batch = "raid_flight_travel", template = "empty", timeoutTicks = 500)
    public static void raidHelicopterAdvancesTowardVillageWithoutACombatTarget(GameTestHelper helper) {
        verifyRaidTravel(helper, "ah_6", true);
    }

    @GameTest(batch = "raid_navigation_status", template = "empty", timeoutTicks = 100)
    public static void nativeRaidGoalSeesDelegatedVehicleNavigationInProgress(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        PENDING_CLEANUP.add(fixture::close);
        fixture.fill(new BlockPos(-20, -5, -20), new BlockPos(20, -1, 110), Blocks.STONE.defaultBlockState());
        CommonConfig.VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL.set(0);
        CommonConfig.VINDICATOR_RAID_VEHICLE_CHANCE.set(1.0D);
        CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(List.of("superbwarfare:yx_100"));
        Raider driver = fixture.spawnRaidVindicator(new Raid(9984, fixture.level, fixture.origin.offset(0, 0, 90)), 2);
        helper.assertTrue(driver.isPassenger(), "Could not generate raid navigation-status fixture");
        Vec3 initial = driver.getVehicle().position();
        helper.onEachTick(() -> {
            helper.assertTrue(driver.getTarget() == null, "Raid navigation-status fixture acquired a combat target");
            if (driver.getVehicle().position().subtract(initial).horizontalDistance() < 2.0D) return;
            helper.assertTrue(driver.isPathFinding() && !driver.getNavigation().isDone(),
                    "Vanilla raid goal sees active delegated vehicle navigation as finished");
            BlockPos waypoint = driver.getNavigation().getTargetPos();
            helper.assertTrue(waypoint != null && waypoint.distSqr(driver.getCurrentRaid().getCenter()) > 400.0D,
                    "Vehicle navigation replaced the native intermediate raid waypoint with the raid center");
            helper.succeed();
        });
    }

    @GameTest(batch = "raid_village_arrival", template = "empty", timeoutTicks = 700)
    public static void raidTankReachesAnOccupiedVillagePoi(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        PENDING_CLEANUP.add(fixture::close);
        fixture.fill(new BlockPos(-24, -5, -20), new BlockPos(24, -1, 115), Blocks.STONE.defaultBlockState());
        BlockPos home = fixture.origin.offset(0, 0, 90);
        fixture.set(new BlockPos(0, 0, 90), Blocks.RED_BED.defaultBlockState());
        fixture.level.getPoiManager().add(home, fixture.level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.POINT_OF_INTEREST_TYPE)
                .getHolderOrThrow(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME));
        helper.assertTrue(fixture.level.getPoiManager().take(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME),
                        (type, pos) -> pos.equals(home), home, 1).isPresent(), "Could not occupy the village POI");
        PENDING_CLEANUP.add(() -> fixture.level.getPoiManager().remove(home));
        CommonConfig.VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL.set(0);
        CommonConfig.VINDICATOR_RAID_VEHICLE_CHANCE.set(1.0D);
        CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(List.of("superbwarfare:yx_100"));
        Raider driver = fixture.spawnRaidVindicator(new Raid(9985, fixture.level, home), 2);
        helper.assertTrue(driver.isPassenger(), "Could not generate village-arrival tank");
        Entity tank = driver.getVehicle();
        PENDING_CLEANUP.add(() -> {
            List.copyOf(tank.getPassengers()).forEach(Entity::discard);
            tank.discard();
        });
        helper.onEachTick(() -> {
            helper.assertTrue(driver.getTarget() == null, "Village-arrival fixture acquired an unintended target");
            double distance = tank.position().subtract(Vec3.atBottomCenterOf(home)).horizontalDistance();
            if (helper.getTick() % 80 == 0) {
                LogUtils.getLogger().info("Raid village approach tick={} distance={} position={} pathFinding={}",
                        helper.getTick(), distance, tank.position(), driver.isPathFinding());
            }
            if (distance <= 16.0D) {
                helper.assertTrue(fixture.level.isVillage(home), "Village POI fixture is not an active village");
                helper.succeed();
            }
        });
    }

    private static void verifyRaidTravel(GameTestHelper helper, String vehicleType, boolean flying) {
        Fixture fixture = new Fixture(helper);
        PENDING_CLEANUP.add(fixture::close);
        fixture.fill(new BlockPos(-20, -5, -20), new BlockPos(20, -1, 110), Blocks.STONE.defaultBlockState());
        CommonConfig.VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL.set(0);
        CommonConfig.VINDICATOR_RAID_VEHICLE_CHANCE.set(1.0D);
        CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(List.of("superbwarfare:" + vehicleType));
        Raid raid = new Raid(9983, fixture.level, fixture.origin.offset(0, 0, 90));
        Raider driver = fixture.spawnRaidVindicator(raid, 2);
        helper.assertTrue(driver.isPassenger(), "Could not generate raid travel vehicle");
        Entity vehicle = driver.getVehicle();
        Vec3 initial = vehicle.position();
        double initialDistance = initial.subtract(Vec3.atBottomCenterOf(raid.getCenter())).horizontalDistance();
        helper.onEachTick(() -> {
            helper.assertTrue(driver.getTarget() == null, "Raid travel fixture acquired an unintended combat target");
            helper.assertTrue(driver.getVehicle() == vehicle, "Raid driver abandoned its generated vehicle");
            double distance = vehicle.position().subtract(Vec3.atBottomCenterOf(raid.getCenter())).horizontalDistance();
            if (helper.getTick() % 40 == 0) {
                LogUtils.getLogger().info("Raid travel vehicle={} tick={} progress={} position={} goals={}",
                        vehicleType, helper.getTick(), initialDistance - distance, vehicle.position(),
                        driver.goalSelector.getRunningGoals().map(goal -> goal.getGoal().getClass().getSimpleName()).toList());
            }
            if (initialDistance - distance < 24.0D) return;
            if (flying) helper.assertTrue(vehicle.getY() > fixture.origin.getY() + 6.0D, "Raid helicopter lost flight altitude");
            helper.succeed();
        });
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos origin;
        private final AABB area;
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private final boolean ai = CommonConfig.ENABLE_VEHICLE_AI.get();
        private final boolean debug = CommonConfig.VEHICLE_AI_DEBUG.get();
        private final boolean needsAmmo = CommonConfig.VEHICLE_AI_NEEDS_AMMO.get();
        private final double patrolChance = CommonConfig.PATROL_VEHICLE_CHANCE.get();
        private final double naturalChance = CommonConfig.NATURAL_VEHICLE_CHANCE.get();
        private final double raidChance = CommonConfig.VINDICATOR_RAID_VEHICLE_CHANCE.get();
        private final int omenLevel = CommonConfig.VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL.get();
        private final int minimumWave = CommonConfig.VINDICATOR_RAID_MIN_WAVE.get();
        private final double extraChance = CommonConfig.GENERATED_VEHICLE_EXTRA_PASSENGER_CHANCE.get();
        private final List<? extends String> vehicleTypes = CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.get();
        private boolean closed;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.origin = helper.absolutePos(new BlockPos(0, 8, 0));
            this.area = new AABB(origin).inflate(32.0D, 80.0D, 32.0D);
            CommonConfig.ENABLE_VEHICLE_AI.set(true);
            CommonConfig.VEHICLE_AI_DEBUG.set(false);
            CommonConfig.VINDICATOR_RAID_VEHICLE_CHANCE.set(0.0D);
            CommonConfig.VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL.set(4);
            CommonConfig.VINDICATOR_RAID_MIN_WAVE.set(2);
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

        private Raider spawnRaidVindicator(Raid raid, int wave) {
            Raider raider = EntityType.VINDICATOR.create(level);
            helper.assertTrue(raider != null, "Missing raid Vindicator fixture");
            raid.joinRaid(wave, raider, origin.below(), false);
            helper.assertTrue(level.getEntity(raider.getUUID()) == raider, "Could not add raid Vindicator fixture");
            return raider;
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
            if (closed) return;
            closed = true;
            clearEntities();
            blocks.forEach(level::setBlockAndUpdate);
            CommonConfig.ENABLE_VEHICLE_AI.set(ai);
            CommonConfig.VEHICLE_AI_DEBUG.set(debug);
            CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(needsAmmo);
            CommonConfig.PATROL_VEHICLE_CHANCE.set(patrolChance);
            CommonConfig.NATURAL_VEHICLE_CHANCE.set(naturalChance);
            CommonConfig.VINDICATOR_RAID_VEHICLE_CHANCE.set(raidChance);
            CommonConfig.VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL.set(omenLevel);
            CommonConfig.VINDICATOR_RAID_MIN_WAVE.set(minimumWave);
            CommonConfig.GENERATED_VEHICLE_EXTRA_PASSENGER_CHANCE.set(extraChance);
            CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(vehicleTypes);
        }
    }
}
