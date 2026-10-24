package com.dillon.starsectormarines.ui.spec;

/**
 * A rating drawn as a row of symbols lit in quarters ({@code ui-nouns.md}).
 *
 * <p>The scale is absolute: {@link #SLOTS} symbols span the whole 0..1 rating, so
 * one symbol is a tenth of it on every card and sheet. Each symbol lights in
 * quarters, floored, so a row never shows more than the rating holds.
 */
public final class RatingSymbols {

    /** Symbols in one row. */
    public static final int SLOTS = 10;

    /** Lit steps one symbol shows: a quarter, a half, three quarters, all of it. */
    public static final int STEPS = 4;

    /** Firepower: a weapon's rating. */
    public static final String SWORD = "sword";

    /** Protection: an armour's rating. */
    public static final String SHIELD = "shield";

    private RatingSymbols() {
    }

    /**
     * How much of each symbol is lit for this rating, each a multiple of
     * {@code 1 / STEPS}.
     *
     * @param rating the rating in 0..1; values outside are clamped
     */
    public static float[] fills(float rating) {
        float clamped = Math.max(0f, Math.min(1f, rating));
        // The nudge keeps a rating stored as a whole percentage, such as 0.05f, from flooring a step short.
        int lit = (int) Math.floor(clamped * SLOTS * STEPS + 1e-4f);
        float[] fills = new float[SLOTS];
        for (int slot = 0; slot < SLOTS; slot++) {
            fills[slot] = Math.max(0, Math.min(STEPS, lit - slot * STEPS)) / (float) STEPS;
        }
        return fills;
    }

    /** The lit picture of a symbol. */
    public static String litImage(String symbol) {
        return "graphics/ui/equipment/rating-" + symbol + "-full.svg";
    }

    /** The unlit picture of a symbol, the same shape the lit one is drawn over. */
    public static String emptyImage(String symbol) {
        return "graphics/ui/equipment/rating-" + symbol + "-empty.svg";
    }

    /** The style that clips a slot's lit picture to its fill. */
    public static String litStyle(float fill) {
        return "width: " + Math.round(fill * 100f) + "%;";
    }
}
