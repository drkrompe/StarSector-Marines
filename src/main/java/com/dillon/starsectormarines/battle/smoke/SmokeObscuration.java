package com.dillon.starsectormarines.battle.smoke;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Turns how much smoke a round crosses into what that smoke costs the shot.
 *
 * <p>Smoke is <em>obscuration</em>, not interception: a cloud never stops a
 * round and never forbids a trigger pull. It degrades the sight picture, so a
 * shooter firing into or through one is shooting at a shape they cannot
 * resolve. Suppressing a screened position stays possible and stays a bad
 * trade, which is what a binary line-of-fire gate could not express — under
 * that rule a screen made both sides simply stop shooting.
 *
 * <p>Depth comes from {@code NavigationGrid.smokeDepthOnLine}, which counts
 * the smoke-filled cells on the shooter-to-target segment including both
 * endpoint cells. Each such cell multiplies accuracy by
 * {@link #ACCURACY_MULT_PER_CELL}, so the penalty compounds with how much
 * cloud is actually in the way rather than snapping between "clear" and
 * "impossible". A deliberate screen is roughly a cloud diameter deep and
 * lands near a third of normal accuracy — a heavier penalty than any authored
 * cover level, as a purpose-thrown grenade should be.
 *
 * <p>{@link #MIN_ACCURACY_MULT} keeps a deep or multi-cloud lane from
 * collapsing to a mathematically zero chance, preserving the difference
 * between "very bad odds" and the hard gate this model replaced.
 */
public final class SmokeObscuration {

    /** Accuracy retained per smoke-filled cell crossed. Compounds per cell. */
    public static final float ACCURACY_MULT_PER_CELL = 0.75f;

    /** Floor on the compounded multiplier, so smoke degrades a shot without ever forbidding it. */
    public static final float MIN_ACCURACY_MULT = 0.05f;

    /**
     * Accuracy retained per hazed cell crossed: a Bombard's spore haze ({@code bombard.md}), the see-through
     * obscurant. Milder than smoke because it never blocks sight: a squad in it still sees and picks its targets
     * and only shoots worse. A starting value, to be felt out in play.
     */
    public static final float HAZE_ACCURACY_MULT_PER_CELL = 0.85f;

    private SmokeObscuration() {}

    /**
     * Accuracy multiplier for a round crossing {@code smokeCells} smoke-filled
     * cells. Zero cells is exactly {@code 1f} — an unobscured shot pays
     * nothing, in the arithmetic as well as in intent.
     *
     * @param smokeCells cells of transient opacity on the segment; negative is
     *                   treated as none
     */
    public static float accuracyMultiplier(int smokeCells) {
        return accuracyMultiplier(smokeCells, 0);
    }

    /**
     * Accuracy multiplier for a round crossing {@code smokeCells} smoke-filled and {@code hazeCells} hazed cells:
     * each kind compounds its own price per cell, under the one floor.
     */
    public static float accuracyMultiplier(int smokeCells, int hazeCells) {
        float mult = 1f;
        for (int i = 0; i < smokeCells; i++) {
            mult *= ACCURACY_MULT_PER_CELL;
            if (mult <= MIN_ACCURACY_MULT) return MIN_ACCURACY_MULT;
        }
        for (int i = 0; i < hazeCells; i++) {
            mult *= HAZE_ACCURACY_MULT_PER_CELL;
            if (mult <= MIN_ACCURACY_MULT) return MIN_ACCURACY_MULT;
        }
        return mult;
    }

    /** What the obscurants on the exact segment from {@code (x0,y0)} to {@code (x1,y1)} cost a round. */
    public static float onLine(NavigationGrid grid, float x0, float y0, float x1, float y1) {
        return accuracyMultiplier(grid.smokeDepthOnLine(x0, y0, x1, y1), grid.hazeDepthOnLine(x0, y0, x1, y1));
    }
}
