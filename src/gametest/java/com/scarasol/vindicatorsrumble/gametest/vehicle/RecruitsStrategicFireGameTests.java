package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.talhanation.recruits.CommandEvents;
import com.talhanation.recruits.RecruitEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.entities.BowmanEntity;
import com.talhanation.recruits.world.RecruitsGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder("vindicators_rumble_recruits")
@PrefixGameTestTemplate(false)
public final class RecruitsStrategicFireGameTests {
    private static final BlockPos WALL = new BlockPos(4, 3, 9);
    private static final ResourceLocation TEMPLATE = new ResourceLocation("vindicators_rumble_recruits", "empty");
    private static final Logger LOGGER = LogUtils.getLogger();

    private RecruitsStrategicFireGameTests() {
    }

    @GameTest(batch = "recruits_strategic_fire", template = "empty", timeoutTicks = 40)
    public static void sharedTargetIsHiddenProtectedAndStationary(GameTestHelper helper) {
        Fixture fixture = fixture(helper, "recruit", 2);
        fixture.command(true);
        ArmorStand target = target(helper, fixture.recruits().get(0));
        Vec3 position = target.position();
        helper.assertTrue(fixture.recruits().get(1).getTarget() == target, "One command created separate targets");
        helper.assertTrue(target.isInvisible() && target.isMarker(), "Command target is renderable or pickable");
        helper.assertTrue(target.hasCustomName() && !target.isCustomNameVisible(),
                "Client identification is missing or exposes a name tag");
        helper.assertTrue(target.noPhysics && target.isNoGravity(), "Command target has physics or gravity");
        helper.assertTrue(target.getAttributeValue(ForgeMod.ENTITY_GRAVITY.get()) == 0.0D,
                "Ballistic prediction still treats the stationary target as falling");
        helper.assertTrue(target.getHealth() > 50.0F, "Coordinate bombardment did not prefer heavy weapons");
        target.hurt(fixture.level().damageSources().generic(), 10_000.0F);
        target.hurt(fixture.level().damageSources().inFire(), 10_000.0F);
        target.hurt(fixture.level().damageSources().magic(), 10_000.0F);
        helper.assertTrue(target.isAlive() && target.getHealth() == target.getMaxHealth(),
                "Ordinary damage destroyed or injured the command target");

        helper.runAfterDelay(20, () -> {
            try {
                helper.assertTrue(target.isAlive() && target.position().equals(position),
                        "Command target disappeared or moved: removal=" + target.getRemovalReason()
                                + " recruits=" + fixture.recruits().stream().map(RecruitsStrategicFireGameTests::state).toList());
                helper.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO), "Command target gained velocity");
                helper.succeed();
            } finally {
                fixture.close();
            }
        });
    }

    @GameTest(batch = "recruits_strategic_fire", template = "empty", timeoutTicks = 40)
    public static void cancelAndDismountReleaseOnlyTheirParticipant(GameTestHelper helper) {
        try (Fixture fixture = fixture(helper, "recruit", 2)) {
            fixture.command(true);
            AbstractRecruitEntity first = fixture.recruits().get(0);
            AbstractRecruitEntity second = fixture.recruits().get(1);
            ArmorStand target = target(helper, first);
            CommandEvents.onStrategicFireCommand(fixture.player(), fixture.player().getUUID(), first, fixture.group(), false);
            helper.assertTrue(first.getTarget() == null, "Canceled participant retained the target");
            helper.assertTrue(target.isAlive() && second.getTarget() == target, "Cancellation affected another participant");
            second.stopRiding();
            helper.assertTrue(second.getTarget() == null && target.isRemoved(), "Last participant left an orphan target");
            helper.succeed();
        }
    }

    @GameTest(batch = "recruits_strategic_fire", template = "empty", timeoutTicks = 40)
    public static void cancelledDeathPreservesTheCommandTarget(GameTestHelper helper) {
        try (Fixture fixture = fixture(helper, "bowman", 1)) {
            fixture.command(true);
            AbstractRecruitEntity recruit = fixture.recruits().get(0);
            ArmorStand target = target(helper, recruit);
            int[] deaths = {0};
            Consumer<LivingDeathEvent> cancel = event -> {
                if (event.getEntity() != recruit) return;
                deaths[0]++;
                event.setCanceled(true);
                recruit.setHealth(recruit.getMaxHealth());
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, cancel);
            try {
                recruit.setHealth(0.0F);
                recruit.die(fixture.level().damageSources().genericKill());
                helper.assertTrue(deaths[0] == 1 && recruit.isAlive() && !recruit.isRemoved(),
                        "Recruit did not survive the canceled native death");
                helper.assertTrue(recruit.getVehicle() == fixture.vehicle() && recruit.getTarget() == target
                                && target.isAlive() && !target.isRemoved(),
                        "Canceled death released the live recruit's command target");
                helper.assertTrue(((BowmanEntity) recruit).getShouldStrategicFire(),
                        "Canceled death canceled the native fire command");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(cancel);
            }
            helper.succeed();
        }
    }

    @GameTest(batch = "recruits_strategic_fire", template = "empty", timeoutTicks = 40)
    public static void lateCancelledDismountPreservesTheCommandTarget(GameTestHelper helper) {
        try (Fixture fixture = fixture(helper, "recruit", 1)) {
            fixture.command(true);
            AbstractRecruitEntity recruit = fixture.recruits().get(0);
            ArmorStand target = target(helper, recruit);
            Consumer<EntityMountEvent> cancel = event -> {
                if (event.isDismounting() && event.getEntityMounting() == recruit) event.setCanceled(true);
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, cancel);
            try {
                recruit.stopRiding();
                helper.assertTrue(recruit.getVehicle() == fixture.vehicle() && recruit.getTarget() == target
                                && target.isAlive() && !target.isRemoved(),
                        "Canceled dismount released the mounted recruit's command target");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(cancel);
            }
            helper.succeed();
        }
    }

    @GameTest(batch = "recruits_strategic_fire", template = "empty", timeoutTicks = 40)
    public static void voidDamageCancelsWithoutRespawning(GameTestHelper helper) {
        Fixture fixture = fixture(helper, "bowman", 2);
        fixture.command(true);
        ArmorStand target = target(helper, fixture.recruits().get(0));
        target.hurt(fixture.level().damageSources().fellOutOfWorld(), 0.0F);
        helper.assertTrue(target.isRemoved(), "Void damage did not remove the protected target");
        helper.runAfterDelay(4, () -> {
            try {
                for (AbstractRecruitEntity recruit : fixture.recruits()) {
                    helper.assertTrue(recruit.getTarget() == null, "Destroyed target was retained or recreated");
                    helper.assertFalse(((BowmanEntity) recruit).getShouldStrategicFire(),
                            "Destroying the target left a persistent native fire command");
                }
                helper.succeed();
            } finally {
                fixture.close();
            }
        });
    }

    @GameTest(batch = "recruits_strategic_fire", template = "empty", timeoutTicks = 40)
    public static void ordinaryRecruitCommandSurvivesSaveReload(GameTestHelper helper) {
        verifySaveReload(helper, "recruit");
    }

    @GameTest(batch = "recruits_strategic_fire", template = "empty", timeoutTicks = 40)
    public static void nativeBowmanCommandSurvivesSaveReload(GameTestHelper helper) {
        verifySaveReload(helper, "bowman");
    }

    @GameTest(batch = "recruits_strategic_fire", template = "empty", timeoutTicks = 40)
    public static void commandPreservesOwnershipGroupAndListenChecks(GameTestHelper helper) {
        try (Fixture fixture = fixture(helper, "recruit", 1)) {
            AbstractRecruitEntity recruit = fixture.recruits().get(0);
            FakePlayer stranger = player(fixture.level(), fixture.player().blockPosition());
            CommandEvents.onStrategicFireCommand(stranger, fixture.player().getUUID(), recruit, fixture.group(), true);
            helper.assertTrue(recruit.getTarget() == null, "Foreign sender used the owner's command identity");
            CommandEvents.onStrategicFireCommand(fixture.player(), fixture.player().getUUID(), recruit, UUID.randomUUID(), true);
            helper.assertTrue(recruit.getTarget() == null, "Command reached a different group");
            recruit.setListen(false);
            fixture.command(true);
            helper.assertTrue(recruit.getTarget() == null, "Command reached a recruit that was not listening");
            helper.succeed();
        }
    }

    @GameTest(batch = "recruits_strategic_fire", template = "empty", timeoutTicks = 40)
    public static void replacementTargetReleasesTheOldCommandPoint(GameTestHelper helper) {
        try (Fixture fixture = fixture(helper, "recruit", 2)) {
            fixture.command(true);
            ArmorStand oldTarget = target(helper, fixture.recruits().get(0));
            helper.setBlock(WALL, Blocks.AIR);
            helper.setBlock(WALL.offset(0, 0, 2), Blocks.BEDROCK);
            fixture.command(true);
            ArmorStand newTarget = target(helper, fixture.recruits().get(0));
            helper.assertTrue(oldTarget.isRemoved() && oldTarget != newTarget, "Previous command point was not released");
            helper.assertTrue(fixture.recruits().get(1).getTarget() == newTarget, "Replacement command was not shared");
            helper.succeed();
        }
    }

    @GameTest(batch = "recruits_strategic_fire", template = "empty", timeoutTicks = 40)
    public static void savedPlaceholderIsNotLoadedAsAnOrphan(GameTestHelper helper) {
        try (Fixture fixture = fixture(helper, "recruit", 1)) {
            fixture.command(true);
            ArmorStand active = target(helper, fixture.recruits().get(0));
            ArmorStand restored = new ArmorStand(fixture.level(), 0.0D, 0.0D, 0.0D);
            restored.load(active.saveWithoutId(new CompoundTag()));
            EntityJoinLevelEvent event = new EntityJoinLevelEvent(restored, fixture.level(), true);
            MinecraftForge.EVENT_BUS.post(event);
            helper.assertTrue(event.isCanceled() && restored.isRemoved(), "Saved placeholder became an orphan entity");
            helper.assertTrue(active.isAlive(), "Discarding a saved copy canceled an active command");
            helper.succeed();
        }
    }

    @GameTest(batch = "recruits_strategic_fire", template = "empty", timeoutTicks = 40)
    public static void automaticTargetChangesDoNotOverrideCommand(GameTestHelper helper) {
        try (Fixture fixture = fixture(helper, "recruit", 1)) {
            fixture.command(true);
            AbstractRecruitEntity recruit = fixture.recruits().get(0);
            ArmorStand target = target(helper, recruit);
            recruit.setTarget(null);
            helper.assertTrue(recruit.getTarget() == target, "Ordinary target cleanup canceled the command target");
            fixture.command(false);
            recruit.setTarget(null);
            helper.assertTrue(recruit.getTarget() == null, "Canceled command continued overriding target changes");
            helper.succeed();
        }
    }

    @GameTest(batch = "recruits_strategic_fire", template = "empty", timeoutTicks = 40)
    public static void newFootCommandDoesNotResumeTheOldVehiclePoint(GameTestHelper helper) {
        Fixture fixture = fixture(helper, "bowman", 1);
        fixture.command(true);
        AbstractRecruitEntity recruit = fixture.recruits().get(0);
        ArmorStand oldTarget = target(helper, recruit);
        recruit.stopRiding();
        fixture.command(true);
        helper.assertTrue(((BowmanEntity) recruit).getShouldStrategicFire(),
                "Vehicle integration canceled the new infantry command");
        helper.assertTrue(recruit.startRiding(fixture.vehicle(), true), "Recruit could not remount");
        helper.runAfterDelay(4, () -> {
            try {
                helper.assertTrue(oldTarget.isRemoved() && recruit.getTarget() == null,
                        "New infantry command resumed the previous vehicle command point");
                helper.succeed();
            } finally {
                fixture.close();
            }
        });
    }

    private static void verifySaveReload(GameTestHelper helper, String type) {
        Fixture fixture = fixture(helper, type, 1);
        fixture.command(true);
        AbstractRecruitEntity original = fixture.recruits().get(0);
        ArmorStand oldTarget = target(helper, original);
        Vec3 point = oldTarget.position();
        CompoundTag saved = original.saveWithoutId(new CompoundTag());
        original.discard();
        AbstractRecruitEntity restored = (AbstractRecruitEntity) create(fixture.level(), "recruits", type);
        restored.load(saved);
        fixture.level().addFreshEntity(restored);
        fixture.recruits().add(restored);
        helper.assertTrue(restored.startRiding(fixture.vehicle(), true), "Reloaded recruit could not resume its seat");

        helper.runAfterDelay(4, () -> {
            try {
                ArmorStand target = target(helper, restored);
                helper.assertTrue(target != oldTarget && oldTarget.isRemoved(), "Reload reused an obsolete placeholder");
                helper.assertTrue(target.position().equals(point), "Reload lost the precise command point");
                if (restored instanceof BowmanEntity bowman) {
                    helper.assertTrue(bowman.getShouldStrategicFire(), "Native strategic-fire flag did not survive reload");
                }
                helper.succeed();
            } finally {
                fixture.close();
            }
        });
    }

    private static Fixture fixture(GameTestHelper helper, String type, int count) {
        ServerLevel level = helper.getLevel();
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.BEDROCK);
            }
        }
        helper.setBlock(WALL, Blocks.BEDROCK);
        helper.assertTrue(level.getStructureManager().getOrCreate(TEMPLATE).getSize().getZ() > WALL.getZ() + 2,
                "Command wall extends beyond the isolated test template");
        FakePlayer player = player(level, helper.absolutePos(new BlockPos(4, 2, 2)));
        Entity vehicle = create(level, "superbwarfare", "sodayo_pick_up_hmg");
        vehicle.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4))));
        vehicle.setNoGravity(true);
        level.addFreshEntity(vehicle);
        RecruitsGroup group = new RecruitsGroup("strategic_fire_test", player, 0);
        RecruitEvents.recruitsGroupsManager.addOrUpdateGroup(level, player, group);
        List<AbstractRecruitEntity> recruits = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            AbstractRecruitEntity recruit = (AbstractRecruitEntity) create(level, "recruits", type);
            // These cases isolate command lifecycle, not vehicle movement or weapon physics.
            recruit.setNoAi(true);
            recruit.setIsOwned(true);
            recruit.setOwnerUUID(Optional.of(player.getUUID()));
            group.addMember(recruit.getUUID());
            recruit.setGroupUUID(group.getUUID());
            recruit.setListen(true);
            recruit.setPos(vehicle.position());
            level.addFreshEntity(recruit);
            helper.assertTrue(recruit.startRiding(vehicle), "Recruit fixture could not enter the vehicle");
            recruits.add(recruit);
        }
        return new Fixture(level, player, vehicle, group.getUUID(), recruits);
    }

    private static FakePlayer player(ServerLevel level, BlockPos position) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "vr_fire"));
        player.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
        player.xRotO = player.getXRot();
        player.yRotO = player.getYRot();
        player.setYHeadRot(0.0F);
        player.yHeadRotO = 0.0F;
        return player;
    }

    private static Entity create(ServerLevel level, String namespace, String id) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(namespace, id));
        if (type == null) throw new AssertionError("Missing fixture entity " + namespace + ":" + id);
        Entity entity = type.create(level);
        if (entity == null) throw new AssertionError("Could not create fixture entity " + namespace + ":" + id);
        return entity;
    }

    private static ArmorStand target(GameTestHelper helper, AbstractRecruitEntity recruit) {
        helper.assertTrue(recruit.getTarget() instanceof ArmorStand, "Command target missing: " + state(recruit));
        return (ArmorStand) recruit.getTarget();
    }

    private static String state(AbstractRecruitEntity recruit) {
        return "id=" + recruit.getId() + " alive=" + recruit.isAlive() + " riding=" + recruit.isPassenger()
                + " pos=" + recruit.position() + " follow=" + recruit.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)
                + " target=" + recruit.getTarget() + " listen=" + recruit.getListen() + " owned=" + recruit.isOwned()
                + " owner=" + recruit.getOwnerUUID() + " group=" + recruit.getGroup() + " data=" + recruit.getPersistentData();
    }

    private record Fixture(ServerLevel level, FakePlayer player, Entity vehicle, UUID group,
                           List<AbstractRecruitEntity> recruits) implements AutoCloseable {
        void command(boolean shouldFire) {
            for (AbstractRecruitEntity recruit : recruits) {
                LOGGER.info("Strategic fixture command={} ray={} hit={} recruit={}", shouldFire,
                        player.pick(200.0D, 1.0F, true).getType(),
                        player.pick(200.0D, 1.0F, true).getLocation(), state(recruit));
                CommandEvents.onStrategicFireCommand(player, player.getUUID(), recruit, group, shouldFire);
                LOGGER.info("Strategic fixture result={}", state(recruit));
            }
        }

        @Override
        public void close() {
            recruits.forEach(Entity::discard);
            vehicle.discard();
            RecruitEvents.recruitsGroupsManager.removeGroup(group);
        }
    }
}
