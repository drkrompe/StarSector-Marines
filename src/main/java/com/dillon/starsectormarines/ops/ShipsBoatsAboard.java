package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipsBoats;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.util.List;

/**
 * The lift the company actually has: the boats aboard the ship they are on.
 *
 * <p><b>This replaces asking the fleet.</b> A detachment used to be assembled
 * from whichever transports the player owned and chose to commit, which made a
 * lift a thing you shopped for and a Valkyrie one of the items. It is not. The
 * company lives aboard one ship, that ship has bays, and what is in them is the
 * lift — so the question "what can we put on the ground" has exactly one answer
 * and it is a fact about where you are standing.
 *
 * <p>The campaign-side half of {@link ShipsBoats}: that one derives boats from a
 * hull and knows nothing about a sector, and this finds the hull. Split so the
 * derivation can be asked about any ship — a prize, a hull being considered —
 * rather than only about the one the company is on.
 */
public final class ShipsBoatsAboard {

    private ShipsBoatsAboard() { }

    /**
     * Every boat the company can put in the air, or empty when they are aboard
     * nothing this can read.
     *
     * <p>Empty is a real answer rather than a failure: a company with no ship
     * has no lift, and a briefing should say so plainly instead of quietly
     * finding transports somewhere else.
     */
    public static List<ShuttleType> lift() {
        FleetMemberAPI aboard = CompanyShipDesignation.aboard();
        if (aboard == null) return List.of();
        CompanyShip ship = CompanyShipResolver.read(aboard);
        return ShipsBoats.aboard(ship);
    }

    /** The ship the lift comes off, for a screen that wants to name her. */
    public static String carrier() {
        FleetMemberAPI aboard = CompanyShipDesignation.aboard();
        return aboard == null ? null : aboard.getShipName();
    }
}
