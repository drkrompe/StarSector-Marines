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
 * @param outline the hull's own form, or null for a ship whose shape could not
 *     be read — her deck then takes a synthetic taper, which is a plausible
 *     ship rather than a particular one
 * @param art how this hull is drawn — the sprite path her {@code .ship} file
 *     names — or null for a ship whose art cannot be resolved. A path and
 *     nothing else, so carrying it here adds no dependency on how anything is
 *     rendered. It rides with {@link #outline} because they are the same fact
 *     read twice: the shape a deck is laid out inside and the picture of the
 *     vessel that shape came from. Split apart, a screen that wants both has to
 *     find the second one itself — and the one that did forgot, so the ship
 *     view drew a deck plan floating in empty space.
 */
public record CompanyShip(HullClass hullClass, HullRole role,
                          int minCrew, int maxCrew, int cargo, float aspect,
                          HullSilhouette outline, String art) {

    public CompanyShip {
        // A hull has one set of proportions. Carrying both an outline and a
        // separate aspect invites them to disagree, and the outline is the one
        // the deck is actually laid out inside.
        if (outline != null) aspect = outline.aspect();
        if (hullClass == null) throw new IllegalArgumentException("a hull class is required");
        if (role == null) throw new IllegalArgumentException("a hull role is required");
        if (minCrew < 0 || maxCrew < minCrew) {
            throw new IllegalArgumentException(
                    "a hull carries at least her own crew: " + minCrew + ".." + maxCrew);
        }
        if (cargo < 0) throw new IllegalArgumentException("hold capacity cannot be negative");
        if (!(aspect > 0f)) throw new IllegalArgumentException("a hull has a positive beam");
    }

    /** A hull whose form and art are both known, which is a ship out of the fleet. */
    public CompanyShip(HullClass hullClass, HullRole role,
                       int minCrew, int maxCrew, int cargo,
                       HullSilhouette outline, String art) {
        this(hullClass, role, minCrew, maxCrew, cargo, 1f, outline, art);
    }

    /** A hull whose form is known: her proportions come from her own outline. */
    public CompanyShip(HullClass hullClass, HullRole role,
                       int minCrew, int maxCrew, int cargo, HullSilhouette outline) {
        this(hullClass, role, minCrew, maxCrew, cargo, 1f, outline, null);
    }

    /** A hull known only by her proportions, with no outline to lay a deck in. */
    public CompanyShip(HullClass hullClass, HullRole role,
                       int minCrew, int maxCrew, int cargo, float aspect) {
        this(hullClass, role, minCrew, maxCrew, cargo, aspect, null, null);
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
