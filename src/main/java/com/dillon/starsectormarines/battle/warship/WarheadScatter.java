package com.dillon.starsectormarines.battle.warship;

/**
 * How wide a multi-warhead missile's sheaf is: the radius of the ground its
 * warheads are scattered over, sized by the number and the blast of the
 * warheads so the footprint keeps the proportion of the one blast it replaces.
 * Where each lands, and when, is {@link SwarmLayout}'s.
 */
public final class WarheadScatter {

    /** The spread's radius as a multiple of one warhead's blast radius times the root of their number. */
    public static final float SPREAD_FACTOR = 1.4f;

    private WarheadScatter() {}

    /** The radius, in cells, of the ground {@code count} warheads of blast radius {@code blastCells} are spread over. */
    public static float spreadRadius(int count, float blastCells) {
        return SPREAD_FACTOR * blastCells * (float) Math.sqrt(Math.max(1, count));
    }
}
