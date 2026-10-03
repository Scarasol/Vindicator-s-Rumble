package com.scarasol.vindicatorsrumble.mixin.sbw;

import com.atsuishio.superbwarfare.item.gun.GunItem;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.scarasol.vindicatorsrumble.util.AllegianceUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

@Mixin(GunItem.class)
public abstract class GunRayFriendlyFireMixin {
    @WrapOperation(
            method = "shootRay",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
                    remap = true
            ),
            remap = false
    )
    private EntityHitResult vindicatorsRumble$skipFriendlyRayTargets(Entity shooter, Vec3 startVec, Vec3 endVec,
                                                                     AABB boundingBox, Predicate<Entity> filter,
                                                                     double distance,
                                                                     Operation<EntityHitResult> original) {
        Predicate<Entity> filtered = filter;
        if (shooter != null) {
            filtered = filter.and(entity -> !AllegianceUtil.shouldProjectileIgnoreTarget(entity, shooter));
        }
        return original.call(shooter, startVec, endVec, boundingBox, filtered, distance);
    }
}
