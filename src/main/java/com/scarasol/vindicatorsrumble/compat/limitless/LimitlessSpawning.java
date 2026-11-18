package com.scarasol.vindicatorsrumble.compat.limitless;

import com.scarasol.vindicatorsrumble.compat.VehicleSpawnCandidates.Candidate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import org.ywzj.vehicle.api.YwzjVehicleAPI;
import org.ywzj.vehicle.api.event.VehicleMoveEvent;
import org.ywzj.vehicle.custom.vehicle.BaseVehicleData;
import org.ywzj.vehicle.custom.vehicle.TrackedVehicleData;
import org.ywzj.vehicle.custom.vehicle.WheeledVehicleData;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

public final class LimitlessSpawning {
    private static final Map<Entity, Runnable> BOARD_AFTER_FIRST_TICK = new IdentityHashMap<>();

    static {
        MinecraftForge.EVENT_BUS.addListener(LimitlessSpawning::afterMovement);
        MinecraftForge.EVENT_BUS.addListener(LimitlessSpawning::onVehicleLeaveLevel);
    }

    private LimitlessSpawning() {
    }

    public static Optional<Candidate> resolve(ResourceLocation id) {
        return YwzjVehicleAPI.getVehicleDataManager().getVehicleData(id)
                .filter(data -> !data.isUav() && (data instanceof WheeledVehicleData || data instanceof TrackedVehicleData))
                .map(data -> new Candidate(id, (level, origin) -> construct(data, level, origin)));
    }

    private static Entity construct(BaseVehicleData<?> data, ServerLevel level, Vec3 origin) {
        AbstractVehicle vehicle = data.construct(level, origin, 0.0F, 0.0F);
        vehicle.initData();
        vehicle.setBoundingBox(vehicle.makeBoundingBox());
        return vehicle;
    }

    public static boolean deferBoarding(Entity vehicle, Runnable board) {
        if (vehicle instanceof AbstractVehicle && vehicle.tickCount == 0) {
            // LV ejects existing passengers before posting its first native movement event.
            BOARD_AFTER_FIRST_TICK.put(vehicle, board);
            return true;
        }
        return false;
    }

    private static void afterMovement(VehicleMoveEvent event) {
        Runnable board = BOARD_AFTER_FIRST_TICK.remove(event.getVehicle());
        if (board != null) {
            board.run();
        }
    }

    private static void onVehicleLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof AbstractVehicle) {
            BOARD_AFTER_FIRST_TICK.remove(event.getEntity());
        }
    }
}
