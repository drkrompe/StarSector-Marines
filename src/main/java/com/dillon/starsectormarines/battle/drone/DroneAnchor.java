package com.dillon.starsectormarines.battle.drone;

/**
 * A point a drone squad screens that is not a hub: a carrier that moves.
 *
 * <p>A hub squad reads its anchor off the hub entity's position. A tender is not
 * an entity, it is a hull overhead, so it publishes its own position through
 * this seam and the same swarm action patrols, leashes and engages around it.
 * Implementations are read from the parallel unit dispatch, so they must return
 * values that are stable for the tick.
 */
public interface DroneAnchor {

    /** Anchor x, in cells. */
    float anchorX();

    /** Anchor y, in cells. */
    float anchorY();

    /** Whether the anchor still exists; a squad with a dead anchor has nothing to screen. */
    boolean anchorAlive();
}
