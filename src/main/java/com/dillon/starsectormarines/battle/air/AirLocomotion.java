package com.dillon.starsectormarines.battle.air;

/**
 * How an aircraft is being moved right now.
 *
 * <p>One aircraft, one entity, three ways of moving it. What changes between a
 * machine parked in a shed, one rolling down a taxiway, one being flown onto a
 * threshold and one making a gun run is not <em>what it is</em> — it is which
 * locomotion model has the body. Saying that out loud is what lets the whole
 * sortie be one thing: the phases either side of a landing are no longer two
 * representations with a handoff in the middle, they are the same aircraft with
 * a different set of physics.
 *
 * <p><b>Derived from the phase, never stored.</b> A mode kept as a second field
 * is a second thing to keep in step, and the phase already says everything the
 * mode does — a craft rolling out is on its wheels because it is rolling out.
 * {@link #of(ShuttleState)} is total: every phase names exactly one mode, which
 * is why a phase that would need two of them is a phase that wants splitting
 * rather than a mode that wants a flag. That is the whole reason
 * {@link ShuttleState#PAD_DESCENT} exists as a phase of its own: a transport's
 * run in to a landing zone is flown, and the settle onto the pad at the end of
 * it is not.
 *
 * <p>See {@code air-nouns.md}.
 */
public enum AirLocomotion {

    /**
     * Nothing is moving the body: waiting off-map to launch, or done.
     */
    OFF_MAP,

    /**
     * On its wheels — {@link GroundDriveSystem}. Velocity is composed from the
     * heading and one speed, so there is no sideways component to bleed off;
     * the craft goes where it is pointed, steers through the arc its
     * undercarriage sets, and can stop.
     */
    GROUNDED,

    /**
     * On a solved trajectory — a curvature-feasible path the craft is being
     * flown along, or a descent onto a point.
     *
     * <p>The mode the transitions live in, and the one that used to be a
     * teleport. Reaching a threshold and reaching it <em>pointed down the
     * strip</em> are different things, and nothing in free flight makes the
     * second happen: a body steered at a point arrives on whatever heading it
     * was on. So the arrival is solved as geometry first and then flown, and
     * the aircraft is straight when it gets there because the path it flew
     * ended that way.
     */
    MANAGED,

    /**
     * Flying — {@link AirSteeringSystem}. Thrust along the nose, momentum
     * carried through the turn, sideways velocity damped rather than absent.
     */
    FREE_FLIGHT;

    /**
     * Whether a craft moving this way is in the air, and therefore something an
     * anti-air post can reach.
     */
    public boolean airborne() {
        return this == MANAGED || this == FREE_FLIGHT;
    }

    /** The one mode {@code state} is moved in. Total over {@link ShuttleState}. */
    public static AirLocomotion of(ShuttleState state) {
        switch (state) {
            case PENDING:
            case GONE:
                return OFF_MAP;
            case LOADING:
            case TAXI_OUT:
            case HOLDING_SHORT:
            case TAKEOFF_ROLL:
            case LANDED:
            case LANDING_ROLL:
            case TAXI_IN:
                return GROUNDED;
            case RETURNING:
            case PAD_DESCENT:
                return MANAGED;
            case INCOMING:
            case HOVER_STATION:
            case ATTACK_RUN:
            case REPOSITION:
            case DEPARTING:
                return FREE_FLIGHT;
            default:
                throw new IllegalArgumentException("no locomotion for " + state);
        }
    }
}
