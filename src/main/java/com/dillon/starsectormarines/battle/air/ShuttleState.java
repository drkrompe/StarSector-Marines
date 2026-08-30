package com.dillon.starsectormarines.battle.air;

/**
 * The lifecycle phase of one air-craft sortie, held on its
 * {@link ShuttleMission} ({@code SHUTTLE_MISSION} component) and driven by
 * {@link AirSystem}'s state-machine tick. Air liveness is {@code mission.state},
 * not a {@code HEALTH} component — a transport carries no grid/combat components.
 *
 * <p>Lifecycle: PENDING (waiting on stagger / re-arm, off-map + engine-silent) →
 * optional LOADING (down on its own hardstand while its squad walks out to it) →
 * INCOMING (steering from the entry point to the LZ) → LANDED (deboarding marines)
 * → DEPARTING (steering to exit) → GONE (terminal). With
 * {@code totalCycles > 1} a shuttle re-enters PENDING after DEPARTING and flies
 * another sortie.
 *
 * <p>There is no phase between setting the payload down and leaving. A
 * transport that has unloaded turns for the exit, because the delivery is what
 * it was flown for and holding station over the drop point is a second job
 * nothing asked it to do — see `air-nouns.md`.
 *
 * <p>A craft based on a field with a strip has a ground procedure around that:
 * TAXI_OUT → HOLDING_SHORT → TAKEOFF_ROLL before INCOMING, and RETURNING →
 * LANDING_ROLL → TAXI_IN after it comes home. Those phases are on the ground — the aircraft is
 * a target for all of them — which is what a runway buys over a vertical lift
 * off a hardstand. See `air-nouns.md`.
 *
 * <p>A top-level enum (formerly {@code Shuttle.State}) so it outlives the
 * dissolved {@code Shuttle} handle; see {@code air-nouns.md}.
 */
public enum ShuttleState {
    PENDING,
    /**
     * Rolling from its shelter to the runway threshold, on the ground and at
     * taxi speed.
     *
     * <p>Only a craft that has to roll has this phase. A shuttle lifts off the
     * stand it was loaded on and never touches a strip; an aircraft based in a
     * shed has to be got out to one, and that crossing is a stretch of open
     * ground somebody can be standing on.
     */
    TAXI_OUT,
    /**
     * Stopped at the threshold with the strip in use by somebody else.
     *
     * <p>The queue a single runway creates. A field with three aircraft
     * launches them one at a time, so this is where the second and third wait
     * — in the open, at the one point on the base every departure has to pass
     * through.
     */
    HOLDING_SHORT,
    /**
     * Accelerating down the centreline. Still on the ground and still
     * shootable as one until it rotates at the far end.
     */
    TAKEOFF_ROLL,
    /**
     * Down on its hardstand with its ramp open, taking aboard the squad walking
     * out to it. Only a sortie that flies from an authored airfield has this
     * phase: a shuttle entering from off-map is already loaded, because there
     * was nowhere on the map for it to load.
     */
    LOADING,
    INCOMING, LANDED,
    /**
     * Flying a straight line through the target with the guns going.
     *
     * <p>Not a hover. An aircraft that station-keeps over a position is a
     * helicopter with the serial numbers filed off, and it wants turrets
     * because nothing about where it points has anything to do with where it
     * shoots. A machine that runs in, fires along its own nose and overshoots
     * is aimed by being flown, which is what makes the airframe the weapon.
     */
    ATTACK_RUN,
    /**
     * Coming round for another pass: out, wide, and back onto a fresh bearing.
     *
     * <p>Its own phase because the turn is most of the time an attack takes
     * and none of the damage. A fighter cannot pivot on the spot at the end of
     * a run — it is carrying its speed through the turn — so the circuit is
     * wide, and the wait between passes is the window the target has to get
     * out of the open.
     */
    REPOSITION,
    DEPARTING,
    /**
     * Flying home to its own field, and lined up on the strip.
     *
     * <p>Not {@link #DEPARTING} with a different destination. Everything a
     * phase decides is the other way round here: it steers to a runway
     * threshold rather than to an off-map exit, it descends rather than
     * climbing away, and it ends by taking the strip rather than by ceasing to
     * exist. A craft leaving the battle and a craft coming home are opposite
     * things that happen to both involve flying away from an objective.
     */
    RETURNING,
    /**
     * Down on the strip and slowing to taxi speed after a landing.
     *
     * <p>The craft holds the runway through this, because it is on it. A
     * landing aircraft and a departing one cannot both have the strip.
     */
    LANDING_ROLL,
    /** Rolling from where it left the strip back to its own shelter. */
    TAXI_IN,
    GONE
}
