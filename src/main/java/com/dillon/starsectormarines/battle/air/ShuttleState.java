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
 * → optional HOVER_STATION (armed fire-support loiter) → DEPARTING (steering to
 * exit) → GONE (terminal). With {@code totalCycles > 1} a shuttle re-enters
 * PENDING after DEPARTING and flies another sortie.
 *
 * <p>A top-level enum (formerly {@code Shuttle.State}) so it outlives the
 * dissolved {@code Shuttle} handle; see {@code air-nouns.md}.
 */
public enum ShuttleState {
    PENDING,
    /**
     * Down on its hardstand with its ramp open, taking aboard the squad walking
     * out to it. Only a sortie that flies from an authored airfield has this
     * phase: a shuttle entering from off-map is already loaded, because there
     * was nowhere on the map for it to load.
     */
    LOADING,
    INCOMING, LANDED, HOVER_STATION, DEPARTING, GONE
}
