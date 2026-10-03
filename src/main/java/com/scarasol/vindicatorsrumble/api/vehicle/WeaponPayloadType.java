package com.scarasol.vindicatorsrumble.api.vehicle;

public enum WeaponPayloadType {
    BULLET,
    HITSCAN,
    SHELL,
    ROCKET,
    MISSILE,
    UTILITY,
    UNKNOWN;

    public boolean isSoftTargetPreferred() {
        return this == BULLET || this == HITSCAN;
    }

    public boolean isHeavyTargetPreferred() {
        return this == SHELL || this == ROCKET || this == MISSILE;
    }
}
