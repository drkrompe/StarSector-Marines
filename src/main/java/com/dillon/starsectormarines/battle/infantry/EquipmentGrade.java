package com.dillon.starsectormarines.battle.infantry;

/**
 * Manufacturing/condition tier applied to a weapon family. Family controls
 * the firing pattern and art; grade provides small, composable stat changes.
 */
public enum EquipmentGrade {
    SURPLUS   (1, "Surplus",    0.92f, 0.95f, 0.90f, 1.10f, 1.18f),
    SERVICE   (2, "Service",    1.00f, 1.00f, 1.00f, 1.00f, 1.00f),
    MILSPEC   (3, "Milspec",    1.05f, 1.05f, 1.07f, 0.94f, 0.88f),
    MASTERWORK(4, "Masterwork", 1.08f, 1.08f, 1.13f, 0.88f, 0.76f);

    public final int tier;
    public final String displayName;
    public final float rangeMult;
    public final float damageMult;
    public final float accuracyMult;
    /** Multiplier on seconds between trigger pulls; lower is better. */
    public final float cooldownMult;
    public final float spreadMult;

    EquipmentGrade(int tier, String displayName, float rangeMult, float damageMult,
                   float accuracyMult, float cooldownMult, float spreadMult) {
        this.tier = tier;
        this.displayName = displayName;
        this.rangeMult = rangeMult;
        this.damageMult = damageMult;
        this.accuracyMult = accuracyMult;
        this.cooldownMult = cooldownMult;
        this.spreadMult = spreadMult;
    }

    public String tierMark() {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "IV";
        };
    }
}
