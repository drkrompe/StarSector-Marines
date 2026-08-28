package com.dillon.starsectormarines.battle.world.gen.ship;

/**
 * Hulls for tests and visual evidence to be drawn on.
 *
 * <p>In a campaign the company lives aboard a ship out of the player's own
 * fleet, so there is no production hull to point a test at. These stand in:
 * ordinary ships with the proportions and complements of the vanilla hulls they
 * are named for, so a deck generated on one is a deck the game could really
 * produce.
 */
public final class TestHulls {

    private TestHulls() { }

    /** A light troop transport: few hands to fly her, most of the hull is lift. */
    public static CompanyShip transport() {
        return new CompanyShip(HullClass.CRUISER, HullRole.TROOP_TRANSPORT,
                10, 250, 50, 0.28f);
    }
}
