package com.scarasol.vindicatorsrumble.compat.sbw;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class SbwGameTestVehicle {
    private SbwGameTestVehicle() {
    }

    public static void prepareUnarmedAirborne(Entity entity) {
        if (!(entity instanceof VehicleEntity vehicle)) {
            throw new IllegalArgumentException("Not an SBW vehicle: " + entity.getType());
        }
        for (int slot = 0; slot < vehicle.getContainerSize(); slot++) {
            vehicle.setItem(slot, ItemStack.EMPTY);
        }
        for (String weapon : List.copyOf(vehicle.getGunDataMap().keySet())) {
            vehicle.modifyGunData(weapon, data -> {
                data.ammo.set(0);
                data.virtualAmmo.set(0);
                data.backupAmmoCount.set(0);
            });
        }
        vehicle.setEngineStart(true);
        vehicle.setEngineStartOver(true);
        vehicle.setPower(0.12F);
        vehicle.setEnergy(vehicle.getMaxEnergy());
        vehicle.setHoverMode(false);
        vehicle.setRoll(0.0F);
        vehicle.setRollAngle(0.0F);
        vehicle.setRollVelocity(0.0F);
        vehicle.setPitchAngle(0.0F);
        vehicle.setPitchVelocity(0.0F);
    }

    public static FlightControl flightControl(Entity entity) {
        if (!(entity instanceof VehicleEntity vehicle)) {
            throw new IllegalArgumentException("Not an SBW vehicle: " + entity.getType());
        }
        return new FlightControl(
                vehicle.getMouseMoveSpeedX(),
                vehicle.getMouseMoveSpeedY(),
                vehicle.getDeltaRot(),
                vehicle.getSynchedPropellerRot(),
                vehicle.forwardInputDown(),
                vehicle.backInputDown(),
                vehicle.leftInputDown(),
                vehicle.rightInputDown(),
                vehicle.upInputDown(),
                vehicle.downInputDown(),
                vehicle.sprintInputDown());
    }

    public static boolean movePassengerToSeat(Entity entity, Entity passenger, int seat) {
        if (!(entity instanceof VehicleEntity vehicle)) {
            throw new IllegalArgumentException("Not an SBW vehicle: " + entity.getType());
        }
        return vehicle.changeSeat(passenger, seat);
    }

    public record FlightControl(float mouseX, float mouseY, float deltaRot, float rotor,
                                boolean forward, boolean back, boolean left, boolean right,
                                boolean up, boolean down, boolean sprint) {
    }
}
