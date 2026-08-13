package com.dillon.starsectormarines.marine;

/** Persisted, player-ownable armor packages for modular infantry. */
public enum MarineArmorPattern {
    ARMORLESS("Armorless fatigues", 1, "graphics/ui/armory/armor-tier-1-field-kit.png",
            0f, 0f, 1.08f, 0.92f),
    CHARCOAL("Charcoal combat armor", 3, "graphics/ui/armory/armor-tier-3-combat.png",
            0.12f, 5f, 0.96f, 0.96f),
    BLUE_SCOUT("Navy scout armor", 2, "graphics/ui/armory/armor-tier-2-scout.png",
            0.06f, 2f, 1.06f, 0.88f),
    RED_ELITE("Crimson elite armor", 4, "graphics/ui/armory/armor-tier-4-heavy.png",
            0.20f, 8f, 0.86f, 0.98f),
    OUTLAW("Outlaw plate", 2, "graphics/ui/armory/armor-tier-2-scout.png",
            0.08f, 3f, 1.02f, 0.94f),
    ARMY_GREEN("Army-green armor", 3, "graphics/ui/armory/armor-tier-3-combat.png",
            0.14f, 4f, 0.94f, 0.97f),
    MILITIA("Militia kit", 2, "graphics/ui/armory/armor-tier-2-scout.png",
            0.07f, 2f, 1.00f, 0.96f);

    public final String displayName;
    public final int tier;
    public final String iconPath;
    /** Fraction of post-cover incoming damage prevented. */
    public final float damageReduction;
    /** Flat health added to the marine's 25 HP baseline. */
    public final float bonusHp;
    /** Multiplier on the marine's movement speed. */
    public final float moveSpeedMult;
    /** Multiplier on hostile hit rolls; lower is harder to hit. */
    public final float incomingAccuracyMult;

    MarineArmorPattern(String displayName, int tier, String iconPath,
                       float damageReduction, float bonusHp, float moveSpeedMult,
                       float incomingAccuracyMult) {
        this.displayName = displayName;
        this.tier = tier;
        this.iconPath = iconPath;
        this.damageReduction = damageReduction;
        this.bonusHp = bonusHp;
        this.moveSpeedMult = moveSpeedMult;
        this.incomingAccuracyMult = incomingAccuracyMult;
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
