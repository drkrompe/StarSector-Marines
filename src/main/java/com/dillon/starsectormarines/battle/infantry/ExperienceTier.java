package com.dillon.starsectormarines.battle.infantry;

/** Earned field experience derived from an individual soldier's XP total. */
public enum ExperienceTier {
    GREEN("Green", 0, 0.92f, 1.08f, 1.08f),
    REGULAR("Regular", 100, 1.00f, 1.00f, 1.00f),
    VETERAN("Veteran", 350, 1.07f, 0.95f, 0.92f),
    ELITE("Elite", 800, 1.13f, 0.90f, 0.84f);

    public final String displayName;
    public final int minimumXp;
    public final float accuracyMult;
    /** Multiplier on trigger cooldown; lower is better. */
    public final float cooldownMult;
    public final float spreadMult;

    ExperienceTier(String displayName, int minimumXp, float accuracyMult,
                   float cooldownMult, float spreadMult) {
        this.displayName = displayName;
        this.minimumXp = minimumXp;
        this.accuracyMult = accuracyMult;
        this.cooldownMult = cooldownMult;
        this.spreadMult = spreadMult;
    }

    public static ExperienceTier fromXp(int xp) {
        if (xp >= ELITE.minimumXp) return ELITE;
        if (xp >= VETERAN.minimumXp) return VETERAN;
        if (xp >= REGULAR.minimumXp) return REGULAR;
        return GREEN;
    }
}
