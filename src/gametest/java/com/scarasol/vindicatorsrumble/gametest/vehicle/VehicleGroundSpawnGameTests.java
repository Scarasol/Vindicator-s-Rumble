package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.mojang.logging.LogUtils;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwGameTestVehicle;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder("vindicators_rumble")
@PrefixGameTestTemplate(false)
public final class VehicleGroundSpawnGameTests {
    private static final List<Runnable> CLEANUP = new ArrayList<>();

    @AfterBatch(batch = "low_ground_natural_spawn")
    public static void cleanNatural(ServerLevel level) {
        clean();
    }

    @AfterBatch(batch = "low_ground_raid_spawn")
    public static void cleanRaid(ServerLevel level) {
        clean();
    }

    @AfterBatch(batch = "crowded_ground_raid_spawn")
    public static void cleanCrowdedRaid(ServerLevel level) {
        clean();
    }

    @AfterBatch(batch = "crowded_ground_natural_spawn")
    public static void cleanCrowdedNatural(ServerLevel level) {
        clean();
    }

    private static void clean() {
        List.copyOf(CLEANUP).forEach(Runnable::run);
        CLEANUP.clear();
    }

    @GameTest(batch = "low_ground_natural_spawn", template = "empty", timeoutTicks = 90)
    public static void naturalTanksStayAboveWorldBottomGround(GameTestHelper helper) {
        verify(helper, false, false);
    }

    @GameTest(batch = "low_ground_raid_spawn", template = "empty", timeoutTicks = 90)
    public static void raidTanksStayAboveWorldBottomGround(GameTestHelper helper) {
        verify(helper, true, false);
    }

    @GameTest(batch = "crowded_ground_raid_spawn", template = "empty", timeoutTicks = 90)
    public static void crowdedRaidTanksDoNotOverlapOrSink(GameTestHelper helper) {
        verify(helper, true, true);
    }

    @GameTest(batch = "crowded_ground_natural_spawn", template = "empty", timeoutTicks = 90)
    public static void crowdedNaturalTanksDoNotOverlapOrSink(GameTestHelper helper) {
        verify(helper, false, true);
    }

    private static void verify(GameTestHelper helper, boolean raidSpawn, boolean crowded) {
        ServerLevel level = helper.getLevel();
        boolean enabled = CommonConfig.ENABLE_VEHICLE_AI.get();
        double natural = CommonConfig.NATURAL_VEHICLE_CHANCE.get();
        double raidChance = CommonConfig.VINDICATOR_RAID_VEHICLE_CHANCE.get();
        int omen = CommonConfig.VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL.get();
        int wave = CommonConfig.VINDICATOR_RAID_MIN_WAVE.get();
        double passengers = CommonConfig.GENERATED_VEHICLE_EXTRA_PASSENGER_CHANCE.get();
        List<? extends String> types = CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.get();
        CLEANUP.add(() -> {
            CommonConfig.ENABLE_VEHICLE_AI.set(enabled);
            CommonConfig.NATURAL_VEHICLE_CHANCE.set(natural);
            CommonConfig.VINDICATOR_RAID_VEHICLE_CHANCE.set(raidChance);
            CommonConfig.VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL.set(omen);
            CommonConfig.VINDICATOR_RAID_MIN_WAVE.set(wave);
            CommonConfig.GENERATED_VEHICLE_EXTRA_PASSENGER_CHANCE.set(passengers);
            CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(types);
        });
        CommonConfig.ENABLE_VEHICLE_AI.set(true);
        CommonConfig.NATURAL_VEHICLE_CHANCE.set(raidSpawn ? 0.0D : 1.0D);
        CommonConfig.VINDICATOR_RAID_VEHICLE_CHANCE.set(raidSpawn ? 1.0D : 0.0D);
        CommonConfig.VINDICATOR_RAID_MIN_BAD_OMEN_LEVEL.set(0);
        CommonConfig.VINDICATOR_RAID_MIN_WAVE.set(2);
        CommonConfig.GENERATED_VEHICLE_EXTRA_PASSENGER_CHANCE.set(0.0D);
        List<Entity> vehicles = new ArrayList<>();
        int groundY = level.getMinBuildHeight() + 4;
        int[] initialOverlaps = {0};
        for (String type : crowded ? List.of("yx_100") : List.of("yx_100", "m_1a_2", "t_90a")) {
            for (int seed = 0; seed < (crowded ? 16 : 4); seed++) {
                BlockPos origin = new BlockPos(512 + (crowded ? 0 : vehicles.size() * 96),
                        groundY, crowded ? 20704 : raidSpawn ? 20352 : 20000);
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        int chunkX = (origin.getX() >> 4) + x;
                        int chunkZ = (origin.getZ() >> 4) + z;
                        level.setChunkForced(chunkX, chunkZ, true);
                        CLEANUP.add(() -> level.setChunkForced(chunkX, chunkZ, false));
                    }
                }
                for (BlockPos block : BlockPos.betweenClosed(origin.offset(-20, -4, -20), origin.offset(20, -1, 20))) {
                    level.setBlock(block, block.getY() == level.getMinBuildHeight()
                            ? Blocks.BEDROCK.defaultBlockState() : Blocks.STONE.defaultBlockState(), 2);
                }
                CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(List.of("superbwarfare:" + type));
                level.random.setSeed(48219L + seed);
                Raider owner = EntityType.VINDICATOR.create(level);
                helper.assertTrue(owner != null, "Invalid fixture: could not create driver");
                if (raidSpawn) {
                    new Raid(18000 + vehicles.size(), level, origin.offset(0, 0, 64))
                            .joinRaid(2, owner, origin.below(), false);
                } else {
                    owner.moveTo(origin.getX() + 0.5D, groundY, origin.getZ() + 0.5D, 0.0F, 0.0F);
                    ForgeEventFactory.onFinalizeSpawn(owner, level, level.getCurrentDifficultyAt(origin),
                            MobSpawnType.NATURAL, null, null);
                    helper.assertTrue(level.addFreshEntity(owner), "Invalid fixture: could not add driver");
                }
                CLEANUP.add(owner::discard);
                owner.setNoAi(true);
                if (crowded && !owner.isPassenger()) continue;
                helper.assertTrue(owner.isPassenger(), "No generated tank for " + type + " seed=" + seed);
                Entity vehicle = owner.getVehicle();
                for (Entity other : vehicles) {
                    if (vehicle.getBoundingBox().deflate(0.001D).intersects(other.getBoundingBox())) {
                        initialOverlaps[0]++;
                        LogUtils.getLogger().info("Overlapping generated tanks first={} second={} firstBox={} secondBox={}",
                                other.getId(), vehicle.getId(), other.getBoundingBox(), vehicle.getBoundingBox());
                    }
                }
                vehicles.add(vehicle);
                CLEANUP.add(() -> {
                    List.copyOf(vehicle.getPassengers()).forEach(Entity::discard);
                    vehicle.discard();
                });
                vehicle.getPassengers().forEach(passenger -> {
                    if (passenger instanceof Mob mob) mob.setNoAi(true);
                });
                LogUtils.getLogger().info("Low-ground tank generated raid={} crowded={} type={} seed={} position={} physical={}",
                        raidSpawn, crowded, type, seed, vehicle.position(), SbwGameTestVehicle.physicalCollisionBounds(vehicle));
                assertAboveGround(helper, vehicle, groundY, true);
            }
        }
        helper.assertTrue(!vehicles.isEmpty(), "No tank generated on an initially empty open floor");
        helper.onEachTick(() -> {
            for (Entity vehicle : vehicles) {
                assertAboveGround(helper, vehicle, groundY, false);
            }
            if (helper.getTick() >= 60) {
                LogUtils.getLogger().info("Low-ground tank result raid={} crowded={} vehicles={} initialOverlaps={} observationTicks=60",
                        raidSpawn, crowded, vehicles.size(), initialOverlaps[0]);
                helper.assertTrue(initialOverlaps[0] == 0, "Generated tanks overlap: " + initialOverlaps[0]);
                helper.succeed();
            }
        });
    }

    private static void assertAboveGround(GameTestHelper helper, Entity vehicle, double groundY, boolean initial) {
        AABB body = SbwGameTestVehicle.physicalCollisionBounds(vehicle);
        helper.assertTrue(body.minY >= groundY - (initial ? 0.001D : 0.2D),
                "Tank body sank below ground initial=" + initial + " type=" + vehicle.getType()
                        + " position=" + vehicle.position() + " physical=" + body);
        helper.assertTrue(!helper.getLevel().getBlockCollisions(vehicle, body.deflate(initial ? 0.001D : 0.2D))
                        .iterator().hasNext(),
                "Generated tank body intersects ground initial=" + initial + " position=" + vehicle.position());
    }
}
