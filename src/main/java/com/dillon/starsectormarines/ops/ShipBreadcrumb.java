package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;

import java.util.Locale;

/**
 * Where aboard a room screen is, said as the deck says it.
 *
 * <p>Read off the ship and the compartment rather than written on the page.
 * A literal breadcrumb is a claim nobody checks: these pages announced
 * FLAGSHIP for a company whose ship need not be the one they fly, a mech bay
 * on a hull that may have none, and a fixed berthing on a ship carrying
 * eighteen of them. Derived, the line changes when the ship does, which is the
 * only way it can stay true.
 *
 * <p>Naval order, coarse to fine: the ship, where on her deck, then the
 * compartment. The middle segment is what makes the ship feel like a place
 * rather than a menu — two squads berthed port and starboard of the same spine
 * read as living in different parts of a vessel, which they do.
 */
final class ShipBreadcrumb {

    private static final String SEPARATOR = " / ";

    private ShipBreadcrumb() { }

    /**
     * The full line for a compartment aboard this ship.
     *
     * @param room the compartment framed, or null for a page that is aboard
     *     without being a room — the line then stops at the ship
     */
    static String of(CompanyShip ship, DeckGraph.Compartment room) {
        if (ship == null) return "";
        String vessel = designation(ship);
        if (room == null) return vessel;
        return vessel + SEPARATOR + station(room) + SEPARATOR + words(room.purpose().name());
    }

    /**
     * What the ship is, as a hull rather than a name.
     *
     * <p>She has no name yet — the company is quartered on whatever the founding
     * hull is until the player chooses one. Her class and her role are true
     * regardless and are what distinguishes one candidate ship from another, so
     * this reads correctly both before and after that choice exists.
     */
    private static String designation(CompanyShip ship) {
        return words(ship.hullClass().name()) + " " + words(ship.role().name());
    }

    /** Where on the deck: the longitudinal zone and the side of the spine. */
    private static String station(DeckGraph.Compartment room) {
        return words(room.zone().name()) + " " + words(room.side().name());
    }

    private static String words(String constant) {
        return constant.replace('_', ' ').toUpperCase(Locale.ROOT);
    }
}
