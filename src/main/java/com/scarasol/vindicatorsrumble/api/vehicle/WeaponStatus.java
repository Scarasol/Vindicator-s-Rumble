package com.scarasol.vindicatorsrumble.api.vehicle;

public record WeaponStatus(
        boolean selected,
        boolean canFire,
        boolean needsReload,
        boolean reloading,
        boolean coolingDown,
        boolean hasAmmo,
        int heat
) {
    public static final WeaponStatus READY = new WeaponStatus(true, true, false, false, false, true, 0);
}
