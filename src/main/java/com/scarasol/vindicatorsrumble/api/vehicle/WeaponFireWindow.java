package com.scarasol.vindicatorsrumble.api.vehicle;

public record WeaponFireWindow(
        WeaponFireWindowState state,
        double yawErrorDegrees,
        double pitchErrorDegrees,
        double estimatedMiss
) {
    public static WeaponFireWindow unknown() {
        return new WeaponFireWindow(WeaponFireWindowState.UNKNOWN, 0.0D, 0.0D, 0.0D);
    }

    public static WeaponFireWindow readyNow(double yawErrorDegrees, double pitchErrorDegrees, double estimatedMiss) {
        return new WeaponFireWindow(WeaponFireWindowState.READY_NOW, yawErrorDegrees, pitchErrorDegrees, estimatedMiss);
    }

    public static WeaponFireWindow inWindow(double yawErrorDegrees, double pitchErrorDegrees, double estimatedMiss) {
        return new WeaponFireWindow(WeaponFireWindowState.IN_WINDOW, yawErrorDegrees, pitchErrorDegrees, estimatedMiss);
    }

    public static WeaponFireWindow needsPosture(double yawErrorDegrees, double pitchErrorDegrees, double estimatedMiss) {
        return new WeaponFireWindow(WeaponFireWindowState.NEEDS_POSTURE, yawErrorDegrees, pitchErrorDegrees, estimatedMiss);
    }

    public static WeaponFireWindow impossible(double yawErrorDegrees, double pitchErrorDegrees, double estimatedMiss) {
        return new WeaponFireWindow(WeaponFireWindowState.IMPOSSIBLE, yawErrorDegrees, pitchErrorDegrees, estimatedMiss);
    }

    public boolean hasCurrentWindow() {
        return this.state == WeaponFireWindowState.READY_NOW || this.state == WeaponFireWindowState.IN_WINDOW;
    }

    public boolean hasPotentialWindow() {
        return this.state != WeaponFireWindowState.IMPOSSIBLE;
    }

    public boolean isImpossible() {
        return this.state == WeaponFireWindowState.IMPOSSIBLE;
    }
}
