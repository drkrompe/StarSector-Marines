package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.nature.Flora;

/**
 * Which living world a {@link SurfacePalette#VERDANT} map is: a look within the palette, not a palette of its own.
 *
 * <p>A jungle, a tundra and an ocean world are all habitable, and every rule the palette owns holds for each of them
 * unchanged: open water belongs there, the colony's lawn is grass, the woods grow, the field stones are mossed, a hill
 * is grass-topped, and each story map's pool of vetted seeds is the living one. What differs is what grows and how
 * much of the ground is wet, and that is this look's: the woods' species ({@link #flora}) and thickness
 * ({@link #woodland}), and the ground dressing the closing landscape pass lays over the wild grass (snow and
 * permafrost on a tundra, swamp on a jungle, marsh, sand and a sea on an ocean world).
 *
 * <p>Making each its own palette would have meant restating {@code VERDANT} in every exhaustive switch over the
 * palette, a vetted seed list for it in every story map pool and a block of surface fillers in the mapping, all
 * saying the same thing. A look keeps those decided once.
 *
 * <p>{@link #TEMPERATE} is the look of every map that names none: a story map built from a palette alone, a fixture,
 * and the green basin floor of a desert world.
 */
public enum LivingWorld {

    /** Temperate forest and meadow: the green woods ({@code terran}, {@code terran-eccentric}). */
    TEMPERATE(Flora.TEMPERATE, 1f),

    /** Dense, humid rainforest over swampy ground with standing water ({@code jungle}). */
    JUNGLE(Flora.JUNGLE, 1.35f),

    /** Boreal conifers and low heath over permafrost, with patchy snow ({@code tundra}). Still habitable. */
    TUNDRA(Flora.TAIGA, .7f),

    /** An ocean world fought on its islands and shores: a sea at the edge, sand and marsh, thin woods ({@code water}). */
    OCEAN(Flora.TEMPERATE, .55f);

    private final Flora flora;
    private final float woodland;

    LivingWorld(Flora flora, float woodland) {
        this.flora = flora;
        this.woodland = woodland;
    }

    /** The species the woods are drawn with: the twin art a placement names ({@link Flora#placedId}). */
    public Flora flora() {
        return flora;
    }

    /** How thick the woods grow, as a multiple of the landscape's own woodland share. UNTUNED. */
    public float woodland() {
        return woodland;
    }
}
