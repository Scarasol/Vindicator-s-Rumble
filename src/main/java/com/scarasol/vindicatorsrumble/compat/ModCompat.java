package com.scarasol.vindicatorsrumble.compat;

import net.minecraftforge.fml.ModList;

/**
 * @author Scarasol
 */
public class ModCompat {
    public static final String SUPERB_WARFARE = "superbwarfare";
    public static final String GUARD_VILLAGERS = "guardvillagers";
    public static final String RECRUITS = "recruits";

    public static boolean isSbwLoaded() {
        return ModList.get().isLoaded(SUPERB_WARFARE);
    }

    public static boolean isGuardVillagersLoaded() {
        return ModList.get().isLoaded(GUARD_VILLAGERS);
    }

    public static boolean isRecruitsLoaded() {
        return ModList.get().isLoaded(RECRUITS);
    }
}
