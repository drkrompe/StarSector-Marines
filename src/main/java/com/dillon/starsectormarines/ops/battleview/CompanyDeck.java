package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip;
import com.dillon.starsectormarines.battle.world.gen.ship.DeckGraph;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipDeckGenerator;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

/**
 * The company ship's interior, as operations screens see it.
 *
 * <p>One deck, generated once from the hull and then answered from. The screens
 * that look at parts of the ship are cameras onto this, so a berthing screen and
 * the Mech Lab are framings of one interior rather than two rooms kept in step
 * by hand — and what one of them shows as damaged the other shows as damaged.
 *
 * <p><b>What the ship has is a question with a real answer.</b> A hull that
 * structurally cannot hold a vehicle bay does not acquire one by being refitted,
 * and a hull whose program owed a room the deck could not fit does not have that
 * room either. Both come back the same way here, because from the player's side
 * they are the same fact: there is no such place aboard. That is what a screen
 * consults before offering to take them to it.
 *
 * <p>Generation is deferred to first use rather than done in the constructor. A
 * deck is a few tens of milliseconds of work, most sessions never open a room
 * view at all, and nothing about the ship changes in the meantime.
 */
public final class CompanyDeck {

    private final CompanyShip ship;
    private final long seed;
    private MapResult deck;
    private DeckGraph rooms;

    /**
     * @param seed fixes the layout, so the ship a player leaves is the ship they
     *     come back to; it belongs to the company rather than to the session
     */
    public CompanyDeck(CompanyShip ship, long seed) {
        if (ship == null) throw new IllegalArgumentException("a company ship is required");
        this.ship = ship;
        this.seed = seed;
    }

    public CompanyShip ship() {
        return ship;
    }

    /**
     * Whether the ship has somewhere of this kind at all.
     *
     * <p>The question a room view asks before it offers itself. An uninhabitable
     * hull has nothing, which is the honest answer rather than an error: a
     * company quartered on a frigate has no rooms to walk around, and the
     * screens should say so by being unavailable.
     */
    public boolean has(RoomPurpose purpose) {
        if (purpose == null) return false;
        if (!ship.habitable()) return false;
        return rooms().largest(purpose) != null;
    }

    /**
     * The compartment this ship means by a purpose, or {@code null} when she has
     * none. Callers that have already asked {@link #has} may take it as present.
     */
    public DeckGraph.Compartment room(RoomPurpose purpose) {
        if (!ship.habitable()) return null;
        return rooms().largest(purpose);
    }

    /**
     * The generated deck: grid, topology, fixtures, and everything standing on
     * it.
     *
     * @throws IllegalStateException if the hull has no playable interior; ask
     *     {@link CompanyShip#habitable} first, or {@link #has}, which answers
     *     for an uninhabitable ship without generating anything
     */
    public MapResult map() {
        generate();
        return deck;
    }

    /** The deck's compartments. @see #map() */
    public DeckGraph rooms() {
        generate();
        return rooms;
    }

    private void generate() {
        if (deck != null) return;
        if (!ship.habitable()) {
            throw new IllegalStateException(
                    "a " + ship.hullClass() + " has no interior to walk around");
        }
        ShipDeckGenerator generator = new ShipDeckGenerator();
        deck = generator.generateDeck(ship.deckPlan(), seed, null);
        rooms = generator.getLastDeckGraph();
    }
}
