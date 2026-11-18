package com.scarasol.vindicatorsrumble.gametest;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.mojang.logging.LogUtils;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleInput;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleMovementType;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponFireWindow;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponFireWindowState;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponHandle;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponPayloadType;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponStatus;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.compat.VehicleSpawnCandidates;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.util.VehicleAiMemory;
import com.scarasol.vindicatorsrumble.util.VehicleAiTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;
import org.ywzj.vehicle.entity.vehicle.WheeledVehicle;
import org.ywzj.vehicle.entity.weapon.BulletEntity;
import org.ywzj.vehicle.util.VectorUtil;
import org.ywzj.vehicle.vehicle.part.WeaponUnit;
import org.ywzj.vehicle.vehicle.weapon.AbstractVehicleWeapon;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

@GameTestHolder("vindicators_rumble_limitless")
@PrefixGameTestTemplate(false)
public final class LimitlessVehicleGameTests {
    private static final String BATCH = "limitless_ground";
    private static final String AMMO_BATCH = "limitless_ammo";
    private static final String COMPARISON_BATCH = "limitless_sbw_ground_comparison";
    private static final boolean TRACE_OBSTACLE = Boolean.getBoolean("vindicatorsrumble.limitlessObstacleTrace");
    private static final ResourceLocation SBW_PICKUP = new ResourceLocation("superbwarfare", "sodayo_pick_up_hmg");
    private static final ResourceLocation LV_MRZR = new ResourceLocation("ywzj_vehicle", "atv_mrzr4");
    private static final ResourceLocation SBW_M1A2 = new ResourceLocation("superbwarfare", "m_1a_2");
    private static final ResourceLocation LV_M1A2 = new ResourceLocation("ywzj_vehicle", "m1a2");
    private static final List<Runnable> CLEANUP = new ArrayList<>();
    private static int nextArena;
    private static boolean previousNeedsAmmo;
    private static boolean previousDebug;
    private static double previousSpawnChance;

    @AfterBatch(batch = BATCH)
    public static void clean(ServerLevel level) {
        CLEANUP.forEach(Runnable::run);
        CLEANUP.clear();
    }

    @BeforeBatch(batch = AMMO_BATCH)
    public static void enableAmmo(ServerLevel level) {
        previousNeedsAmmo = CommonConfig.VEHICLE_AI_NEEDS_AMMO.get();
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(true);
    }

    @AfterBatch(batch = AMMO_BATCH)
    public static void restoreAmmo(ServerLevel level) {
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(previousNeedsAmmo);
        clean(level);
    }

    @BeforeBatch(batch = COMPARISON_BATCH)
    public static void prepareComparison(ServerLevel level) {
        previousNeedsAmmo = CommonConfig.VEHICLE_AI_NEEDS_AMMO.get();
        previousSpawnChance = CommonConfig.NATURAL_VEHICLE_CHANCE.get();
        previousDebug = CommonConfig.VEHICLE_AI_DEBUG.get();
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(true);
        CommonConfig.NATURAL_VEHICLE_CHANCE.set(0.0D);
        if (TRACE_OBSTACLE) {
            CommonConfig.VEHICLE_AI_DEBUG.set(true);
        }
    }

    @AfterBatch(batch = COMPARISON_BATCH)
    public static void restoreComparison(ServerLevel level) {
        CommonConfig.NATURAL_VEHICLE_CHANCE.set(previousSpawnChance);
        CommonConfig.VEHICLE_AI_DEBUG.set(previousDebug);
        restoreAmmo(level);
    }

    @GameTest(batch = COMPARISON_BATCH, template = "empty", timeoutTicks = 130)
    public static void compareWheeledGearChangeAndBraking(GameTestHelper helper) {
        compareSteering(helper, SBW_PICKUP, LV_MRZR, VehicleMovementType.WHEEL);
    }

    @GameTest(batch = COMPARISON_BATCH, template = "empty", timeoutTicks = 130)
    public static void compareTrackedGearChangeAndBraking(GameTestHelper helper) {
        compareSteering(helper, SBW_M1A2, LV_M1A2, VehicleMovementType.TRACK);
    }

    private static void compareSteering(GameTestHelper helper, ResourceLocation sbw, ResourceLocation lv,
                                        VehicleMovementType movementType) {
        List<SteeringProbe> probes = List.of(new SteeringProbe(new ComparisonFixture(helper, sbw)),
                new SteeringProbe(new ComparisonFixture(helper, lv)));
        List<VehicleInput> inputs = List.of(
                new VehicleInput(true, false, false, false, false, false, false, false, false, false),
                new VehicleInput(false, true, false, true, false, false, false, false, false, false),
                VehicleInput.BRAKE);
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 12) {
                return;
            }
            for (SteeringProbe probe : probes) {
                var fixture = probe.fixture;
                if (tick == 12) {
                    fixture.mount(helper);
                    helper.assertTrue(fixture.controller.movementType() == movementType,
                            "Wrong movement type for " + fixture.id);
                    helper.assertTrue(fixture.controller.canDrive(fixture.crew), "No driver seat for " + fixture.id);
                }
                probe.advance(tick - 12, inputs);
            }
            if (tick == 117) {
                probes.forEach(SteeringProbe::log);
                for (SteeringProbe probe : probes) {
                    String id = probe.fixture.id.toString();
                    helper.assertTrue(probe.switchSpeed > 0.05D, id + " did not reach forward speed");
                    helper.assertTrue(probe.reverseSpeed < -0.03D, id + " did not establish reverse motion");
                    helper.assertTrue(probe.turn < -3.0D && probe.wrongWayTurn < 3.0D,
                            id + " steered against the reverse-right request");
                    helper.assertTrue(probe.stoppedAt >= 0, id + " did not stop after braking");
                }
                helper.succeed();
            }
        });
    }

    @GameTest(batch = COMPARISON_BATCH, template = "empty", timeoutTicks = 260)
    public static void compareTankTurretReversalAndFirstShot(GameTestHelper helper) {
        List<TurretProbe> probes = List.of(new TurretProbe(new ComparisonFixture(helper, SBW_M1A2)),
                new TurretProbe(new ComparisonFixture(helper, LV_M1A2)));
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick == 12) {
                probes.forEach(probe -> probe.mountGunner(helper));
            }
            // The native LV powered station is unavailable while the engine spools up.
            if (tick < 52) {
                return;
            }
            for (TurretProbe probe : probes) {
                if (tick == 52) {
                    probe.prepare(helper);
                }
                probe.advance(helper, tick - 52);
            }
            if (probes.stream().allMatch(probe -> probe.firedAt >= 0)) {
                helper.succeed();
            } else if (tick == 245) {
                probes.forEach(TurretProbe::log);
                helper.fail("At least one tank failed to align on both sides and fire");
            }
        });
    }

    @GameTest(batch = COMPARISON_BATCH, template = "empty", timeoutTicks = 360)
    public static void compareWheeledNavigationAroundObstacle(GameTestHelper helper) {
        NavigationProbe sbw = new NavigationProbe(new ComparisonFixture(helper, SBW_PICKUP));
        NavigationProbe limitless = new NavigationProbe(new ComparisonFixture(helper, LV_MRZR));
        List<NavigationProbe> probes = List.of(sbw, limitless);
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 12) {
                return;
            }
            for (NavigationProbe probe : probes) {
                if (tick == 12) {
                    probe.start(helper);
                }
                probe.advance(tick - 12);
            }
            if (probes.stream().allMatch(probe -> probe.arrivedAt >= 0)) {
                probes.forEach(NavigationProbe::log);
                helper.assertTrue(limitless.adapterOnlyCollisionTicks > 0,
                        "LV native contact never reached the controller while the vanilla flag was clear");
                helper.succeed();
            } else if (tick == 345) {
                probes.forEach(NavigationProbe::log);
                helper.fail("At least one driver failed to reach and hold the destination around the obstacle");
            }
        });
    }

    private record ComparisonFixture(Arena arena, ResourceLocation id, VehicleController controller, Mob crew) {
        private ComparisonFixture(GameTestHelper helper, ResourceLocation id) {
            this(new Arena(helper), id);
        }

        private ComparisonFixture(Arena arena, ResourceLocation id) {
            this(arena, id, VehicleControllers.create(arena.vehicle(id, 0.0F)).orElseThrow(),
                    arena.spawn(EntityType.VINDICATOR.create(arena.level), arena.origin.add(0.0D, 0.0D, -5.0D)));
            this.crew.setNoAi(true);
            this.crew.setPersistenceRequired();
        }

        private void mount(GameTestHelper helper) {
            helper.assertTrue(this.controller.mount(this.crew), "Could not mount " + this.id);
            this.controller.initializeGeneratedSupplies();
        }
    }

    private static final class SteeringProbe {
        private final ComparisonFixture fixture;
        private float priorYaw;
        private double switchSpeed;
        private double reverseSpeed;
        private double turn;
        private double wrongWayTurn;
        private Vec3 brakeOrigin;
        private double predictedStop;
        private double actualStop;
        private long stoppedAt = -1;

        private SteeringProbe(ComparisonFixture fixture) {
            this.fixture = fixture;
        }

        private void advance(long tick, List<VehicleInput> inputs) {
            var controller = this.fixture.controller;
            float yaw = controller.yaw();
            double radians = Math.toRadians(yaw);
            double signedSpeed = controller.velocity().dot(new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians)));
            if (tick == 30) {
                this.switchSpeed = signedSpeed;
            } else if (tick > 30 && tick <= 60) {
                this.turn += Mth.wrapDegrees(yaw - this.priorYaw);
                this.wrongWayTurn = Math.max(this.wrongWayTurn, this.turn);
                this.reverseSpeed = Math.min(this.reverseSpeed, signedSpeed);
            }
            if (tick == 60) {
                this.brakeOrigin = controller.position();
                this.predictedStop = controller.estimatedHorizontalStoppingDistance();
            } else if (tick > 60 && this.stoppedAt < 0 && controller.velocity().horizontalDistance() < 0.035D) {
                this.stoppedAt = tick - 60;
                this.actualStop = controller.position().subtract(this.brakeOrigin).horizontalDistance();
            }
            this.priorYaw = yaw;
            controller.drive(this.fixture.crew, inputs.get(tick < 30 ? 0 : tick < 60 ? 1 : 2));
        }

        private void log() {
            LogUtils.getLogger().info("Ground A/B steering {} forwardSpeed={} reverseSpeed={} reverseYaw={} "
                            + "wrongWayYaw={} predictedStop={} measuredStop={} stopTicks={}",
                    this.fixture.id, this.switchSpeed, this.reverseSpeed, this.turn, this.wrongWayTurn,
                    this.predictedStop, this.actualStop, this.stoppedAt);
        }
    }

    private static final class NavigationProbe {
        private final ComparisonFixture fixture;
        private final Vec3 destination;
        private boolean requested;
        private int stoppedTicks;
        private int collisionTicks;
        private int adapterOnlyCollisionTicks;
        private double maxRise;
        private long arrivedAt = -1;

        private NavigationProbe(ComparisonFixture fixture) {
            this.fixture = fixture;
            this.destination = fixture.arena.origin.add(0.0D, 0.0D, 24.0D);
            BlockPos obstacle = BlockPos.containing(fixture.arena.origin.add(0.0D, 0.0D, 9.0D));
            for (int y = 0; y < 3; y++) {
                fixture.arena.level.setBlock(obstacle.above(y), Blocks.OAK_LOG.defaultBlockState(), 2);
            }
        }

        private void start(GameTestHelper helper) {
            this.fixture.mount(helper);
            Mob driver = this.fixture.crew;
            driver.setNoAi(false);
            driver.goalSelector.addGoal(0, new Goal() {
                { this.setFlags(EnumSet.of(Flag.MOVE)); }

                @Override
                public boolean canUse() {
                    return NavigationProbe.this.fixture.controller.canDrive(driver);
                }

                @Override
                public void tick() {
                    if (!NavigationProbe.this.requested) {
                        Vec3 goal = NavigationProbe.this.destination;
                        NavigationProbe.this.requested = driver.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0D);
                    }
                }
            });
        }

        private void advance(long tick) {
            var controller = this.fixture.controller;
            boolean vanillaCollision = controller.entity().horizontalCollision;
            boolean controllerCollision = controller.hasHorizontalCollision();
            if (TRACE_OBSTACLE && (tick < 100 ? tick % 5 == 0 : tick % 20 == 0)) {
                String nativeState = "";
                if (controller.entity() instanceof WheeledVehicle wheeled) {
                    var keys = wheeled.controlUnit;
                    nativeState = " stuck=" + wheeled.physicsEngine.stuckTick + " grounded=" + wheeled.onGround()
                            + " steer=" + wheeled.getTurnAngle() + " forward=" + keys.forward + " back=" + keys.backward
                            + " left=" + keys.left + " right=" + keys.right + " handbrake=" + keys.up;
                }
                LogUtils.getLogger().info("Ground A/B obstacle trace {} driver={} tick={} pos={} velocity={} yaw={} pitch={} "
                                + "bounds={} radius={} vanillaCollision={} controllerCollision={}{}",
                        this.fixture.id, this.fixture.crew.getId(), tick,
                        controller.position().subtract(this.fixture.arena.origin), controller.velocity(), controller.yaw(),
                        controller.pitch(), controller.localCollisionBounds(), controller.estimatedGroundTurningRadius(),
                        vanillaCollision, controllerCollision, nativeState);
            }
            if (vanillaCollision) {
                this.collisionTicks++;
            }
            if (controllerCollision && !vanillaCollision) {
                this.adapterOnlyCollisionTicks++;
            }
            this.maxRise = Math.max(this.maxRise, controller.position().y - this.fixture.arena.origin.y);
            double arrival = VehicleAiTuning.navigationArrivalDistance(controller, 4.0D);
            boolean settled = controller.position().subtract(this.destination).horizontalDistance() <= arrival + 0.15D
                    && controller.velocity().horizontalDistance() < 0.035D;
            this.stoppedTicks = settled ? this.stoppedTicks + 1 : 0;
            if (this.stoppedTicks == 15) {
                this.arrivedAt = tick;
            } else if (!settled) {
                this.arrivedAt = -1;
            }
        }

        private void log() {
            var controller = this.fixture.controller;
            LogUtils.getLogger().info("Ground A/B obstacle {} requested={} arriveTicks={} remaining={} position={} maxRise={} "
                            + "vanillaCollisionTicks={} adapterOnlyCollisionTicks={}",
                    this.fixture.id, this.requested, this.arrivedAt, controller.position().subtract(this.destination).horizontalDistance(),
                    controller.position().subtract(this.fixture.arena.origin), this.maxRise, this.collisionTicks,
                    this.adapterOnlyCollisionTicks);
        }
    }

    private static final class TurretProbe {
        private final ComparisonFixture fixture;
        private final Mob target;
        private final List<Projectile> shots = new ArrayList<>();
        private WeaponHandle gun;
        private WeaponFireWindow window;
        private WeaponFireWindowState beyondRange;
        private long firstReady = -1;
        private long secondReady = -1;
        private long firedAt = -1;
        private double initialYawError;
        private double reversedYawError;
        private double previousBarrelYaw;
        private double maxYawPerTick;
        private WeaponStatus status;
        private boolean clearShot;
        private int fireRequests;

        private TurretProbe(ComparisonFixture fixture) {
            this.fixture = fixture;
            this.placeTargetPlatform(11);
            this.placeTargetPlatform(-11);
            this.target = fixture.arena.spawn(EntityType.VILLAGER.create(fixture.arena.level), this.targetPosition(11));
            this.target.setNoAi(true);
            this.target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 300, 4));
            Consumer<EntityJoinLevelEvent> observer = event -> {
                if (event.getLevel() == fixture.arena.level && event.getEntity() instanceof Projectile projectile
                        && projectile.getOwner() == fixture.crew) {
                    this.shots.add(projectile);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(observer);
            CLEANUP.add(() -> {
                MinecraftForge.EVENT_BUS.unregister(observer);
                this.shots.forEach(Entity::discard);
            });
        }

        private Vec3 targetPosition(int side) {
            return this.fixture.arena.origin.add(side, 2.0D, 18.0D);
        }

        private void placeTargetPlatform(int side) {
            this.fixture.arena.level.setBlock(BlockPos.containing(this.targetPosition(side)).below(),
                    Blocks.BEDROCK.defaultBlockState(), 2);
        }

        private void mountGunner(GameTestHelper helper) {
            this.fixture.mount(helper);
            var occupied = this.fixture.controller.seatOf(this.fixture.crew).orElseThrow();
            if (!occupied.hasWeapon()) {
                var gunner = this.fixture.controller.seats().stream()
                        .filter(seat -> seat.hasWeapon() && seat.occupant().isEmpty()).findFirst().orElseThrow();
                helper.assertTrue(this.fixture.controller.moveToSeat(this.fixture.crew, gunner),
                        "Could not move to an armed seat for " + this.fixture.id);
            }
        }

        private void prepare(GameTestHelper helper) {
            var controller = this.fixture.controller;
            Mob crew = this.fixture.crew;
            this.gun = controller.weapons(crew).stream()
                    .filter(weapon -> weapon.payloadType() == WeaponPayloadType.SHELL)
                    .findFirst().orElseThrow(() -> new IllegalStateException("No main gun for " + this.fixture.id
                            + ": seat=" + controller.seatOf(crew) + ", weapons=" + controller.weapons(crew)
                            + ", engine=" + controller.engineStarted() + ", power=" + controller.power()));
            helper.assertTrue(controller.selectWeapon(crew, this.gun), "Main gun not selected for " + this.fixture.id);
            Vec3 outOfRange = controller.position().add(0.0D, 0.0D, this.gun.maxRange() + 50.0D);
            this.target.moveTo(outOfRange);
            // SBW's window covers joint geometry; LV also enforces weapon range here.
            this.beyondRange = controller.fireWindow(crew, this.target, outOfRange, this.gun).state();
            if (controller.entity() instanceof AbstractVehicle) {
                helper.assertTrue(this.beyondRange == WeaponFireWindowState.IMPOSSIBLE,
                        "Out-of-range LV target had a firing window");
            }
            this.target.moveTo(this.targetPosition(11));
            crew.setTarget(this.target);
            helper.assertTrue(VehicleAiTuning.isValidVehicleCombatTarget(crew, this.target),
                    "Invalid combat target for " + this.fixture.id);
            this.previousBarrelYaw = this.barrelYaw();
        }

        private void advance(GameTestHelper helper, long tick) {
            if (this.firedAt >= 0) {
                return;
            }
            var controller = this.fixture.controller;
            Mob crew = this.fixture.crew;
            Vec3 point = controller.aimPoint(crew, this.target, this.gun);
            this.window = controller.fireWindow(crew, this.target, point, this.gun);
            double barrelYaw = this.barrelYaw();
            this.maxYawPerTick = Math.max(this.maxYawPerTick, Math.abs(Mth.wrapDegrees(barrelYaw - this.previousBarrelYaw)));
            this.previousBarrelYaw = barrelYaw;
            double headingError = Math.abs(Mth.wrapDegrees(yawOf(point.subtract(controller.position())) - barrelYaw));
            if (tick == 0) {
                this.initialYawError = headingError;
                helper.assertTrue(this.window.state() != WeaponFireWindowState.READY_NOW && this.initialYawError > 8.0D,
                        "Initial off-axis target was already ready for " + this.fixture.id + ": " + this.window);
            }
            controller.aimAt(crew, this.target, point);
            if (this.firstReady < 0 && this.window.state() == WeaponFireWindowState.READY_NOW) {
                this.firstReady = tick;
                this.target.moveTo(this.targetPosition(-11));
                this.target.setDeltaMovement(Vec3.ZERO);
                return;
            }
            if (this.firstReady >= 0 && tick == this.firstReady + 1) {
                this.reversedYawError = headingError;
                helper.assertTrue(this.window.state() != WeaponFireWindowState.READY_NOW && this.reversedYawError > 8.0D,
                        "Reversed target was already ready for " + this.fixture.id + ": " + this.window);
            }
            if (this.firstReady >= 0 && this.window.state() == WeaponFireWindowState.READY_NOW) {
                if (this.secondReady < 0) {
                    this.secondReady = tick - this.firstReady;
                }
                this.status = controller.weaponStatus(crew, this.gun);
                this.clearShot = controller.hasLineOfSight(crew, this.target, this.target.getBoundingBox().getCenter());
                if (this.status.needsReload() && !this.status.reloading()) {
                    controller.reload(crew);
                }
                if (this.clearShot && controller.isAimedAt(crew, this.target, point, this.gun) && this.status.canFire()) {
                    this.fireRequests++;
                    controller.fire(crew, this.target, point, this.gun);
                }
            }
            if (!this.shots.isEmpty()) {
                helper.assertTrue(this.secondReady >= 0, "Projectile preceded firing window for " + this.fixture.id);
                this.firedAt = tick;
                this.log();
            }
        }

        private double barrelYaw() {
            Entity vehicle = this.fixture.controller.entity();
            if (vehicle instanceof VehicleEntity sbw) {
                return yawOf(sbw.getBarrelVector(1.0F));
            }
            var station = (WeaponUnit) ((AbstractVehicle) vehicle).getOwnOperatorUnit(this.fixture.crew);
            var muzzle = station.getCurrentWeapon().orElseThrow().getWeaponUnit().aimContext();
            return yawOf(VectorUtil.rotToVec(muzzle.direction.x, muzzle.direction.y));
        }

        private static double yawOf(Vec3 direction) {
            return Math.toDegrees(Math.atan2(-direction.x, direction.z));
        }

        private void log() {
            LogUtils.getLogger().info("Ground A/B turret {} firstReadyTicks={} reversalReadyTicks={} shotTick={} "
                            + "initialYawError={} reversalYawError={} maxYawPerTick={} beyondRange={} finalWindow={} "
                            + "status={} clearShot={} fireRequests={} shots={}",
                    this.fixture.id, this.firstReady, this.secondReady, this.firedAt, this.initialYawError,
                    this.reversedYawError, this.maxYawPerTick, this.beyondRange, this.window,
                    this.status, this.clearShot, this.fireRequests, this.shots.size());
        }
    }

    @GameTest(batch = BATCH, template = "empty", timeoutTicks = 320)
    public static void wheeledMobBoardsDrivesAndStops(GameTestHelper helper) {
        verifyDrive(helper, "atv_mrzr4", VehicleMovementType.WHEEL);
    }

    @GameTest(batch = BATCH, template = "empty", timeoutTicks = 320)
    public static void trackedMobBoardsDrivesAndStops(GameTestHelper helper) {
        verifyDrive(helper, "m1a2", VehicleMovementType.TRACK);
    }

    private static void verifyDrive(GameTestHelper helper, String model, VehicleMovementType type) {
        Arena arena = new Arena(helper);
        Vec3 origin = arena.origin;
        var vehicle = arena.vehicle(model, 0.0F);
        VehicleController controller = VehicleControllers.create(vehicle).orElseThrow();
        helper.assertTrue(controller.movementType() == type, "Vehicle movement type was not mapped");
        var other = arena.spawn(ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("superbwarfare", "sodayo_pick_up_hmg"))
                .create(arena.level), origin.add(10.0D, 0.0D, 0.0D));
        helper.runAtTickTime(10, () -> {
            var found = VehicleControllers.find(arena.level, vehicle.getBoundingBox().inflate(12));
            helper.assertTrue(found.stream().anyMatch(candidate -> candidate.entity() == vehicle),
                    "Ticking LV is not discoverable at " + vehicle.position() + ", age=" + vehicle.tickCount);
            helper.assertTrue(found.stream().anyMatch(candidate -> candidate.entity() == other),
                    "Limitless discovery replaced Superb Warfare");
            other.discard();
        });
        Vec3 destination = origin.add(3.0D, 0.0D, 20.0D);
        var driver = arena.spawn(EntityType.VINDICATOR.create(arena.level), origin.add(3.5D, 0.0D, 0.0D));
        Goal navigation = new Goal() {
            boolean requested;
            {
                this.setFlags(EnumSet.of(Flag.MOVE));
            }

            @Override
            public boolean canUse() {
                return controller.canDrive(driver);
            }

            @Override
            public void tick() {
                if (!this.requested && helper.getTick() >= 18) {
                    this.requested = driver.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0D);
                }
            }
        };
        driver.goalSelector.addGoal(4, navigation);
        int[] stopped = {0};
        helper.onEachTick(() -> {
            double remaining = controller.position().subtract(destination).horizontalDistance();
            double arrival = VehicleAiTuning.navigationArrivalDistance(controller, 4.0D);
            if (remaining <= arrival + 0.15D && controller.velocity().horizontalDistance() < 0.035D) {
                stopped[0]++;
            } else {
                stopped[0] = 0;
            }
            if (stopped[0] == 15) {
                helper.assertTrue(controller.canDrive(driver), "The actual driver is not in the native driver seat");
                controller.drive(driver, new VehicleInput(true, false, false, false, false, false, false, false, false, false));
                helper.assertTrue(vehicle.controlUnit.forward, "AI did not publish a native forward input");
                helper.assertTrue(controller.dismount(driver), "AI driver could not dismount");
                helper.assertTrue(!vehicle.controlUnit.forward && vehicle.getDriver() == null, "Dismount left stale AI input");
                Player player = helper.makeMockSurvivalPlayer();
                helper.assertTrue(player.startRiding(vehicle, true) && vehicle.getDriver() == player,
                        "A player could not take over after the AI dismounted");
                helper.assertTrue(!vehicle.controlUnit.forward && !vehicle.controlUnit.backward,
                        "The next driver inherited AI throttle");
                player.stopRiding();
                helper.succeed();
            }
            if (helper.getTick() == 290) {
                helper.fail("Never arrived and stopped: pos=" + controller.position() + ", remaining=" + remaining + ", arrival=" + arrival
                        + ", velocity=" + controller.velocity() + ", seat=" + controller.seatOf(driver));
            }
        });
    }

    @GameTest(batch = BATCH, template = "empty", timeoutTicks = 180)
    public static void turretGoalFiresNativeRound(GameTestHelper helper) {
        verifyTurret(helper, "lav150", 0, 0.0D, WeaponPayloadType.SHELL, false, false);
    }

    @GameTest(batch = BATCH, template = "empty", timeoutTicks = 180)
    public static void trackedTurretFiresNativeRound(GameTestHelper helper) {
        // Keep this infantry target within the main gun's native depression limit.
        verifyTurret(helper, "m1a2", 2, 0.0D, WeaponPayloadType.SHELL, true, false);
    }

    @GameTest(batch = BATCH, template = "empty", timeoutTicks = 180)
    public static void linkedCoaxialGunUsesParentTurretYaw(GameTestHelper helper) {
        verifyTurret(helper, "m1a2", 2, 7.0D, WeaponPayloadType.BULLET, false, false);
    }

    @GameTest(batch = AMMO_BATCH, template = "empty", timeoutTicks = 300)
    public static void turretConsumesRoundAndReloadsFromVehicleInventory(GameTestHelper helper) {
        verifyTurret(helper, "lav150", 0, 0.0D, WeaponPayloadType.SHELL, false, true);
    }

    private static void verifyTurret(GameTestHelper helper, String model, int elevation, double lateralOffset,
                                     WeaponPayloadType expectedPayload, boolean heavyTarget, boolean checkReload) {
        Arena arena = new Arena(helper);
        var vehicle = arena.vehicle(model, 0.0F);
        var driver = EntityType.VINDICATOR.create(arena.level);
        driver.setPersistenceRequired();
        arena.spawn(driver, arena.origin.add(0.0D, 0.0D, -5.0D));
        var target = EntityType.VILLAGER.create(arena.level);
        target.setNoAi(true);
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 240, 4));
        if (heavyTarget) {
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
            target.setHealth(100.0F);
        }
        Vec3 targetPosition = arena.origin.add(lateralOffset, elevation, 14.0D);
        if (elevation > 0) {
            arena.level.setBlock(BlockPos.containing(targetPosition).below(), Blocks.BEDROCK.defaultBlockState(), 2);
        }
        arena.spawn(target, targetPosition);
        VehicleController controller = VehicleControllers.create(vehicle).orElseThrow();
        AbstractVehicleWeapon<?>[] nativeWeapon = {null};
        boolean[] fired = {false};
        boolean[] reloading = {false};
        helper.runAtTickTime(12, () -> {
            if (driver.getVehicle() != vehicle) {
                helper.assertTrue(controller.mount(driver), "Fixture could not board the turret");
            }
            helper.assertTrue(controller.seatOf(driver).orElseThrow().hasWeapon(), "Turret seat has no compatible weapon");
            helper.assertTrue(controller.hasLineOfSight(driver, target, target.getBoundingBox().getCenter()),
                    "The driving controller cannot see the target before selecting a weapon");
            if (checkReload) {
                controller.initializeGeneratedSupplies();
                WeaponUnit station = (WeaponUnit) vehicle.getOwnOperatorUnit(driver);
                nativeWeapon[0] = station.getCurrentWeapon().orElseThrow();
                helper.assertTrue(nativeWeapon[0].hasStorageAmmo(), "Generated weapon has no reserve ammo");
                nativeWeapon[0].setRemainAmmo(1);
            }
            driver.setTarget(target);
        });
        helper.onEachTick(() -> {
            if (!fired[0] && !arena.level.getEntitiesOfClass(BulletEntity.class, new AABB(arena.origin, target.position()).inflate(32),
                    projectile -> projectile.getOwner() == driver).isEmpty()) {
                helper.assertTrue(VehicleAiMemory.getCurrentWeapon(driver).orElseThrow().payloadType() == expectedPayload,
                        "The AI did not fire the expected payload");
                fired[0] = true;
                if (!checkReload) {
                    helper.succeed();
                    return;
                }
                helper.assertTrue(nativeWeapon[0].getRemainAmmo() == 0, "Native fire did not consume the last round");
                driver.targetSelector.removeAllGoals(goal -> true);
                driver.setTarget(null);
            }
            if (checkReload && fired[0]) {
                reloading[0] |= nativeWeapon[0].isReloading();
                if (reloading[0] && nativeWeapon[0].hasAmmo()) {
                    helper.succeed();
                }
            }
            if (!fired[0] && helper.getTick() == 155) {
                var weapon = controller.weapons(driver).stream().findFirst();
                helper.fail("No AI projectile: driver=" + driver.getVehicle() + ", target=" + driver.getTarget()
                        + ", pos=" + vehicle.position() + ", power=" + vehicle.getPower()
                        + ", selection=" + VehicleAiMemory.getCurrentWeapon(driver) + ", weapon=" + weapon
                        + ", window=" + weapon.map(handle -> controller.fireWindow(driver, target,
                        controller.aimPoint(driver, target, handle), handle)));
            }
        });
    }

    @GameTest(batch = BATCH, template = "empty", timeoutTicks = 80)
    public static void spawnPoolFiltersUnavailableEntriesAndBoardsAfterEjection(GameTestHelper helper) {
        Arena arena = new Arena(helper);
        double oldChance = CommonConfig.NATURAL_VEHICLE_CHANCE.get();
        List<? extends String> oldPool = CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.get();
        Mob driver = EntityType.VINDICATOR.create(arena.level);
        try {
            CommonConfig.NATURAL_VEHICLE_CHANCE.set(1.0D);
            CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(List.of(
                    "not_installed:vehicle, 1000000000",
                    "ywzj_vehicle:nonexistent_pack_model, 1000000000",
                    "ywzj_vehicle:ah_6, 1000000000",
                    "ywzj_vehicle:atv_mrzr4, 1"));
            arena.spawn(driver, arena.origin);
        } finally {
            CommonConfig.NATURAL_VEHICLE_CHANCE.set(oldChance);
            CommonConfig.VINDICATOR_RAID_VEHICLE_TYPES.set(oldPool);
        }
        helper.assertTrue(!driver.isPassenger(), "Generated LV passenger mounted before the initial ejection tick");
        helper.succeedWhen(() -> {
            helper.assertTrue(driver.getVehicle() instanceof AbstractVehicle, "Filtered pool did not spawn and board an LV vehicle");
            AbstractVehicle vehicle = (AbstractVehicle) driver.getVehicle();
            helper.assertTrue(vehicle.getVehicleId().equals(new ResourceLocation("ywzj_vehicle", "atv_mrzr4")),
                    "Unavailable entries consumed the spawn weight");
            helper.assertTrue(vehicle.tickCount >= 2 && vehicle.getDriver() == driver && vehicle.getEnergy() > 0.0F,
                    "Generated driver did not survive initial ticks with a usable vehicle");
        });
    }

    private static final class Arena {
        private final ServerLevel level;
        private final Vec3 origin;

        private Arena(GameTestHelper helper) {
            this.level = helper.getLevel();
            BlockPos center = new BlockPos(1600 + nextArena++ * 96, 4, 6608);
            this.origin = Vec3.atBottomCenterOf(center);
            List<BlockPos> chunks = new ArrayList<>();
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos chunk = new BlockPos((center.getX() >> 4) + x, 0, (center.getZ() >> 4) + z);
                    this.level.setChunkForced(chunk.getX(), chunk.getZ(), true);
                    chunks.add(chunk);
                }
            }
            for (int x = -20; x <= 20; x++) {
                for (int z = -18; z <= 30; z++) {
                    this.level.setBlock(center.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
                }
            }
            CLEANUP.add(() -> {
                this.level.getEntities((Entity) null, new AABB(center).inflate(37.0D), entity -> !(entity instanceof Player))
                        .forEach(Entity::discard);
                chunks.forEach(chunk -> this.level.setChunkForced(chunk.getX(), chunk.getZ(), false));
            });
        }

        private AbstractVehicle vehicle(String id, float yaw) {
            AbstractVehicle vehicle = (AbstractVehicle) this.vehicle(new ResourceLocation("ywzj_vehicle", id), yaw);
            vehicle.setEnergy(vehicle.energyInfo.energyCapacity);
            return vehicle;
        }

        private Entity vehicle(ResourceLocation id, float yaw) {
            Entity entity = VehicleSpawnCandidates.resolve(id).orElseThrow().create(this.level, this.origin);
            VehicleControllers.create(entity).orElseThrow().placeGenerationCandidate(this.origin, yaw);
            this.level.addFreshEntity(entity);
            return entity;
        }

        private <T extends Entity> T spawn(T entity, Vec3 position) {
            entity.setPos(position);
            this.level.addFreshEntity(entity);
            return entity;
        }
    }
}
