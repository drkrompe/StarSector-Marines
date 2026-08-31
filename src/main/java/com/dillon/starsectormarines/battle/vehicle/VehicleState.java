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
 * → {@link #DEPARTING} (consuming the outbound queue) → {@link #GONE} (terminal;
 * the world entity is destroyed), or any visible live state → {@link #WRECKED}
 * when structure reaches zero. A wreck remains world-resident for rendering and
 * obstruction but is no longer targetable.
 *
 * <p>There is no phase between setting the payload down and leaving, armed or
 * not. A carrier that has unloaded turns for its outbound corridor — see
 * `convoy-nouns.md`.
 *
 * <p>{@link #DEPLOYED} is outside that flow entirely: a chassis on the field
 * with no errand, holding position until somebody tells it to go somewhere. It
 * is what a vehicle the player owns is doing between orders, and it is the
 * state that makes delivery one of the things a vehicle can be doing rather
 * than the whole of what a vehicle is.
 */
public enum VehicleState {
    PENDING, INCOMING, LANDED, DEPARTING, DEPLOYED, WRECKED, GONE
}
