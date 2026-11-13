package com.scarasol.vindicatorsrumble.util;

import com.scarasol.vindicatorsrumble.VindicatorsRumbleMod;
import com.scarasol.vindicatorsrumble.config.CommonConfig;
import net.minecraft.world.entity.Entity;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class VehicleAiLogger {
    private static final String PREFIX = "[VehicleAI] ";
    private static final int MAX_SAMPLED_MESSAGES_PER_TICK = 64;
    private static final Map<Entity, SampledMessages> SAMPLED_MESSAGES = new WeakHashMap<>();

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
        if (!shouldLog(entity, intervalTicks)) {
            return;
        }
        if (entity == null) {
            debug(message, args);
            return;
        }
        // Cache rendered text only: weak entity keys must not be retained through log arguments.
        FormattingTuple formatted = MessageFormatter.arrayFormat(message, args);
        if (formatted.getThrowable() == null) {
            synchronized (SAMPLED_MESSAGES) {
                SampledMessages sampled = SAMPLED_MESSAGES.get(entity);
                if (sampled == null || sampled.tick() != entity.tickCount) {
                    sampled = new SampledMessages(entity.tickCount, new HashSet<>());
                    SAMPLED_MESSAGES.put(entity, sampled);
                }
                if (sampled.messages().contains(formatted.getMessage())) {
                    return;
                }
                if (sampled.messages().size() < MAX_SAMPLED_MESSAGES_PER_TICK) {
                    sampled.messages().add(formatted.getMessage());
                }
            }
        }
        debug(entity, message, args);
    }

    public static boolean shouldLog(Entity entity, int intervalTicks) {
        return isDebugEnabled()
                && (entity == null || intervalTicks <= 1
                || Math.floorMod((long) entity.tickCount + entity.getId(), intervalTicks) == 0);
    }

    private record SampledMessages(int tick, Set<String> messages) {
    }
}
