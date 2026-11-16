package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.mojang.logging.LogUtils;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwGameTestVehicle;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.gametest.fixture.VehicleTrackingFixture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@GameTestHolder("vindicators_rumble")
@PrefixGameTestTemplate(false)
public final class GroundMountedLifecycleGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();
    private static final List<Fixture> FIXTURES = new ArrayList<>();
    private static boolean previousEnabled;
    private static boolean previousAmmo;
    private static double previousNatural;

    @BeforeBatch(batch = "ground_mounted_lifecycle")
    public static void prepare(ServerLevel level) {
        previousEnabled = CommonConfig.ENABLE_VEHICLE_AI.get();
        previousAmmo = CommonConfig.VEHICLE_AI_NEEDS_AMMO.get();
        previousNatural = CommonConfig.NATURAL_VEHICLE_CHANCE.get();
        CommonConfig.ENABLE_VEHICLE_AI.set(true);
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(true);
        CommonConfig.NATURAL_VEHICLE_CHANCE.set(0.0D);
    }

    @AfterBatch(batch = "ground_mounted_lifecycle")
    public static void cleanup(ServerLevel level) {
        List.copyOf(FIXTURES).forEach(Fixture::close);
        CommonConfig.ENABLE_VEHICLE_AI.set(previousEnabled);
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(previousAmmo);
        CommonConfig.NATURAL_VEHICLE_CHANCE.set(previousNatural);
    }

    @GameTest(batch = "ground_mounted_lifecycle", template = "empty", timeoutTicks = 420)
    public static void targetLossAndReacquisitionStillAllowRamming(GameTestHelper helper) {
        verify(helper, false, false);
    }

    @GameTest(batch = "ground_mounted_lifecycle", template = "empty", timeoutTicks = 420)
    public static void nativeSeatPositionAcrossHiddenChunkStillAllowsRamming(GameTestHelper helper) {
        verify(helper, true, false);
    }

    @GameTest(batch = "ground_mounted_lifecycle", template = "empty", timeoutTicks = 420)
    public static void cancelledDeathDoesNotDisableAiOnRemount(GameTestHelper helper) {
        verify(helper, false, true);
    }

    private static void verify(GameTestHelper helper, boolean crossHiddenChunk, boolean cancelledDeath) {
        Fixture fixture = new Fixture(helper);
        int[] phase = {0};
        long[] phaseStarted = {0};
        Vec3[] engagementStart = {null};
        boolean[] contact = {false};
        helper.onEachTick(() -> {
            helper.assertTrue(fixture.driver.isAlive() && fixture.driver.getVehicle() == fixture.vehicle,
                    "Lost the living mounted driver");
            if (phase[0] == 0) {
                if (helper.getTick() < 8) return;
                if (crossHiddenChunk) {
                    fixture.prepareSeatCrossing();
                    phase[0] = 1;
                } else {
                    fixture.exposeTarget();
                    phase[0] = 2;
                }
                phaseStarted[0] = helper.getTick();
                return;
            }
            if (phase[0] == 1) {
                helper.assertTrue(helper.getTick() - phaseStarted[0] < 30,
                        "Invalid fixture: native seat positioning did not end tracking");
                if (fixture.leaves == 0) return;
                helper.assertTrue(fixture.seatMoveLeave && fixture.deaths == 0 && fixture.joins == 0,
                        "Leave was not a native seat move without death, removal or readmission");
                VehicleTrackingFixture.setVisibility(fixture.level, fixture.hiddenChunk, Visibility.TICKING);
                fixture.exposeTarget();
                phase[0] = 2;
                phaseStarted[0] = helper.getTick();
                return;
            }
            if (phase[0] == 2) {
                helper.assertTrue(helper.getTick() - phaseStarted[0] < 100,
                        "Driver did not naturally acquire the visible target");
                if (fixture.driver.getTarget() != fixture.target) return;
                if (crossHiddenChunk || cancelledDeath) {
                    if (cancelledDeath) fixture.cancelLethalDamage();
                    fixture.closeTarget();
                    engagementStart[0] = fixture.vehicle.position();
                    phase[0] = 5;
                } else {
                    fixture.target.moveTo(fixture.start.add(0.0D, 0.0D, 120.0D));
                    phase[0] = 3;
                }
                phaseStarted[0] = helper.getTick();
                return;
            }
            if (phase[0] == 3) {
                helper.assertTrue(helper.getTick() - phaseStarted[0] < 30, "Out-of-range target was not released");
                if (fixture.driver.getTarget() != null) return;
                helper.assertTrue(fixture.leaves == 0 && fixture.deaths == 0,
                        "Ordinary target loss unexpectedly left the level or killed the driver");
                fixture.closeTarget();
                phase[0] = 4;
                phaseStarted[0] = helper.getTick();
                return;
            }
            if (phase[0] == 4) {
                helper.assertTrue(helper.getTick() - phaseStarted[0] < 100, "Driver did not reacquire its target");
                if (fixture.driver.getTarget() != fixture.target) return;
                engagementStart[0] = fixture.vehicle.position();
                phase[0] = 5;
                phaseStarted[0] = helper.getTick();
            }
            contact[0] |= fixture.vehicle.getBoundingBox().intersects(fixture.target.getBoundingBox());
            if (helper.getTick() - phaseStarted[0] < 140) return;
            double travel = fixture.vehicle.position().subtract(engagementStart[0]).horizontalDistance();
            LogUtils.getLogger().info("Mounted lifecycle result seatCrossing={} cancelledDeath={} leaves={} deaths={} joins={} contact={} travel={}",
                    crossHiddenChunk, cancelledDeath, fixture.leaves, fixture.deaths, fixture.joins, contact[0], travel);
            helper.assertTrue(contact[0], "Tank did not ram after the lifecycle transition; travel=" + travel);
            fixture.close();
            helper.succeed();
        });
    }

    private static final class Fixture implements AutoCloseable {
        private final ServerLevel level;
        private final GameTestHelper helper;
        private final List<ChunkPos> chunks = new ArrayList<>();
        private final Entity vehicle;
        private final Mob driver;
        private final Mob target;
        private Vec3 start;
        private ChunkPos hiddenChunk;
        private int leaves;
        private int deaths;
        private int joins;
        private boolean seatMoveLeave;
        private boolean closed;
        private final Consumer<EntityLeaveLevelEvent> onLeave;
        private final Consumer<EntityJoinLevelEvent> onJoin;
        private final Consumer<LivingDeathEvent> onDeath;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            BlockPos origin = new BlockPos(24576 + ARENA.getAndIncrement() * 192, 5, 16384);
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    ChunkPos chunk = new ChunkPos((origin.getX() >> 4) + x, (origin.getZ() >> 4) + z);
                    this.level.setChunkForced(chunk.x, chunk.z, true);
                    this.chunks.add(chunk);
                }
            }
            for (int x = -40; x <= 40; x++) {
                for (int z = -40; z <= 40; z++) {
                    this.level.setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
                }
            }
            this.start = Vec3.atBottomCenterOf(origin).add(8.0D, 0.0D, 8.0D);
            this.vehicle = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("superbwarfare", "yx_100")).create(this.level);
            this.vehicle.moveTo(this.start.x, this.start.y, this.start.z, -102.354F, 0.0F);
            this.vehicle.setInvulnerable(true);
            helper.assertTrue(this.level.addFreshEntity(this.vehicle), "Could not add tank fixture");
            SbwGameTestVehicle.prepareSmokeFixture(this.vehicle, 0, 0);
            this.driver = EntityType.VINDICATOR.create(this.level);
            this.driver.moveTo(this.start);
            this.driver.setPersistenceRequired();
            this.driver.setInvulnerable(true);
            this.driver.setNoAi(true);
            this.driver.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64.0D);
            helper.assertTrue(this.level.addFreshEntity(this.driver), "Could not add driver fixture");
            helper.assertTrue(VehicleControllers.create(this.vehicle).orElseThrow().mount(this.driver), "Could not mount driver");
            this.target = EntityType.VILLAGER.create(this.level);
            this.target.moveTo(this.start.add(0.0D, 0.0D, 120.0D));
            this.target.setNoAi(true);
            this.target.setPersistenceRequired();
            this.target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255, false, false));
            helper.assertTrue(this.level.addFreshEntity(this.target), "Could not add target fixture");
            this.onLeave = event -> {
                if (event.getEntity() != this.driver) return;
                this.leaves++;
                var trace = new Throwable("Native mounted-driver leave");
                this.seatMoveLeave |= !this.driver.isRemoved()
                        && java.util.Arrays.stream(trace.getStackTrace()).anyMatch(frame ->
                        frame.getClassName().endsWith("VehicleEntity") && frame.getMethodName().equals("passengerPos"));
                LogUtils.getLogger().info("Mounted driver leave removed={} reason={} alive={} pos={} vehiclePos={}",
                        this.driver.isRemoved(), this.driver.getRemovalReason(), this.driver.isAlive(),
                        this.driver.position(), this.vehicle.position(), trace);
            };
            this.onJoin = event -> { if (event.getEntity() == this.driver) this.joins++; };
            this.onDeath = event -> { if (event.getEntity() == this.driver) this.deaths++; };
            MinecraftForge.EVENT_BUS.addListener(this.onLeave);
            MinecraftForge.EVENT_BUS.addListener(this.onJoin);
            MinecraftForge.EVENT_BUS.addListener(this.onDeath);
            FIXTURES.add(this);
        }

        private void prepareSeatCrossing() {
            Vec3 offset = this.driver.position().subtract(this.vehicle.position());
            helper.assertTrue(offset.horizontalDistance() > 0.2D, "Invalid fixture: no physical seat offset");
            boolean xAxis = Math.abs(offset.x) >= Math.abs(offset.z);
            double component = xAxis ? offset.x : offset.z;
            double current = xAxis ? this.start.x : this.start.z;
            double boundary = Math.floor(current / 16.0D) * 16.0D + 16.0D;
            double shift = boundary - component * 0.5D - current;
            this.start = this.start.add(xAxis ? shift : 0.0D, 0.0D, xAxis ? 0.0D : shift);
            this.vehicle.moveTo(this.start.x, this.start.y, this.start.z, -102.354F, 0.0F);
            this.driver.moveTo(this.start);
            ChunkPos vehicleChunk = new ChunkPos(this.vehicle.blockPosition());
            this.hiddenChunk = new ChunkPos(BlockPos.containing(this.start.add(offset)));
            helper.assertTrue(!vehicleChunk.equals(this.hiddenChunk), "Invalid fixture: seat did not cross a chunk boundary");
            VehicleTrackingFixture.setVisibility(this.level, vehicleChunk, Visibility.TICKING);
            VehicleTrackingFixture.setVisibility(this.level, this.hiddenChunk, Visibility.HIDDEN);
        }

        private void exposeTarget() {
            this.target.moveTo(this.start.add(0.0D, 0.0D, 9.0D));
            this.driver.setNoAi(false);
        }

        private void closeTarget() {
            this.target.moveTo(this.vehicle.position().add(0.0D, 0.0D, 5.5D));
        }

        private void cancelLethalDamage() {
            Consumer<LivingDeathEvent> cancel = event -> {
                if (event.getEntity() != this.driver) return;
                event.setCanceled(true);
                this.driver.setHealth(this.driver.getMaxHealth());
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, cancel);
            try {
                // Enclosed SBW seats reject damage before vanilla reaches its death event.
                this.driver.stopRiding();
                this.driver.setInvulnerable(false);
                this.driver.hurt(this.level.damageSources().genericKill(), Float.MAX_VALUE);
                helper.assertTrue(this.deaths == 1 && this.driver.isAlive() && !this.driver.isRemoved(),
                        "Invalid fixture: lethal damage was not canceled");
                this.driver.setInvulnerable(true);
                helper.assertTrue(this.driver.startRiding(this.vehicle, true),
                        "Surviving driver could not remount");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(cancel);
            }
        }

        @Override
        public void close() {
            if (this.closed) return;
            this.closed = true;
            MinecraftForge.EVENT_BUS.unregister(this.onLeave);
            MinecraftForge.EVENT_BUS.unregister(this.onJoin);
            MinecraftForge.EVENT_BUS.unregister(this.onDeath);
            if (this.hiddenChunk != null) VehicleTrackingFixture.setVisibility(this.level, this.hiddenChunk, Visibility.TICKING);
            this.target.discard();
            this.driver.discard();
            this.vehicle.discard();
            this.chunks.forEach(chunk -> this.level.setChunkForced(chunk.x, chunk.z, false));
            FIXTURES.remove(this);
        }
    }
}
