package com.scarasol.vindicatorsrumble.gametest.profile.mixin;

import com.scarasol.vindicatorsrumble.api.vehicle.VehicleController;
import com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive.helicopter.HelicopterFlightPlanner;
import com.scarasol.vindicatorsrumble.gametest.profile.HelicopterPlanTrace;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiFunction;
import java.util.function.Predicate;

@Mixin(value = HelicopterFlightPlanner.class, remap = false)
public abstract class HelicopterPlanTraceMixin {
    @Inject(method = "resolvePlan", at = @At("RETURN"), remap = false)
    private void recordPlan(VehicleController controller, Entity target, @Coerce Object state,
                            double hoverHeight, double followRange, Predicate<?> recoveryBlocked,
                            BiFunction<?, ?, ?> selector, CallbackInfoReturnable<?> callback) {
        HelicopterPlanTrace.record(controller.entity().getId(), state, callback.getReturnValue());
    }
}
