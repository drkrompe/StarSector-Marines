package com.dillon.starsectormarines.battle.drone;

/**
 * A point a drone squad screens that is not a hub: a carrier that moves.
 *
 * <p>A hub squad reads its anchor off the hub entity's position. A tender is not
 * an entity, it is a hull overhead, so it publishes its own position through
 * this seam and the same swarm action patrols, leashes and engages around it.
 * Implementations are read from the parallel unit dispatch, so they must return
 * values that are stable for the tick.
 *
 * <p>The optional methods below turn the anchor from a point into a carrier that
 * its drones fly out of and back into. Each defaults to the hub's behaviour, so a
 * plain anchor changes nothing: only an anchor that says it is an
 * {@linkplain #escort() escort} gets a loose formation instead of random patrol
 * waypoints, a tighter leash and a focus target.
 */
public interface DroneAnchor {

    /** Anchor x, in cells. */
    float anchorX();

    /** Anchor y, in cells. */
    float anchorY();

    /** Whether the anchor still exists; a squad with a dead anchor has nothing to screen. */
    boolean anchorAlive();

    /** The anchor's heading in sprite degrees (0 is +Y, counter-clockwise positive); the formation turns with it. */
    default float anchorHeadingDegrees() {
        return 0f;
    }

    /**
     * Whether the squad escorts this anchor: loiters in a loose formation round
     * it, engages what comes into its reach, and goes home when it must.
     */
    default boolean escort() {
        return false;
    }

    /** How far from the anchor an escort will stray, in cells. */
    default float leashCells() {
        return Drone.ENGAGE_LEASH_RADIUS_CELLS;
    }

    /** Whether a hostile is inside the anchor's reach, which draws the formation in. */
    default boolean threatened() {
        return false;
    }

    /** The hostile nearest the anchor inside its reach, or {@code 0L}; escorts prefer it to what is nearest themselves. */
    default long focusTarget() {
        return 0L;
    }

    /** Ids of the drones in the formation, in slot order; a member's slot is its index here. */
    default long[] escortIds() {
        return NO_IDS;
    }

    /**
     * Where each drone of {@link #escortIds()} was when the carrier last published its roster,
     * as {@code x0, y0, x1, y1, ...}. A snapshot taken in the serial part of the tick, so every
     * drone reads the same positions for its neighbours whichever worker runs it first.
     */
    default float[] escortPositions() {
        return NO_POSITIONS;
    }

    /**
     * Where the drone must fly instead of going about its business, as
     * {@code {x, y}}: out to the exit point after launch, or home on recall or
     * retreat. {@code null} when the drone is on task.
     */
    default float[] sortieGoal(long member) {
        return null;
    }

    long[] NO_IDS = new long[0];

    float[] NO_POSITIONS = new float[0];
}
