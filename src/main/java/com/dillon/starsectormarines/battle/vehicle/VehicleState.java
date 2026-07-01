package com.dillon.starsectormarines.battle.vehicle;

/**
 * Convoy-vehicle lifecycle state machine (the ground twin of
 * {@link com.dillon.starsectormarines.battle.air.ShuttleState}). Promoted from a
 * {@code Vehicle.State} nested enum to top-level so it outlives the {@code Vehicle}
 * handle as its state migrates into the world's {@code VEHICLE_MISSION} component
 * (convoy-{@code Vehicle}-into-world epic,
 * {@code roadmap/ecs-migration/stories/vehicle-into-world.md}).
 *
 * <p>Flow: {@link #PENDING} (off-map, waiting on the spawn stagger) → {@link #INCOMING}
 * (consuming the inbound waypoint queue) → {@link #LANDED} (deboarding militia at the LZ)
 * → {@link #OVERWATCH} (armed vehicles hold + fire) → {@link #DEPARTING} (consuming the
 * outbound queue) → {@link #GONE} (terminal; the world entity is destroyed). Unarmed
 * trucks skip OVERWATCH and depart straight after deboard.
 */
public enum VehicleState {
    PENDING, INCOMING, LANDED, OVERWATCH, DEPARTING, GONE
}
