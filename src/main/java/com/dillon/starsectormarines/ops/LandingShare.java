package com.dillon.starsectormarines.ops;

/**
 * The fraction of a battle by which the whole committed force should be on the
 * ground.
 *
 * <p>A mission states how much force it commits and how far out it lands; this
 * is the third statement of the same kind, and the one the lift is sized from.
 * It is deliberately a <em>share of the battle</em> rather than a shuttle count:
 * doubling the force should not double how long the force takes to arrive, and a
 * fixed count of craft is exactly what makes it do that.
 *
 * <p><b>The default is the share the fixture that wins already had.</b> Measured
 * across the 560x336 Conquest matrix, {@code reinforced-south} finished landing
 * at 29% of the battle and fought; {@code full-strength-west} finished at 97%,
 * committed its 34 squads in penny packets into a 226-cell approach, and had its
 * alive-squad count plateau at 12–15 from tick 6,000 onward because arrivals
 * exactly replaced losses. 30% is the winning fixture's own figure rounded, not
 * a target picked for it.
 */
public record LandingShare(float fraction) {

    /**
     * How long a battle is taken to be, in sim-seconds, when a share is turned
     * into a window.
     *
     * <p>A battle has no length of its own — it ends when a side does — so the
     * share needs a nominal one to be a share of, and this is the budget every
     * measurement in the matrix was taken against: 18,000 ticks at
     * {@code BattleSimulation.TICK_DT}. A battle that runs longer than this
     * merely lands its force in a smaller share than stated, which is the safe
     * direction to be wrong in.
     */
    public static final float NOMINAL_BATTLE_SECONDS = 600f;

    /** The winning fixture's own share; see this type's own measurement. */
    public static final LandingShare DEFAULT = new LandingShare(0.30f);

    public LandingShare {
        if (!Float.isFinite(fraction) || fraction <= 0f || fraction > 1f) {
            throw new IllegalArgumentException(
                    "a landing share is a fraction of the battle in (0, 1]: " + fraction);
        }
    }

    /** The sim-seconds the whole force has to be down in. */
    public float windowSeconds() {
        return fraction * NOMINAL_BATTLE_SECONDS;
    }
}
