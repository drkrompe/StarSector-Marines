package com.dillon.starsectormarines.battle.world.gen.ship;

/**
 * The hull the company lives aboard, as the facts a deck is generated from.
 *
 * <p>A hull, not a deck. What the company has is a ship; the interior is what
 * that ship implies, and generating it is a separate step so that acquiring a
 * better hull and refitting the one you have are two independent ways to change
 * where the company lives. See {@code company-ship.md}.
 *
 * <p>This is deliberately the vanilla numbers a hull already publishes —
 * complement, hold, and proportions — rather than a bespoke description of an
 * interior. A ship the player bought in the ordinary way has to be able to
 * become a company ship without anybody authoring rooms for it, or the choice
 * collapses to a short list of hulls somebody prepared.
 *
 * @param minCrew crew needed to work the ship; a vanilla hull's {@code min crew}
 * @param maxCrew everyone she can carry; a vanilla hull's {@code max crew}
 * @param cargo hold capacity; a vanilla hull's {@code cargo}
 * @param aspect beam over length, which gives the deck the hull's proportions
 */
public record CompanyShip(HullClass hullClass, HullRole role,
                          int minCrew, int maxCrew, int cargo, float aspect) {

    public CompanyShip {
        if (hullClass == null) throw new IllegalArgumentException("a hull class is required");
        if (role == null) throw new IllegalArgumentException("a hull role is required");
        if (minCrew < 0 || maxCrew < minCrew) {
            throw new IllegalArgumentException(
                    "a hull carries at least her own crew: " + minCrew + ".." + maxCrew);
        }
        if (cargo < 0) throw new IllegalArgumentException("hold capacity cannot be negative");
        if (!(aspect > 0f)) throw new IllegalArgumentException("a hull has a positive beam");
    }

    /** What this hull owes in rooms, and how much deck to lay them out on. */
    public DeckSizing.DeckPlan deckPlan() {
        return DeckSizing.planFor(hullClass, role, minCrew, maxCrew, cargo, aspect);
    }

    /**
     * Whether this hull can be lived aboard at all.
     *
     * <p>Only a hull with no deck of its own fails this — a fighter is carried
     * rather than entered. Everything from a frigate up has an interior; how
     * poor a home it makes is a matter of what its deck turns out to hold, not
     * of whether it has one.
     */
    public boolean habitable() {
        return hullClass.boardable();
    }
}
