package com.dillon.starsectormarines.battle.derelict;

/**
 * The tasks a commander gives a flock (`derelict-drone-flocks.md`, Tasks). The security director only ever names a
 * task and an anchor; how the members fly it is the flock's.
 */
public enum FlockTask {
    /** Hold a station between a threat and what it covers. */
    SCREEN,
    /** Fly a route and report what it sees. */
    PROBE,
    /** Close, wrap and strafe a target. */
    STRIKE,
    /** Follow a contact out to the leash. */
    PURSUE,
    /** Go home to the cradle. */
    RETURN
}
