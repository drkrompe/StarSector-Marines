package com.dillon.starsectormarines.battle.smoke;

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
        if (smokeCells <= 0) return 1f;
        float mult = 1f;
        for (int i = 0; i < smokeCells; i++) {
            mult *= ACCURACY_MULT_PER_CELL;
            if (mult <= MIN_ACCURACY_MULT) return MIN_ACCURACY_MULT;
        }
        return mult;
    }
}
