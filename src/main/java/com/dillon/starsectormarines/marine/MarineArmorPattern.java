package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;

/** Persisted, player-ownable armor packages for modular infantry. */
public enum MarineArmorPattern {
    ARMORLESS("armor.field-fatigues", "ARM-VAC-1-UNP", 1, "graphics/ui/armory/armor-armorless.png",
            0f, 0f, 1.08f, 0.92f),
    CHARCOAL("armor.combat", "ARM-PRSLG-3-LN", 3, "graphics/ui/armory/armor-charcoal.png",
            9f, 8f, 0.96f, 0.96f),
    BLUE_SCOUT("armor.scout", "ARM-TACH-2-RCN", 2, "graphics/ui/armory/armor-blue-scout.png",
            4f, 4f, 1.06f, 0.88f),
    RED_ELITE("armor.heavy", "ARM-HEG-4-PWR", 4, "graphics/ui/armory/armor-red-elite.png",
            60f, 12f, 0.86f, 0.98f),
    OUTLAW("armor.outlaw", "ARM-PIR-2-LN", 2, "graphics/ui/armory/armor-outlaw.png",
            7f, 4f, 1.02f, 0.94f),
    ARMY_GREEN("armor.line", "ARM-HEG-3-LN", 3, "graphics/ui/armory/armor-army-green.png",
            10f, 8f, 0.94f, 0.97f),
    MILITIA("armor.militia", "ARM-SEC-2-LN", 2, "graphics/ui/armory/armor-militia.png",
            5f, 4f, 1.00f, 0.96f);

    public final String id;
    public final String displayName;
    public final int tier;
    public final String iconPath;
    /** Ablative protection capacity seeded in front of structure. */
    public final float armorCapacity;
    /** Resistance matched against weapon penetration while armor remains. */
    public final float armorRating;
    /** Multiplier on the marine's movement speed. */
    public final float moveSpeedMult;
    /** Multiplier on hostile hit rolls; lower is harder to hit. */
    public final float incomingAccuracyMult;

    MarineArmorPattern(String id, String displayName, int tier, String iconPath,
                       float armorCapacity, float armorRating, float moveSpeedMult,
                       float incomingAccuracyMult) {
        this.id = id;
        this.displayName = displayName;
        this.tier = tier;
        this.iconPath = iconPath;
        this.armorCapacity = armorCapacity;
        this.armorRating = armorRating;
        this.moveSpeedMult = moveSpeedMult;
        this.incomingAccuracyMult = incomingAccuracyMult;
    }

    public LayeredArmorFamily layeredFamily() {
        return LayeredArmorFamily.valueOf(name());
    }

    public static MarineArmorPattern fromId(String id) {
        for (MarineArmorPattern pattern : values()) {
            if (pattern.id.equals(id)) return pattern;
        }
        throw new IllegalArgumentException("Unknown marine armor id '" + id + "'");
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
