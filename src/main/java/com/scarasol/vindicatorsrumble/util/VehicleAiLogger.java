package com.scarasol.vindicatorsrumble.util;

import com.scarasol.vindicatorsrumble.VindicatorsRumbleMod;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import net.minecraft.world.entity.Entity;

public class VehicleAiLogger {
    private static final String PREFIX = "[VehicleAI] ";

    private VehicleAiLogger() {
    }

    public static boolean isDebugEnabled() {
        try {
            return CommonConfig.VEHICLE_AI_DEBUG.get();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static void debug(String message, Object... args) {
        if (isDebugEnabled()) {
            VindicatorsRumbleMod.LOGGER.info(PREFIX + message, args);
        }
    }

    public static void debug(Entity entity, String message, Object... args) {
        if (entity == null) {
            debug(message, args);
            return;
        }
        if (!isDebugEnabled()) {
            return;
        }

        Object[] prefixedArgs = new Object[args.length + 2];
        prefixedArgs[0] = entity.getType().getDescriptionId();
        prefixedArgs[1] = entity.getId();
        System.arraycopy(args, 0, prefixedArgs, 2, args.length);
        VindicatorsRumbleMod.LOGGER.info(PREFIX + "[{}#{}] " + message, prefixedArgs);
    }

    public static void debugEvery(Entity entity, int intervalTicks, String message, Object... args) {
        if (!isDebugEnabled()) {
            return;
        }
        if (entity != null && intervalTicks > 1 && entity.tickCount % intervalTicks != 0) {
            return;
        }
        debug(entity, message, args);
    }
}
