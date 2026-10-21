package com.dillon.starsectormarines.battle.creep;

/**
 * How fast and how far one creep source spreads: the reach it gains per second
 * of battle time and the path distance, in cells, it stops at.
 *
 * <p>The field does not know what a bore tier is; a source hands it one of
 * these. {@link #FISSURE}, {@link #BORE} and {@link #MAW} are the presets a
 * bore of that tier declares.
 */
public record CreepGrowth(float cellsPerSecond, float maxReachCells) {

    /** A just-opened fissure: a slow, short stain. */
    public static final CreepGrowth FISSURE = new CreepGrowth(0.30f, 30f);
    /** A grown bore: the street and the buildings around it. */
    public static final CreepGrowth BORE = new CreepGrowth(0.45f, 50f);
    /** A maw: a whole precinct. */
    public static final CreepGrowth MAW = new CreepGrowth(0.60f, 80f);

    public CreepGrowth {
        if (!(cellsPerSecond >= 0f) || !(maxReachCells >= 0f))
            throw new IllegalArgumentException("Creep growth must be non-negative: " + cellsPerSecond + ", " + maxReachCells);
    }

    /** The preset for a bore tier counted from 0 (fissure); higher tiers clamp to maw. */
    public static CreepGrowth forTier(int tier) {
        if (tier <= 0) return FISSURE;
        return tier == 1 ? BORE : MAW;
    }
}
