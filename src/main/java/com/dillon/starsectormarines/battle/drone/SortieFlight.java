package com.dillon.starsectormarines.battle.drone;

/**
 * The geometry of a drone leaving a carrier and coming home to it.
 *
 * <p>A drone does not appear on its task or vanish at the bay. It leaves through
 * the bay's mouth, flies out to an exit point a few cells beyond it, and only then
 * goes about its business; coming home it flies to an approach point just outside
 * the mouth first and then in, so the last stretch of every return is the same
 * short, deliberate line whatever side it came from. Both points are measured
 * from the bay as it is now, so a moving ship carries them with it.
 *
 * <p>Pure arithmetic: no clock, no state, nothing random. Bearings use the sprite
 * convention of the air layer: 0 is +Y, positive is counter-clockwise, and a
 * bearing {@code a} points along {@code (-sin a, cos a)}.
 */
public final class SortieFlight {

    /** How far beyond the bay's mouth a launching drone flies before it is on its task, in cells. */
    public static final float EXIT_CELLS = 5f;
    /** Half the width of the fan launching drones spread across, in degrees either side of the mouth. */
    public static final float FAN_DEGREES = 28f;
    /** How far outside the mouth the line home begins, in cells. */
    public static final float APPROACH_CELLS = 4.5f;
    /** A drone this close to its exit or approach point has reached it, in cells. */
    public static final float REACHED_CELLS = 1.5f;
    /** A drone this close to the bay is inside it, in cells. */
    public static final float DOCK_CELLS = 1.2f;

    private SortieFlight() {}

    /** The bearing a drone with phase {@code phase} (in [0, 1)) leaves on: the mouth's, fanned by up to {@link #FAN_DEGREES}. */
    public static float exitBearing(float mouthDeg, float phase) {
        return mouthDeg + (phase * 2f - 1f) * FAN_DEGREES;
    }

    /** The point a launching drone flies to before it goes about its task. */
    public static float[] exitPoint(float bayX, float bayY, float mouthDeg, float phase) {
        return along(bayX, bayY, exitBearing(mouthDeg, phase), EXIT_CELLS);
    }

    /** The point a returning drone flies to before it turns in to the bay. */
    public static float[] approachPoint(float bayX, float bayY, float mouthDeg) {
        return along(bayX, bayY, mouthDeg, APPROACH_CELLS);
    }

    /**
     * Seconds after the first at which the {@code index}th drone of a wave leaves
     * or is released, {@code gap} seconds apart. A wave is a line, not a burst.
     */
    public static float stagger(int index, float gap) {
        return Math.max(0, index) * gap;
    }

    /** The unit vector a bearing points along. */
    public static float[] direction(float bearingDeg) {
        double r = Math.toRadians(bearingDeg);
        return new float[]{(float) -Math.sin(r), (float) Math.cos(r)};
    }

    private static float[] along(float x, float y, float bearingDeg, float cells) {
        float[] d = direction(bearingDeg);
        return new float[]{x + d[0] * cells, y + d[1] * cells};
    }
}
