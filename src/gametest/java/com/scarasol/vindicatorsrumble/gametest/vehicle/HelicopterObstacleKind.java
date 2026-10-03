package com.scarasol.vindicatorsrumble.gametest.vehicle;

enum HelicopterObstacleKind {
    WIDE_WALL(true),
    TALL_TOWER(false),
    WIDE_BUILDING(false),
    DEEP_BUILDING(false);

    private final boolean solid;

    HelicopterObstacleKind(boolean solid) {
        this.solid = solid;
    }

    boolean solid() {
        return this.solid;
    }
}
