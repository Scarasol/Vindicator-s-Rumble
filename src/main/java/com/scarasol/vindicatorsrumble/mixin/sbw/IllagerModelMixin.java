package com.scarasol.vindicatorsrumble.mixin.sbw;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.AbstractIllager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IllagerModel.class)
public abstract class IllagerModelMixin {
    @Shadow
    @Final
    private ModelPart head;

    @Shadow
    @Final
    private ModelPart hat;

    @Shadow
    @Final
    private ModelPart arms;

    @Shadow
    @Final
    private ModelPart leftLeg;

    @Shadow
    @Final
    private ModelPart rightLeg;

    @Shadow
    @Final
    private ModelPart rightArm;

    @Shadow
    @Final
    private ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/monster/AbstractIllager;FFFFF)V", at = @At("TAIL"))
    private void vindicatorsRumble$applySbwVehicleSeatPose(AbstractIllager entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity.getVehicle() instanceof VehicleEntity vehicle)) {
            return;
        }

        int index = vehicle.getSeatIndex(entity);
        var seats = vehicle.computed().seats();
        if (index < 0 || index >= seats.size()) {
            return;
        }

        String pose = seats.get(index).pose;
        switch (pose) {
            case "Pilot" -> this.applyPilotPose();
            case "Tow" -> this.applyTowPose();
            case "Climb" -> this.applyClimbPose();
            case "Stand" -> this.applyStandPose();
            case "MachineGunStand" -> this.applyMachineGunStandPose();
            default -> {
            }
        }
    }

    private void showSeparateArms() {
        this.arms.visible = false;
        this.leftArm.visible = true;
        this.rightArm.visible = true;
    }

    private void resetHead() {
        this.head.xRot = 0.0F;
        this.head.yRot = 0.0F;
        this.head.zRot = 0.0F;
        this.hat.xRot = 0.0F;
        this.hat.yRot = 0.0F;
        this.hat.zRot = 0.0F;
    }

    private void resetLegs() {
        this.leftLeg.xRot = 0.0F;
        this.leftLeg.yRot = 0.0F;
        this.leftLeg.zRot = 0.0F;
        this.rightLeg.xRot = 0.0F;
        this.rightLeg.yRot = 0.0F;
        this.rightLeg.zRot = 0.0F;
    }

    private void applyPilotPose() {
        this.showSeparateArms();
        this.resetHead();

        this.rightArm.xRot = -55.0F * Mth.DEG_TO_RAD;
        this.rightArm.yRot = -15.0F * Mth.DEG_TO_RAD;
        this.rightArm.zRot = -30.0F * Mth.DEG_TO_RAD;
    }

    private void applyTowPose() {
        this.showSeparateArms();
        this.head.xRot = 0.0F;
        this.hat.xRot = 0.0F;

        this.leftArm.yRot = 45.0F * Mth.DEG_TO_RAD;
        this.leftArm.xRot = -115.0F * Mth.DEG_TO_RAD;
        this.leftArm.zRot = 0.0F;

        this.rightArm.yRot = 25.0F * Mth.DEG_TO_RAD;
        this.rightArm.xRot = -115.0F * Mth.DEG_TO_RAD;
        this.rightArm.zRot = 0.0F;
    }

    private void applyClimbPose() {
        this.showSeparateArms();
        this.leftArm.xRot = -112.5F * Mth.DEG_TO_RAD;
        this.rightArm.xRot = -112.5F * Mth.DEG_TO_RAD;
    }

    private void applyStandPose() {
        this.resetLegs();
    }

    private void applyMachineGunStandPose() {
        this.showSeparateArms();
        this.leftArm.xRot = -90.0F * Mth.DEG_TO_RAD;
        this.leftArm.yRot = 0.0F;
        this.leftArm.zRot = 0.0F;

        this.rightArm.xRot = -90.0F * Mth.DEG_TO_RAD;
        this.rightArm.yRot = 0.0F;
        this.rightArm.zRot = 0.0F;

        this.resetLegs();
    }
}
