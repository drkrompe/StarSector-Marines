package com.dillon.starsectormarines.battle.air;

/**
 * The lifecycle phase of one air-craft sortie, held on its
 * {@link ShuttleMission} ({@code SHUTTLE_MISSION} component) and driven by
 * {@link AirSystem}'s state-machine tick. Air liveness is {@code mission.state},
 * not a {@code HEALTH} component — a transport carries no grid/combat components.
 *
 * <p>Lifecycle: PENDING (waiting on stagger / re-arm, off-map + engine-silent) →
 * INCOMING (steering from off-map entry to the LZ) → LANDED (deboarding marines)
 * → optional HOVER_STATION (armed fire-support loiter) → DEPARTING (steering to
 * exit) → GONE (terminal). With {@code totalCycles > 1} a shuttle re-enters
 * PENDING after DEPARTING and flies another sortie.
 *
 * <p>A top-level enum (formerly {@code Shuttle.State}) so it outlives the
 * dissolving {@code Shuttle} handle — see
 * {@code roadmap/air/air-entities-into-world.md}.
 */
public enum ShuttleState {
    PENDING, INCOMING, LANDED, HOVER_STATION, DEPARTING, GONE
}
