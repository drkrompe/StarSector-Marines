package com.dillon.starsectormarines.battle.vehicle;

/**
 * What a vehicle is driving a route <em>for</em>, and therefore what reaching
 * the end of it means.
 *
 * <p>This replaces the {@code isInbound} flag the controller used to thread
 * through every arrival decision. The flag answered "which of the delivery's
 * two polylines is this", which forced every route a vehicle could ever drive
 * to be one of exactly two legs of one errand. A leg says what kind of journey
 * this is instead, so the route itself becomes something the caller supplies —
 * a delivery supplies two in sequence, and a player move order supplies one.
 *
 * <p>The properties are named rather than switched on at the use site, because
 * adding a leg should be a matter of answering these questions for it rather
 * than of finding every {@code == DELIVERY_RUN} in the controller.
 */
public enum VehicleLeg {

    /**
     * Driving in to a drop point to unload. The end of this route is a place
     * the vehicle must be <em>exactly</em>, on a known departure heading, so it
     * gets the tight tolerance, the snap, and the docking maneuver that earns
     * the heading.
     */
    DELIVERY_RUN,

    /**
     * Driving out to an off-map exit. The end of this route is a direction more
     * than a point — the vehicle is leaving — so the tolerance is loose, there
     * is nothing to snap to, and its problem is getting <em>started</em> rather
     * than stopping accurately.
     */
    DEPARTURE_RUN;

    /** Authored arrival tolerance (cells) for the end of this leg. */
    float arrivalFloorCells() {
        return this == DELIVERY_RUN
                ? VehicleController.LZ_ARRIVAL_DIST
                : VehicleController.EXIT_ARRIVAL_DIST;
    }

    /**
     * Whether arrival places the body exactly on the final waypoint. True only
     * where the endpoint is a specific place the vehicle is expected to occupy;
     * the tolerance is tight enough that the correction is invisible.
     */
    boolean snapsToEndpoint() {
        return this == DELIVERY_RUN;
    }

    /**
     * Whether the terminal Reeds-Shepp docking maneuver may engage near the
     * end. Docking exists to land the body on an authored goal facing, so it is
     * only meaningful for a leg whose endpoint has one.
     */
    boolean docksOnArrival() {
        return this == DELIVERY_RUN;
    }

    /**
     * Whether reaching the planner's soft terminal region counts as arriving
     * when no safe forward segment remains. A footprint-constrained pose a
     * little short of the drop point is the best landing available and must not
     * hold the payload forever; a departure that cannot finish has not left.
     */
    boolean terminalRegionCountsAsArrival() {
        return this == DELIVERY_RUN;
    }

    /**
     * Whether the vehicle may back and fill onto the corridor when forward
     * planning finds nothing. This is the getting-started problem: only a leg
     * that can begin misaligned needs it, and a delivery approaches on the
     * heading its own route gave it.
     */
    boolean mayTurnAroundOntoRoute() {
        return this == DEPARTURE_RUN;
    }
}
