package com.dillon.starsectormarines.battle.drone;

/**
 * The one task a commander gives a {@link Flock}. The commander picks the task and its point or target; the flock
 * moves its anchor for it and its members fly the anchor themselves.
 */
public enum FlockTask {
    /** Hold a station: the anchor sits on a point between a threat and what it covers. */
    SCREEN,
    /** Fly a route and report what the members see: the anchor walks the route's points in turn and holds the last. */
    PROBE,
    /** Close, wrap and strafe a target: the anchor sits on it and the members take orbit slots round it. */
    STRIKE,
    /** Follow a contact: the anchor tracks it, never past the flock's leash from home. */
    PURSUE,
    /** Go home: the anchor goes back to the flock's home point and holds there. */
    RETURN
}
