package com.scarasol.vindicatorsrumble.mixin.sbw;

import com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity;
import com.atsuishio.superbwarfare.world.phys.EntityResult;
import com.scarasol.vindicatorsrumble.util.AllegianceUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProjectileEntity.class)
public abstract class ProjectileFriendlyFireMixin {
    @Inject(
            method = "getHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lcom/atsuishio/superbwarfare/world/phys/EntityResult;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void vindicatorsRumble$skipFriendlyProjectileTargets(Entity target, Vec3 startVec, Vec3 endVec,
                                                                 CallbackInfoReturnable<EntityResult> cir) {
        Entity shooter = ((ProjectileEntity) (Object) this).getOwner();
        if (AllegianceUtil.shouldProjectileIgnoreTarget(target, shooter)) {
            cir.setReturnValue(null);
        }
    }
}
