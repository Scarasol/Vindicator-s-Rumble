package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.compat.VehicleControllers;
import com.scarasol.vindicatorsrumble.compat.sbw.SbwGameTestVehicle;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@GameTestHolder("vindicators_rumble")
@PrefixGameTestTemplate(false)
public final class GroundHoldInputGameTests {
    private static final AtomicInteger ARENA = new AtomicInteger();
    private static final List<Runnable> CLEANUP = new ArrayList<>();
    private static boolean previousEnabled;
    private static boolean previousAmmo;
    private static double previousNatural;

    @BeforeBatch(batch = "ground_hold_inputs")
    public static void prepare(ServerLevel level) {
        previousEnabled = CommonConfig.ENABLE_VEHICLE_AI.get();
        previousAmmo = CommonConfig.VEHICLE_AI_NEEDS_AMMO.get();
        previousNatural = CommonConfig.NATURAL_VEHICLE_CHANCE.get();
        CommonConfig.ENABLE_VEHICLE_AI.set(true);
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(true);
        CommonConfig.NATURAL_VEHICLE_CHANCE.set(0.0D);
    }

    @AfterBatch(batch = "ground_hold_inputs")
    public static void restore(ServerLevel level) {
        List.copyOf(CLEANUP).forEach(Runnable::run);
        CLEANUP.clear();
        CommonConfig.ENABLE_VEHICLE_AI.set(previousEnabled);
        CommonConfig.VEHICLE_AI_NEEDS_AMMO.set(previousAmmo);
        CommonConfig.NATURAL_VEHICLE_CHANCE.set(previousNatural);
    }

    @GameTest(batch = "ground_hold_inputs", template = "empty", timeoutTicks = 450)
    public static void firingTankDoesNotIssueMovementOrSteering(GameTestHelper helper) {
        verify(helper, false);
    }

    @GameTest(batch = "ground_hold_inputs", template = "empty", timeoutTicks = 450)
    public static void firingPickupDoesNotIssueMovementOrSteering(GameTestHelper helper) {
        verify(helper, true);
    }

    private static void verify(GameTestHelper helper, boolean pickup) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new BlockPos(512 + ARENA.getAndIncrement() * 192, 5, 12672);
        Vec3 start = Vec3.atBottomCenterOf(origin);
        List<BlockPos> chunks = new ArrayList<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                BlockPos chunk = new BlockPos((origin.getX() >> 4) + x, 0, (origin.getZ() >> 4) + z);
                level.setChunkForced(chunk.getX(), chunk.getZ(), true);
                chunks.add(chunk);
            }
        }
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState(), 2);
            }
        }
        Entity vehicle = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(
                "superbwarfare", pickup ? "sodayo_pick_up_hmg" : "yx_100")).create(level);
        vehicle.moveTo(start.x, start.y + 0.05D, start.z, 0.0F, 0.0F);
        vehicle.setInvulnerable(true);
        List<Mob> crew = new ArrayList<>();
        crew.add(addCrew(level, EntityType.VINDICATOR, start));
        if (pickup) crew.add(addCrew(level, EntityType.PILLAGER, start));
        Mob target = EntityType.VILLAGER.create(level);
        target.moveTo(start.add(0.0D, 0.0D, 30.0D));
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                Integer.MAX_VALUE, 255, false, false));
        level.addFreshEntity(target);
        List<Entity> shots = new ArrayList<>();
        Consumer<EntityJoinLevelEvent> observer = event -> {
            if (event.getEntity() instanceof Projectile shot && crew.contains(shot.getOwner())) shots.add(shot);
        };
        MinecraftForge.EVENT_BUS.addListener(observer);
        Runnable close = () -> {
            MinecraftForge.EVENT_BUS.unregister(observer);
            shots.forEach(Entity::discard);
            crew.forEach(Entity::discard);
            target.discard();
            vehicle.discard();
            chunks.forEach(chunk -> level.setChunkForced(chunk.getX(), chunk.getZ(), false));
        };
        CLEANUP.add(close);
        long[] mountedAt = {-1};
        long[] observationAt = {-1};
        int[] initialShots = {0};
        helper.onEachTick(() -> {
            if (mountedAt[0] < 0) {
                helper.assertTrue(helper.getTick() < 120, "Invalid fixture: natural acquisition failed");
                if (crew.stream().anyMatch(member -> member.getTarget() != target)) return;
                target.moveTo(start.add(0.0D, pickup ? 0.0D : 5.0D, 18.0D));
                target.setNoGravity(true);
                target.setDeltaMovement(Vec3.ZERO);
                level.addFreshEntity(vehicle);
                SbwGameTestVehicle.prepareContinuousGroundFire(vehicle, false);
                VehicleController controller = VehicleControllers.create(vehicle).orElseThrow();
                for (Mob member : crew) {
                    helper.assertTrue(controller.mount(member), "Invalid fixture: crew failed to board");
                }
                if (pickup) {
                    var seat = controller.seats().stream()
                            .filter(value -> value.hasWeapon() && !value.occupied()).findFirst().orElseThrow();
                    helper.assertTrue(controller.moveToSeat(crew.get(1), seat), "Invalid fixture: no gunner seat");
                }
                mountedAt[0] = helper.getTick();
            }
            helper.assertTrue(vehicle.isAlive() && crew.get(0).getVehicle() == vehicle, "Lost driver or vehicle");
            helper.assertTrue(crew.get(0).getTarget() == target, "Lost naturally acquired target");
            if (observationAt[0] < 0) {
                helper.assertTrue(helper.getTick() - mountedAt[0] < 120, "Vehicle never established firing hold");
                if (shots.isEmpty()) return;
                observationAt[0] = helper.getTick() + 10;
                initialShots[0] = shots.size();
            }
            long elapsed = helper.getTick() - observationAt[0];
            if (elapsed < 0) return;
            var input = SbwGameTestVehicle.flightControl(vehicle);
            helper.assertTrue(!input.forward() && !input.back() && !input.left()
                            && !input.right() && !input.sprint(),
                    "Firing hold still submitted movement or steering: " + input);
            helper.assertTrue(vehicle.getDeltaMovement().horizontalDistance() <= 0.02D,
                    "Firing hold did not remain stopped");
            if (elapsed < 160) return;
            helper.assertTrue(shots.size() - initialShots[0] >= 2, "Stationary vehicle did not keep firing");
            close.run();
            CLEANUP.remove(close);
            helper.succeed();
        });
    }

    private static Mob addCrew(ServerLevel level, EntityType<? extends Mob> type, Vec3 position) {
        Mob member = type.create(level);
        member.moveTo(position);
        member.setPersistenceRequired();
        member.setInvulnerable(true);
        member.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64.0D);
        level.addFreshEntity(member);
        return member;
    }
}
