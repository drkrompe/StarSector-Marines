package com.dillon.starsectormarines.battle.vehicle;

/**
 * Convoy-vehicle lifecycle state machine (the ground twin of
 * {@link com.dillon.starsectormarines.battle.air.ShuttleState}). Promoted from a
 * {@code Vehicle.State} nested enum to top-level so it outlives the {@code Vehicle}
 * handle as its state migrates into the world's {@code VEHICLE_MISSION} component
 * (convoy-{@code Vehicle}-into-world epic,
 * {@code ecs-nouns.md}).
 *
 * <p>Flow: {@link #PENDING} (off-map, waiting on the spawn stagger) → {@link #INCOMING}
 * (consuming the inbound waypoint queue) → {@link #LANDED} (deboarding militia at the LZ)
 * → {@link #OVERWATCH} (armed vehicles hold + fire) → {@link #DEPARTING} (consuming the
 * outbound queue) → {@link #GONE} (terminal; the world entity is destroyed), or
 * any visible live state → {@link #WRECKED} when structure reaches zero. A wreck
 * remains world-resident for rendering and obstruction but is no longer targetable.
 * Unarmed
 * trucks skip OVERWATCH and depart straight after deboard.
 */
public enum VehicleState {
    PENDING, INCOMING, LANDED, OVERWATCH, DEPARTING, WRECKED, GONE
}
