package com.dillon.starsectormarines.battle.infantry;

/** Earned field experience derived from an individual soldier's XP total. */
public enum ExperienceTier {
    GREEN("Green", 0, 0.92f, 1.08f, 1.08f, 0.20f),
    REGULAR("Regular", 100, 1.00f, 1.00f, 1.00f, 0.50f),
    VETERAN("Veteran", 350, 1.07f, 0.95f, 0.92f, 0.75f),
    ELITE("Elite", 800, 1.13f, 0.90f, 0.84f, 0.90f);

    public final String displayName;
    public final int minimumXp;
    public final float accuracyMult;
    /** Multiplier on trigger cooldown; lower is better. */
    public final float cooldownMult;
    public final float spreadMult;
    /** Chance to withhold a primary round whose committed ballistic outcome would hit a friendly. */
    public final float friendlyFireHoldChance;

    ExperienceTier(String displayName, int minimumXp, float accuracyMult,
                   float cooldownMult, float spreadMult,
                   float friendlyFireHoldChance) {
        this.displayName = displayName;
        this.minimumXp = minimumXp;
        this.accuracyMult = accuracyMult;
        this.cooldownMult = cooldownMult;
        this.spreadMult = spreadMult;
        this.friendlyFireHoldChance = friendlyFireHoldChance;
    }

    public static ExperienceTier fromXp(int xp) {
        if (xp >= ELITE.minimumXp) return ELITE;
        if (xp >= VETERAN.minimumXp) return VETERAN;
        if (xp >= REGULAR.minimumXp) return REGULAR;
        return GREEN;
    }
}
