package com.scarasol.vindicatorsrumble.api.vehicle;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public record DriveRequest(
        DriveIntent intent,
        Optional<Vec3> targetPosition,
        Optional<Entity> targetEntity,
        Optional<Path> path,
        double desiredDistance,
        boolean needsLineOfSight,
        boolean preferStableFire
) {
    public static final DriveRequest NONE = new DriveRequest(DriveIntent.NONE, Optional.empty(), Optional.empty(), Optional.empty(), 0.0D, false, false);

    public static DriveRequest chase(Entity target, double desiredDistance) {
        return new DriveRequest(DriveIntent.CHASE_TARGET, Optional.empty(), Optional.of(target), Optional.empty(), desiredDistance, true, false);
    }

    public static DriveRequest ram(Entity target) {
        return new DriveRequest(DriveIntent.RAM_TARGET, Optional.empty(), Optional.of(target), Optional.empty(), 0.0D, false, false);
    }

    public static DriveRequest supportFire(Entity target, double desiredDistance) {
        return new DriveRequest(DriveIntent.SUPPORT_FIRE, Optional.empty(), Optional.of(target), Optional.empty(), desiredDistance, true, true);
    }

    public static DriveRequest moveTo(Vec3 targetPosition) {
        return new DriveRequest(DriveIntent.MOVE_TO, Optional.of(targetPosition), Optional.empty(), Optional.empty(), 4.0D, false, false);
    }

    public static DriveRequest moveTo(Vec3 targetPosition, Path path) {
        return new DriveRequest(DriveIntent.MOVE_TO, Optional.of(targetPosition), Optional.empty(), Optional.ofNullable(path), 4.0D, false, false);
    }

    public static DriveRequest moveTo(Entity target, Path path) {
        return new DriveRequest(DriveIntent.MOVE_TO, Optional.empty(), Optional.of(target), Optional.ofNullable(path), 4.0D, false, false);
    }

    public DriveRequest withPreferStableFire(boolean preferStableFire) {
        if (this.preferStableFire == preferStableFire) {
            return this;
        }
        return new DriveRequest(this.intent, this.targetPosition, this.targetEntity, this.path,
                this.desiredDistance, this.needsLineOfSight, preferStableFire);
    }
}
