package com.dillon.starsectormarines.battle.vehicle;

/**
 * Immutable (x, y, facingDeg) tuple — a vehicle pose in world coordinates,
 * facing convention matching {@link BicycleBody} (0° = +Y, positive CCW).
 *
 * <p>Used by the planning stack ({@link ReedsShepp}, future Hybrid A*) as the
 * argument and return type for "drive from start pose to goal pose"
 * queries. Separate from {@link GroundBody} which carries mutable kinematic
 * state alongside pose.
 */
public final class Pose {
    public final float x;
    public final float y;
    public final float facingDeg;

    public Pose(float x, float y, float facingDeg) {
        this.x = x;
        this.y = y;
        this.facingDeg = facingDeg;
    }

    @Override
    public String toString() {
        return "Pose(" + x + ", " + y + ", " + facingDeg + "°)";
    }
}
