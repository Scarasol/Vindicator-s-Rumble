package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.helicopter;

record HelicopterEnvironment(boolean hasLineOfSight,
                             boolean frontBlocked,
                             boolean elevatedFrontClear,
                             int sidePreference,
                             double terrainPreferredY,
                             double frontClearance,
                             double elevatedFrontClearance) {
    static final HelicopterEnvironment NONE = new HelicopterEnvironment(
            true,
            false,
            true,
            0,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.POSITIVE_INFINITY);

    HelicopterEnvironment {
        sidePreference = Integer.compare(sidePreference, 0);
    }

    boolean hasTerrainPreferredY() {
        return !Double.isNaN(this.terrainPreferredY);
    }
}
