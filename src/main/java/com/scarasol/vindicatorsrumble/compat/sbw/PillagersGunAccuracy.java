package com.scarasol.vindicatorsrumble.compat.sbw;

import com.scarasol.vindicatorsrumble.VindicatorsRumbleMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class PillagersGunAccuracy {
    private static final MethodHandle MODIFY_INACCURACY = findAccuracyEvent();

    private PillagersGunAccuracy() {
    }

    public static double modifySpread(double spread, Mob shooter, Entity target) {
        if (MODIFY_INACCURACY == null) {
            return spread;
        }
        try {
            float modified = (float) MODIFY_INACCURACY.invokeExact(
                    (float) spread, shooter, target, target.getPosition(0.0F));
            // Keep SBW's double precision when the event leaves the spread unchanged.
            return modified == (float) spread || !Float.isFinite(modified) || modified < 0.0F
                    ? spread : modified;
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new IllegalStateException("Pillager's Gun accuracy event failed", exception);
        }
    }

    private static MethodHandle findAccuracyEvent() {
        try {
            Class<?> factory = Class.forName("com.scarasol.pillagers_gun.event.EventFactory",
                    false, PillagersGunAccuracy.class.getClassLoader());
            return MethodHandles.publicLookup().findStatic(factory, "getModifiedInaccuracy",
                    MethodType.methodType(float.class, float.class, Mob.class, Entity.class, Vec3.class));
        } catch (ReflectiveOperationException | LinkageError exception) {
            VindicatorsRumbleMod.LOGGER.warn(
                    "Pillager's Gun accuracy API is unavailable ({}); vehicle weapons keep their configured spread",
                    exception.toString());
            return null;
        }
    }
}
