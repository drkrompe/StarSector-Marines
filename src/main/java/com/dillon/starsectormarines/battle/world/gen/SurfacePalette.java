package com.dillon.starsectormarines.battle.world.gen;

/**
 * What the world's own ground is made of, distilled from the target planet so
 * {@code battle.world.gen} never sees a game API. The campaign-decoupled
 * vocabulary for terrain, exactly as {@link EconomicFunction} is for industry.
 *
 * <p><b>This is the wild surface, not every green thing on the map.</b> A
 * settlement's parks and street verges are ground the colonists made and
 * maintain — irrigated lawn on an airless rock is a statement about the
 * colony's wealth, not about the planet — so cultivated fills keep their own
 * character and do not consult this. What follows the palette is ground nobody
 * planted: the hinterland the road growth never reached, and the wild
 * {@code NATURE_*} lots inside the built area.
 *
 * <p>Most of the Sector is not a garden. {@link #ROCK} is the baseline rather
 * than {@link #VERDANT}, because a habitable world is the rare case worth
 * remarking on and defaulting to grass quietly asserts the opposite on every
 * map that has not said otherwise.
 *
 * <p>Each palette is a key into the mapping registry's authored surface
 * fillers, so adding one is a constant plus a JSON block — no new
 * {@link BlockKind}, and no fork of the district weight tables.
 */
public enum SurfacePalette {

    /** Bare regolith — stone, dirt and broken rubble. Rocks scatter; nothing grows. */
    ROCK,

    /** Dust and sand over hardpan. Rocks scatter; nothing grows. */
    ARID,

    /** A living world: grass and soil, with shrubs and tufts among the stones. */
    VERDANT,
}
