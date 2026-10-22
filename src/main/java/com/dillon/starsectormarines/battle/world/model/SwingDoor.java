package com.dillon.starsectormarines.battle.world.model;

/**
 * A hinged door in a one-cell doorway, and how far it stands open.
 *
 * <p><b>Nothing stores how open a swinging door is.</b> The angle is read off
 * where people are standing: anyone in line with the doorway pushes it open
 * as they come within reach and lets it swing shut as they leave, so a door
 * eases open in step with the walker rather than on a timer. That keeps it
 * pure presentation: no state to save or advance, the same picture from any
 * host that draws the same moment, and nothing in the battle waits on it.
 *
 * <p>The door swings into the building, away from the street, and hangs on
 * whichever jamb the cell's hash picks, so a row of doors does not all hinge
 * on the same side.
 */
public final class SwingDoor {

    /** At or nearer than this, across the doorway, a body holds the door fully open. */
    public static final float FULL_CELLS = 0.6f;
    /** At or beyond this, across the doorway, a body leaves the door shut. */
    public static final float REACH_CELLS = 1.7f;
    /** How far past either jamb, along the wall, a body still counts as in line with the door. */
    static final float SLACK_CELLS = 0.35f;

    private SwingDoor() { }

    /**
     * How open a door at cell {@code (x, y)} is pushed by a body at
     * {@code (bx, by)}: 0 shut, 1 square open, eased between.
     *
     * @param spanX whether the doorway's jambs lie either side along x, so the
     *     shut leaf spans x and people pass through along y
     */
    public static float pushedBy(int x, int y, boolean spanX, float bx, float by) {
        float along = spanX ? bx - (x + 0.5f) : by - (y + 0.5f);
        float off = Math.abs(spanX ? by - (y + 0.5f) : bx - (x + 0.5f));
        if (Math.abs(along) > 0.5f + SLACK_CELLS || off >= REACH_CELLS) return 0f;
        float t = Math.min(1f, (REACH_CELLS - off) / (REACH_CELLS - FULL_CELLS));
        return t * t * (3f - 2f * t);
    }

    /** Whether the hinge stands on the low jamb (left or bottom) rather than the high one. */
    public static boolean hingeLow(int x, int y) {
        int hash = x * 73856093 ^ y * 19349663;
        hash ^= hash >>> 13;
        return (hash & 1) == 0;
    }
}
