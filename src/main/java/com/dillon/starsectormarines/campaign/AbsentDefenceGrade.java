package com.dillon.starsectormarines.campaign;

/**
 * Grades a Garrison defence the player never answered from the raid effectiveness
 * vanilla itself computed, with the stationed detachment already counted in the
 * defended market's ground defences.
 *
 * <p>Pure arithmetic: it reads no campaign state and no sector. The lapse path asks it
 * what happened, then writes the answer through the ordinary settlement policy.
 */
public final class AbsentDefenceGrade {

    /** How an absent detachment came out of a raid vanilla resolved without the player. */
    public enum Outcome {
        HELD,
        HELD_WITH_LOSSES,
        OVERRUN
    }

    /** Below this the raid is "likely to be largely repelled by the ground defences". */
    public static final float REPELLED_BAND = 0.33f;
    /** At or above this the raid is "likely to be successful". */
    public static final float SUCCESSFUL_BAND = 0.66f;

    /**
     * The share of the surviving-seat count an overrun garrison can lose at most. A raid
     * that lands is a defeat, not an extermination; the assignment fails either way.
     */
    private static final float OVERRUN_CASUALTY_CAP = 0.9f;
    /** Casualty share of the raid effectiveness for a defence that held its ground. */
    private static final float HELD_CASUALTY_COEFFICIENT = 0.5f;

    private AbsentDefenceGrade() {}

    /**
     * Vanilla's own forecast bands, so the raid intel's assessment and this settlement
     * agree: {@code RaidIntel} calls a raid below 0.33 largely repelled by the ground
     * defences, above 0.66 likely to be successful, and uncertain between.
     */
    public static Outcome grade(float raidEffectiveness) {
        if (raidEffectiveness < REPELLED_BAND) return Outcome.HELD;
        if (raidEffectiveness < SUCCESSFUL_BAND) return Outcome.HELD_WITH_LOSSES;
        return Outcome.OVERRUN;
    }

    /**
     * Mirrors {@code MarketCMD.getRaidEffectiveness(MarketAPI, float)}, which is the
     * ratio vanilla resolved the ground half of the raid with.
     */
    public static float raidEffectiveness(float attackerStrength, float defenderStrength) {
        float attacker = Math.max(0f, attackerStrength);
        float defender = Math.max(0f, defenderStrength);
        return attacker / Math.max(1f, attacker + defender);
    }

    /**
     * Marines the detachment loses out of its {@code activeSeats} deployable strength.
     *
     * <p>The coefficients here are a first proposal to be judged against the live pass,
     * not a model the design docs own: a held defence loses half of what the raid's
     * effectiveness would have taken, and an overrun one loses that effectiveness
     * outright, capped so the garrison is beaten rather than annihilated.
     */
    public static int casualties(int activeSeats, Outcome outcome, float raidEffectiveness) {
        if (activeSeats <= 0 || outcome == null) return 0;
        float effectiveness = Math.max(0f, Math.min(1f, raidEffectiveness));
        float share = outcome == Outcome.OVERRUN
                ? Math.min(OVERRUN_CASUALTY_CAP, effectiveness)
                : effectiveness * HELD_CASUALTY_COEFFICIENT;
        return Math.min(activeSeats, Math.round(activeSeats * share));
    }
}
