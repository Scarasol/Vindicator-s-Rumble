package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.mojang.logging.LogUtils;
import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponHandle;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwGameTestVehicle;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.sona.init.SonaMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder("vindicators_rumble_recruits")
@PrefixGameTestTemplate(false)
public final class HelicopterCombatGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();

    private HelicopterCombatGameTests() {
    }

    @GameTest(batch = "helicopter_combat", template = "empty", timeoutTicks = 260)
    public static void ah6FiresAtALevelAirborneTargetWithoutDivingAtTheGround(GameTestHelper helper) {
        verifyAirCombat(helper, "ah_6", 0.0D);
    }

    @GameTest(batch = "helicopter_combat", template = "empty", timeoutTicks = 260)
    public static void mi28FiresAtASlightlyLowerAirborneTargetWithoutASteepDive(GameTestHelper helper) {
        verifyAirCombat(helper, "mi_28", -4.0D);
    }

    @GameTest(batch = "helicopter_combat", template = "empty", timeoutTicks = 80)
    public static void heavyWeaponUsesGroundImpactOnlyForAGroundedTarget(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "ah_6", 0.0D);
        WeaponHandle rocket = fixture.controller.weapons(fixture.driver).stream()
                .filter(weapon -> weapon.payloadType().isHeavyTargetPreferred()).findFirst().orElseThrow();
        fixture.target.stopRiding();
        Vec3 groundPosition = Vec3.atBottomCenterOf(fixture.origin.offset(0, 0, 40));
        fixture.target.setPos(groundPosition);
        fixture.target.setNoGravity(false);
        fixture.target.setOnGround(true);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(fixture.target.onGround(), "Grounded target has not settled");
        }).thenExecute(() -> {
            Vec3 point = fixture.controller.aimPoint(fixture.driver, fixture.target, rocket);
            helper.assertTrue(Math.abs(point.y - groundPosition.y) < 0.2D,
                    "Grounded explosive target no longer uses a ground impact: " + point);
            fixture.target.setPos(groundPosition.add(0.0D, 32.0D, 0.0D));
            fixture.target.setOnGround(false);
            fixture.target.setNoGravity(true);
            point = fixture.controller.aimPoint(fixture.driver, fixture.target, rocket);
            helper.assertTrue(point.distanceTo(fixture.target.getBoundingBox().getCenter()) < 0.01D,
                    "Unmounted airborne target was projected onto the ground: " + point);
            fixture.close();
            helper.succeed();
        });
    }

    private static void verifyAirCombat(GameTestHelper helper, String helicopter, double targetHeightOffset) {
        Fixture fixture = new Fixture(helper, helicopter, targetHeightOffset);
        long[] acquiredAt = {-1};
        int[] alignedTicks = {0};
        double[] maxShallowPitch = {0.0D};
        double[] highestTargetY = {fixture.targetVehicle.getBoundingBox().getCenter().y};
        helper.onEachTick(() -> {
            helper.assertTrue(fixture.driver.isPassenger() && fixture.target.isPassenger(),
                    "Air combat lost a passenger");
            if (helper.getTick() % 20 == 0) {
                LogUtils.getLogger().info("Helicopter air combat tick={} vehicle={} driverTick={} targetTick={} acquired={} position={} targetPosition={} pitch={} goals={}",
                        helper.getTick(), helicopter, fixture.driver.tickCount, fixture.target.tickCount,
                        fixture.driver.getTarget(), fixture.vehicle.position(), fixture.targetVehicle.position(),
                        fixture.controller.pitch(), fixture.driver.goalSelector.getRunningGoals()
                                .map(goal -> goal.getGoal().getClass().getSimpleName()).toList());
            }
            if (fixture.driver.getTarget() != fixture.target) {
                helper.assertTrue(acquiredAt[0] < 0, "Naturally acquired air target was lost");
                return;
            }
            if (acquiredAt[0] < 0) acquiredAt[0] = helper.getTick();
            Vec3 center = fixture.targetVehicle.getBoundingBox().getCenter();
            highestTargetY[0] = Math.max(highestTargetY[0], center.y);
            helper.assertTrue(fixture.vehicle.getY() < highestTargetY[0] + 12.0D,
                    "Air pass kept climbing instead of tracking target height: aircraftY="
                            + fixture.vehicle.getY() + " highestTargetY=" + highestTargetY[0]);
            List<WeaponHandle> heavyWeapons = fixture.controller.weapons(fixture.driver).stream()
                    .filter(weapon -> weapon.payloadType().isHeavyTargetPreferred()).toList();
            helper.assertFalse(heavyWeapons.isEmpty(), "Air combat fixture has no heavy weapon");
            for (WeaponHandle weapon : heavyWeapons) {
                Vec3 point = fixture.controller.aimPoint(fixture.driver, fixture.target, weapon);
                helper.assertTrue(point.distanceTo(center) < 0.01D,
                        "Air weapon " + weapon.name() + " aims away from the target vehicle: " + point + " vs " + center);
                if (fixture.controller.isAimedAt(fixture.driver, fixture.target, point, weapon)) {
                    alignedTicks[0]++;
                }
            }
            Vec3 delta = center.subtract(fixture.vehicle.position());
            double elevation = -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
            if (delta.horizontalDistance() > 20.0D && Math.abs(elevation) < 15.0D) {
                double pitch = Math.abs(fixture.controller.pitch());
                maxShallowPitch[0] = Math.max(maxShallowPitch[0], pitch);
                helper.assertTrue(pitch < 45.0D,
                        "Shallow airborne target forced a steep dive: elevation=" + elevation + " pitch=" + pitch);
            }
            if (helper.getTick() - acquiredAt[0] < 120) return;
            LogUtils.getLogger().info("Helicopter air combat vehicle={} targetOffset={} alignedTicks={} firedShots={} maxShallowPitch={} distance={} elevation={}",
                    helicopter, targetHeightOffset, alignedTicks[0], fixture.firedShots, maxShallowPitch[0],
                    delta.horizontalDistance(), elevation);
            helper.assertTrue(alignedTicks[0] > 0, "Aircraft never aimed a weapon at the actual airborne target");
            helper.assertTrue(fixture.firedShots > 0, "Air combat never fired with the actual target UUID");
            fixture.close();
            helper.succeed();
        });
    }

    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;
        final BlockPos origin;
        final List<BlockPos> chunks = new ArrayList<>();
        final Entity vehicle;
        final Entity targetVehicle;
        final Mob driver;
        final Mob target;
        final VehicleController controller;
        final org.apache.logging.log4j.core.Logger logger;
        final AbstractAppender observer;
        int firedShots;

        Fixture(GameTestHelper helper, String helicopter, double targetHeightOffset) {
            CommonConfig.VEHICLE_AI_DEBUG.set(true);
            this.level = helper.getLevel();
            this.origin = new BlockPos(4096 + ARENA.getAndIncrement() * 256, 5, 4096);
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 7; z++) {
                    BlockPos chunk = new BlockPos((this.origin.getX() >> 4) + x, 0, (this.origin.getZ() >> 4) + z);
                    this.level.setChunkForced(chunk.getX(), chunk.getZ(), true);
                    this.chunks.add(chunk);
                }
            }
            for (int x = -32; x <= 32; x++) {
                for (int z = -32; z <= 112; z++) {
                    this.level.setBlock(this.origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
                }
            }
            this.vehicle = this.create(helicopter, Vec3.atBottomCenterOf(this.origin).add(0.0D, 32.0D, 0.0D));
            this.targetVehicle = this.create(helicopter,
                    Vec3.atBottomCenterOf(this.origin.offset(0, 0, 40)).add(0.0D, 32.0D + targetHeightOffset, 0.0D));
            this.controller = VehicleControllers.create(this.vehicle).orElseThrow();
            this.target = EntityType.VILLAGER.create(this.level);
            this.target.setNoAi(true);
            this.target.setPersistenceRequired();
            this.target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255, false, false));
            this.target.setPos(this.targetVehicle.position());
            this.target.addEffect(new MobEffectInstance(SonaMobEffects.EXPOSURE.get(), 260, 3, false, false));
            this.level.addFreshEntity(this.target);
            helper.assertTrue(this.target.startRiding(this.targetVehicle, true), "Air target could not mount");
            this.driver = EntityType.VINDICATOR.create(this.level);
            this.driver.setPersistenceRequired();
            this.driver.setInvulnerable(true);
            this.driver.setPos(this.vehicle.position());
            this.level.addFreshEntity(this.driver);
            helper.assertTrue(this.driver.startRiding(this.vehicle, true), "Combat driver could not mount");
            SbwGameTestVehicle.prepareArmedAirborne(this.vehicle);
            this.logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(
                    "com.scarasol.vindicatorsrumble.VindicatorsRumbleMod");
            String driverPrefix = "[entity.minecraft.vindicator#" + this.driver.getId() + "]";
            this.observer = new AbstractAppender("air_combat_" + this.driver.getId(), null, null, false) {
                @Override
                public void append(LogEvent event) {
                    String message = event.getMessage().getFormattedMessage();
                    if (message.contains(driverPrefix) && message.contains("SBW fire called")
                            && message.contains("groundTarget=false") && message.contains("canShootBefore=true")) {
                        firedShots++;
                    }
                }
            };
            this.observer.start();
            this.logger.addAppender(this.observer);
        }

        private Entity create(String helicopter, Vec3 position) {
            Entity entity = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("superbwarfare", helicopter))
                    .create(this.level);
            entity.setPos(position);
            entity.setInvulnerable(true);
            this.level.addFreshEntity(entity);
            SbwGameTestVehicle.prepareUnarmedAirborne(entity);
            SbwGameTestVehicle.setHoverFixture(entity);
            return entity;
        }

        @Override
        public void close() {
            this.logger.removeAppender(this.observer);
            this.observer.stop();
            this.driver.discard();
            this.target.discard();
            this.vehicle.discard();
            this.targetVehicle.discard();
            this.chunks.forEach(chunk -> this.level.setChunkForced(chunk.getX(), chunk.getZ(), false));
        }
    }
}
