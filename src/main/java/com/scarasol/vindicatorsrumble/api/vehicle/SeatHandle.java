package com.scarasol.vindicatorsrumble.api.vehicle;

import net.minecraft.world.entity.Entity;

import java.util.Optional;

public record SeatHandle(
        int id,
        boolean canDrive,
        boolean hasWeapon,
        Optional<Entity> occupant
) {
    public boolean occupied() {
        return this.occupant.isPresent();
    }
}
