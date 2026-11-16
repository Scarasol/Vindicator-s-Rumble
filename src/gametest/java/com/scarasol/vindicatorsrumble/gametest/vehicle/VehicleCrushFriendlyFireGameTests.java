package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.mojang.authlib.GameProfile;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwGameTestVehicle;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.common.VehicleAiGoal;
import com.scarasol.vindicatorsrumble.util.AllegianceUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import tallestegg.guardvillagers.GuardEntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@GameTestHolder("vindicators_rumble_crush")
@PrefixGameTestTemplate(false)
public final class VehicleCrushFriendlyFireGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();
    private static final ResourceLocation TANK = new ResourceLocation("superbwarfare", "m_1a_2");
    private static final ResourceLocation PICKUP = new ResourceLocation("superbwarfare", "sodayo_pick_up_hmg");

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void sameTypeFriendIsProtectedBeforeHurt(GameTestHelper helper) {
        verifyFriendlyMob(helper, EntityType.VINDICATOR, false);
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void forcedDamageCannotBypassProtection(GameTestHelper helper) {
        verifyFriendlyMob(helper, EntityType.VINDICATOR, true);
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void alliedRaiderIsProtected(GameTestHelper helper) {
        verifyFriendlyMob(helper, EntityType.PILLAGER, true);
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void alliedVexIsProtected(GameTestHelper helper) {
        verifyFriendlyMob(helper, EntityType.VEX, true);
    }

    private static void verifyFriendlyMob(GameTestHelper helper, EntityType<? extends Mob> type, boolean force) {
        run(helper, true, fixture -> {
            fixture.driver(EntityType.VINDICATOR);
            Mob friend = fixture.victim(type);
            fixture.impact(force);
            fixture.assertProtected(friend);
        });
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void guardTankProtectsAnotherGuard(GameTestHelper helper) {
        verifyGuardCrush(helper, TANK, GuardEntityType.GUARD.get(), false);
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void guardTankProtectsAnotherGuardWithForcedDamage(GameTestHelper helper) {
        verifyGuardCrush(helper, TANK, GuardEntityType.GUARD.get(), true);
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void guardPickupProtectsAnotherGuard(GameTestHelper helper) {
        verifyGuardCrush(helper, PICKUP, GuardEntityType.GUARD.get(), true);
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void guardTankProtectsVillager(GameTestHelper helper) {
        verifyGuardCrush(helper, TANK, EntityType.VILLAGER, true);
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void guardPickupProtectsVillager(GameTestHelper helper) {
        verifyGuardCrush(helper, PICKUP, EntityType.VILLAGER, true);
    }

    private static void verifyGuardCrush(GameTestHelper helper, ResourceLocation vehicleType,
                                         EntityType<? extends Mob> victimType, boolean force) {
        run(helper, true, vehicleType, fixture -> {
            fixture.driver(GuardEntityType.GUARD.get());
            Mob friend = fixture.victim(victimType);
            fixture.impact(force);
            fixture.assertProtected(friend);
        });
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void guardVillageAllianceIsSymmetricAndUsedByProjectiles(GameTestHelper helper) {
        run(helper, true, fixture -> {
            Mob guard = fixture.driver(GuardEntityType.GUARD.get());
            Mob villager = fixture.victim(EntityType.VILLAGER);
            Mob raider = fixture.victim(EntityType.PILLAGER);
            helper.assertTrue(AllegianceUtil.isSameSide(guard, villager),
                    "Guard did not recognize a villager as friendly");
            helper.assertTrue(AllegianceUtil.isSameSide(villager, guard),
                    "Village alliance was not symmetric");
            helper.assertTrue(AllegianceUtil.shouldProjectileIgnoreTarget(villager, guard),
                    "Guard projectiles did not use the shared village alliance");
            helper.assertFalse(AllegianceUtil.isSameSide(guard, raider),
                    "Village alliance accidentally protected an illager");
        });
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void hostileStillTakesCrushDamage(GameTestHelper helper) {
        run(helper, true, fixture -> {
            fixture.driver(EntityType.VINDICATOR);
            Mob enemy = fixture.victim(EntityType.VILLAGER);
            fixture.impact(true);
            fixture.assertDamaged(enemy);
        });
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void protectingOneFriendDoesNotSkipTheEnemy(GameTestHelper helper) {
        run(helper, true, fixture -> {
            fixture.driver(EntityType.VINDICATOR);
            Mob friend = fixture.victim(EntityType.PILLAGER);
            Mob enemy = fixture.victim(EntityType.VILLAGER);
            friend.setPos(friend.position().add(-0.4D, 0.0D, 0.0D));
            enemy.setPos(enemy.position().add(0.4D, 0.0D, 0.0D));
            fixture.impact(true);
            fixture.assertProtected(friend);
            fixture.assertDamaged(enemy);
        });
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void scoreboardAllianceUsesTheSharedRelationship(GameTestHelper helper) {
        run(helper, true, fixture -> {
            Mob driver = fixture.driver(EntityType.VINDICATOR);
            Mob friend = fixture.victim(EntityType.VILLAGER);
            fixture.team(driver, friend);
            fixture.impact(true);
            fixture.assertProtected(friend);
        });
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void hotDisablePreservesHistoricalAiProtection(GameTestHelper helper) {
        run(helper, true, fixture -> {
            fixture.driver(EntityType.VINDICATOR);
            Mob friend = fixture.victim(EntityType.PILLAGER);
            CommonConfig.ENABLE_VEHICLE_AI.set(false);
            fixture.impact(true);
            fixture.assertProtected(friend);
        });
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void hotEnableDoesNotProtectHistoricalDisabledDrivers(GameTestHelper helper) {
        run(helper, false, fixture -> {
            fixture.driver(EntityType.VINDICATOR);
            Mob other = fixture.victim(EntityType.PILLAGER);
            CommonConfig.ENABLE_VEHICLE_AI.set(true);
            fixture.impact(true);
            fixture.assertDamaged(other);
        });
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void untaggedDriverKeepsNativeDamage(GameTestHelper helper) {
        run(helper, true, fixture -> {
            fixture.driver(EntityType.COW);
            Mob other = fixture.victim(EntityType.COW);
            fixture.impact(true);
            fixture.assertDamaged(other);
        });
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void projectileFriendlyFilterKeepsItsOriginalScope(GameTestHelper helper) {
        run(helper, true, fixture -> {
            Mob shooter = fixture.driver(EntityType.VINDICATOR);
            Mob friend = fixture.victim(EntityType.PILLAGER);
            Mob enemy = fixture.victim(EntityType.VILLAGER);
            helper.assertTrue(AllegianceUtil.shouldProjectileIgnoreTarget(friend, shooter),
                    "Projectile filtering stopped protecting an allied mob");
            helper.assertTrue(AllegianceUtil.shouldProjectileIgnoreTarget(fixture.vehicle, shooter),
                    "Projectile filtering stopped protecting the shooter's own vehicle");
            helper.assertFalse(AllegianceUtil.shouldProjectileIgnoreTarget(enemy, shooter),
                    "Projectile filtering protected a hostile target");
            helper.assertFalse(AllegianceUtil.shouldProjectileIgnoreTarget(friend, null),
                    "Ownerless projectiles gained friendly protection");

            Mob untagged = fixture.mob(EntityType.COW, 4.0D);
            Mob sameType = fixture.mob(EntityType.COW, 5.0D);
            helper.assertFalse(AllegianceUtil.shouldProjectileIgnoreTarget(sameType, untagged),
                    "Projectile filtering expanded to an untagged shooter");
        });
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void projectileTagsRemainIndependentFromCrushAdmission(GameTestHelper helper) {
        run(helper, false, fixture -> {
            Mob shooter = fixture.driver(EntityType.VINDICATOR);
            Mob friend = fixture.victim(EntityType.PILLAGER);
            helper.assertTrue(AllegianceUtil.shouldProjectileIgnoreTarget(friend, shooter),
                    "Existing tag-based projectile filtering became dependent on AI admission");
            fixture.impact(true);
            fixture.assertDamaged(friend);
        });
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void playerDriverWithAiGunnerKeepsNativeDamage(GameTestHelper helper) {
        run(helper, true, fixture -> {
            var player = FakePlayerFactory.get(fixture.level, new GameProfile(UUID.randomUUID(), "vr_crush"));
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(fixture.vehicle.position());
            fixture.entities.add(player);
            helper.assertTrue(player.startRiding(fixture.vehicle, true), "Player could not occupy the driver seat");
            fixture.driver(EntityType.VINDICATOR);
            Mob other = fixture.victim(EntityType.VILLAGER);
            fixture.team(player, other);
            fixture.impact(true);
            fixture.assertDamaged(other);
        });
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void emptyDriverSeatWithAiPassengerKeepsNativeDamage(GameTestHelper helper) {
        run(helper, true, fixture -> {
            Mob passenger = fixture.driver(EntityType.VINDICATOR);
            helper.assertTrue(SbwGameTestVehicle.movePassengerToSeat(fixture.vehicle, passenger, 1),
                    "Could not leave the driver seat empty");
            Mob other = fixture.victim(EntityType.PILLAGER);
            fixture.impact(true);
            fixture.assertDamaged(other);
        });
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void friendlyVehiclesKeepImpulseWithoutMutualDamage(GameTestHelper helper) {
        verifyVehicleCollision(helper, EntityType.PILLAGER, false, true);
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void sameModelHostileVehicleStillTakesMutualDamage(GameTestHelper helper) {
        verifyVehicleCollision(helper, EntityType.VILLAGER, false, false);
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void mixedPassengersDoNotGrantVehicleImmunity(GameTestHelper helper) {
        verifyVehicleCollision(helper, EntityType.PILLAGER, true, false);
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void emptyVehicleStillTakesMutualDamage(GameTestHelper helper) {
        verifyVehicleCollision(helper, null, false, false);
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void guardVehiclesKeepImpulseWithoutMutualDamage(GameTestHelper helper) {
        verifyVehicleCollision(helper, GuardEntityType.GUARD.get(), GuardEntityType.GUARD.get(), false, true);
    }

    private static void verifyVehicleCollision(GameTestHelper helper, EntityType<? extends Mob> occupant,
                                               boolean hostilePassenger, boolean protectedCollision) {
        verifyVehicleCollision(helper, EntityType.VINDICATOR, occupant, hostilePassenger, protectedCollision);
    }

    private static void verifyVehicleCollision(GameTestHelper helper, EntityType<? extends Mob> driver,
                                               EntityType<? extends Mob> occupant,
                                               boolean hostilePassenger, boolean protectedCollision) {
        run(helper, true, fixture -> {
            fixture.driver(driver);
            Entity other = fixture.vehicle(1.5D);
            if (occupant != null) {
                helper.assertTrue(fixture.mob(occupant, 1.5D).startRiding(other, true),
                        "Other vehicle's driver could not mount");
            }
            if (hostilePassenger) {
                helper.assertTrue(fixture.mob(EntityType.VILLAGER, 1.5D).startRiding(other, true),
                        "Hostile passenger could not mount");
            }
            float selfHealth = SbwGameTestVehicle.vehicleHealth(fixture.vehicle);
            float otherHealth = SbwGameTestVehicle.vehicleHealth(other);
            fixture.impact(true);
            if (protectedCollision) {
                helper.assertTrue(SbwGameTestVehicle.vehicleHealth(fixture.vehicle) == selfHealth
                                && SbwGameTestVehicle.vehicleHealth(other) == otherHealth,
                        "Friendly vehicle collision inflicted direct or reciprocal damage");
            } else {
                helper.assertTrue(SbwGameTestVehicle.vehicleHealth(fixture.vehicle) < selfHealth
                                && SbwGameTestVehicle.vehicleHealth(other) < otherHealth,
                        "An enemy or unowned vehicle lost native direct or reciprocal collision damage: self="
                                + selfHealth + "->" + SbwGameTestVehicle.vehicleHealth(fixture.vehicle)
                                + ", other=" + otherHealth + "->" + SbwGameTestVehicle.vehicleHealth(other)
                                + ", otherMotion=" + other.getDeltaMovement() + ", " + fixture.diagnostics);
            }
            helper.assertTrue(other.getDeltaMovement().horizontalDistanceSqr() > 0.0D,
                    "Vehicle collision lost its native physical response");
        });
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 40)
    public static void damageOutsideCrushCallKeepsNativeBehavior(GameTestHelper helper) {
        run(helper, true, fixture -> {
            fixture.driver(EntityType.VINDICATOR);
            Mob friend = fixture.victim(EntityType.PILLAGER);
            SbwGameTestVehicle.directVehicleStrike(friend, fixture.vehicle, 4.0F);
            fixture.assertDamaged(friend);
        });
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 140)
    public static void nativeVehicleTickProtectsFriendAndStillHitsEnemy(GameTestHelper helper) {
        verifyNativeVehicleTick(helper, TANK, EntityType.VINDICATOR, EntityType.PILLAGER, EntityType.VILLAGER);
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 140)
    public static void guardPickupNativeTickProtectsGuardAndHitsRaider(GameTestHelper helper) {
        verifyNativeVehicleTick(helper, PICKUP, GuardEntityType.GUARD.get(),
                GuardEntityType.GUARD.get(), EntityType.PILLAGER);
    }

    @GameTest(templateNamespace = "vindicators_rumble", template = "empty", timeoutTicks = 140)
    public static void guardPickupNativeTickProtectsVillagerAndHitsRaider(GameTestHelper helper) {
        verifyNativeVehicleTick(helper, PICKUP, GuardEntityType.GUARD.get(), EntityType.VILLAGER, EntityType.PILLAGER);
    }

    private static void verifyNativeVehicleTick(GameTestHelper helper, ResourceLocation vehicleType,
                                                EntityType<? extends Mob> driver,
                                                EntityType<? extends Mob> friend, EntityType<? extends Mob> enemy) {
        Fixture fixture = new Fixture(helper, true, vehicleType);
        Mob[] victims = new Mob[2];
        int[] startTick = {0};
        helper.runAtTickTime(135, fixture::close);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(fixture.ready(),
                "Waiting for the fixture's normal vehicle tick")).thenExecute(() -> {
            fixture.begin();
            fixture.driver(driver);
            victims[0] = fixture.victim(friend);
            victims[1] = fixture.victim(enemy);
            victims[0].setPos(victims[0].position().add(-0.4D, 0.0D, 0.0D));
            victims[1].setPos(victims[1].position().add(0.4D, 0.0D, 0.0D));
            startTick[0] = fixture.vehicle.tickCount;
            fixture.vehicle.setDeltaMovement(0.0D, 0.0D, 0.8D);
        }).thenWaitUntil(() -> helper.assertTrue(fixture.vehicle.tickCount > startTick[0],
                "Waiting for the normal collision tick")).thenExecute(() -> {
            try {
                fixture.assertProtected(victims[0]);
                fixture.assertDamaged(victims[1]);
            } finally {
                fixture.close();
            }
        }).thenSucceed();
    }

    private static void run(GameTestHelper helper, boolean enabled, Consumer<Fixture> scenario) {
        run(helper, enabled, TANK, scenario);
    }

    private static void run(GameTestHelper helper, boolean enabled, ResourceLocation vehicleType,
                            Consumer<Fixture> scenario) {
        Fixture fixture = new Fixture(helper, enabled, vehicleType);
        helper.runAtTickTime(35, fixture::close);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(fixture.ready(),
                "Waiting for the collision arena's normal entity tracking")).thenExecute(() -> {
            fixture.begin();
            try {
                scenario.accept(fixture);
            } finally {
                fixture.close();
            }
        }).thenSucceed();
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos origin;
        private final List<Entity> entities = new ArrayList<>();
        private final List<Mob> victims = new ArrayList<>();
        private final List<Entity> attacked = new ArrayList<>();
        private final List<BlockPos> chunks = new ArrayList<>();
        private final List<PlayerTeam> teams = new ArrayList<>();
        private final Consumer<LivingAttackEvent> attack;
        private boolean previousEnabled;
        private double previousNatural;
        private final boolean enabled;
        private final ResourceLocation vehicleType;
        private final Entity vehicle;
        private String diagnostics;
        private boolean started;
        private boolean closed;

        private Fixture(GameTestHelper helper, boolean enabled, ResourceLocation vehicleType) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.enabled = enabled;
            this.vehicleType = vehicleType;
            this.origin = new BlockPos(30000 + ARENA.getAndIncrement() * 96, 5, 30000);
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos chunk = new BlockPos((origin.getX() >> 4) + x, 0, (origin.getZ() >> 4) + z);
                    level.setChunkForced(chunk.getX(), chunk.getZ(), true);
                    chunks.add(chunk);
                }
            }
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 12; z++) {
                    level.setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
                }
            }
            vehicle = vehicle(0.0D);
            attack = event -> {
                if (victims.contains(event.getEntity())) attacked.add(event.getEntity());
            };
            MinecraftForge.EVENT_BUS.addListener(attack);
        }

        private void begin() {
            previousEnabled = CommonConfig.ENABLE_VEHICLE_AI.get();
            previousNatural = CommonConfig.NATURAL_VEHICLE_CHANCE.get();
            CommonConfig.ENABLE_VEHICLE_AI.set(enabled);
            CommonConfig.NATURAL_VEHICLE_CHANCE.set(0.0D);
            started = true;
        }

        private boolean ready() {
            return vehicle.tickCount > 0
                    && level.getEntities((Entity) null, vehicle.getBoundingBox()).contains(vehicle);
        }

        private Entity vehicle(double offset) {
            Entity entity = ForgeRegistries.ENTITY_TYPES.getValue(vehicleType).create(level);
            helper.assertTrue(entity != null, "Missing crush fixture vehicle");
            entity.setPos(Vec3.atBottomCenterOf(origin).add(0.0D, 0.0D, offset));
            helper.assertTrue(level.addFreshEntity(entity), "Crush vehicle fixture was rejected");
            SbwGameTestVehicle.prepareSmokeFixture(entity, 0, 0);
            SbwGameTestVehicle.initializeCollisionBounds(entity);
            entities.add(entity);
            return entity;
        }

        private Mob mob(EntityType<? extends Mob> type, double offset) {
            Mob mob = type.create(level);
            helper.assertTrue(mob != null, "Missing crush fixture mob");
            mob.setPos(Vec3.atBottomCenterOf(origin).add(0.0D, 0.0D, offset));
            mob.setNoAi(true);
            mob.setPersistenceRequired();
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
            mob.setHealth(mob.getMaxHealth());
            helper.assertTrue(level.addFreshEntity(mob), "Crush mob fixture was rejected");
            entities.add(mob);
            return mob;
        }

        private Mob driver(EntityType<? extends Mob> type) {
            Mob mob = mob(type, 0.0D);
            boolean injected = mob.goalSelector.getAvailableGoals().stream()
                    .anyMatch(goal -> goal.getGoal() instanceof VehicleAiGoal);
            helper.assertTrue(injected == (enabled && type != EntityType.COW),
                    "Fixture admission did not match its configured AI scope");
            helper.assertTrue(mob.startRiding(vehicle, true), "Crush fixture occupant could not mount");
            return mob;
        }

        private Mob victim(EntityType<? extends Mob> type) {
            Mob victim = mob(type, 1.0D);
            victims.add(victim);
            return victim;
        }

        private void team(Entity first, Entity second) {
            PlayerTeam team = level.getScoreboard().addPlayerTeam("crush_" + ARENA.get());
            team.setAllowFriendlyFire(true);
            level.getScoreboard().addPlayerToTeam(first.getScoreboardName(), team);
            level.getScoreboard().addPlayerToTeam(second.getScoreboardName(), team);
            teams.add(team);
        }

        private void impact(boolean force) {
            vehicle.setDeltaMovement(0.0D, 0.0D, 0.8D);
            diagnostics = SbwGameTestVehicle.crushDiagnostics(vehicle);
            SbwGameTestVehicle.crushEntities(vehicle, force);
        }

        private void assertProtected(Mob victim) {
            helper.assertTrue(victim.getHealth() == victim.getMaxHealth()
                            && victim.getLastHurtByMob() == null && victim.hurtTime == 0,
                    "Friendly crush reached damage or retaliation state");
            helper.assertTrue(!attacked.contains(victim), "Friend reached LivingAttackEvent before being filtered");
            helper.assertTrue(victim.getDeltaMovement().horizontalDistanceSqr() > 0.0D,
                    "Friendly protection removed the native collision impulse");
        }

        private void assertDamaged(Mob victim) {
            helper.assertTrue(victim.getHealth() < victim.getMaxHealth(),
                    "Native crush fixture did not damage the unprotected victim");
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            MinecraftForge.EVENT_BUS.unregister(attack);
            entities.forEach(entity -> {
                entity.stopRiding();
                if (!(entity instanceof Player)) entity.discard();
            });
            teams.forEach(team -> level.getScoreboard().removePlayerTeam(team));
            chunks.forEach(chunk -> level.setChunkForced(chunk.getX(), chunk.getZ(), false));
            if (started) {
                CommonConfig.ENABLE_VEHICLE_AI.set(previousEnabled);
                CommonConfig.NATURAL_VEHICLE_CHANCE.set(previousNatural);
            }
        }
    }
}
