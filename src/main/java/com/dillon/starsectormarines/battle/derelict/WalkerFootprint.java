package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.appearance.DerelictLegRig;

/**
 * Where a standing derelict walker blocks the ground (`ruins-guardians.md`, Underneath): only where its feet plant.
 * Its torso rides high between four legs, so a body small enough ({@link #fitsUnder}) walks under it, between the
 * legs, and is kept off its feet alone; a downed walker drops its torso and blocks its whole body again. The feet are
 * placed as the leg rig rests them on open floor, on the hip bearings ({@link WalkerKnees}) at
 * {@link DerelictLegRig#REST_CELLS}: the battle's footprint, not the drawn foot, which may stand on a wall.
 */
public final class WalkerFootprint {

    /** A foot's blocking radius, cells. */
    public static final float FOOT_RADIUS = 0.35f;
    /** The largest body that fits under the torso, cells: a person, never a machine. */
    public static final float UNDER_RADIUS = 0.45f;
    /** A shooter this near the torso's centre stands under it, cells. */
    public static final float UNDER_CELLS = 1.2f;

    private WalkerFootprint() {}

    /** Whether a body of {@code radius} fits under a standing walker's torso. */
    public static boolean fitsUnder(float radius, boolean machine) {
        return !machine && radius <= UNDER_RADIUS;
    }

    /** Whether an offset {@code (dx, dy)} from the walker's centre is under its torso. */
    public static boolean under(float dx, float dy) {
        return dx * dx + dy * dy <= UNDER_CELLS * UNDER_CELLS;
    }

    /**
     * The four feet of a walker at {@code (x, y)} facing {@code facingDegrees}, as {@code out = {x0, y0, x1, y1, ...}}
     * in leg order; returns the count.
     */
    public static int feet(float x, float y, float facingDegrees, float[] out) {
        for (int i = 0; i < WalkerKnees.LEGS; i++) {
            double heading = Math.toRadians(facingDegrees + WalkerKnees.HIP_BEARING[i]);
            // The battle's facing convention: heading 0 points +y, the forward vector is (-sin, cos).
            out[2 * i] = x - (float) Math.sin(heading) * DerelictLegRig.REST_CELLS;
            out[2 * i + 1] = y + (float) Math.cos(heading) * DerelictLegRig.REST_CELLS;
        }
        return WalkerKnees.LEGS;
    }
}
