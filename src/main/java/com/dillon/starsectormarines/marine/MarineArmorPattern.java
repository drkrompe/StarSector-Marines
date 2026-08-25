package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;

/** Persisted, player-ownable armor packages for modular infantry. */
public enum MarineArmorPattern {
    ARMORLESS("armor.field-fatigues", "Armorless fatigues", 1, "graphics/ui/armory/armor-tier-1-field-kit.png",
            0f, 0f, 1.08f, 0.92f),
    CHARCOAL("armor.combat", "Charcoal combat armor", 3, "graphics/ui/armory/armor-tier-3-combat.png",
            9f, 8f, 0.96f, 0.96f),
    BLUE_SCOUT("armor.scout", "Navy scout armor", 2, "graphics/ui/armory/armor-tier-2-scout.png",
            4f, 4f, 1.06f, 0.88f),
    RED_ELITE("armor.heavy", "Crimson elite armor", 4, "graphics/ui/armory/armor-tier-4-heavy.png",
            20f, 12f, 0.86f, 0.98f),
    OUTLAW("armor.outlaw", "Outlaw plate", 2, "graphics/ui/armory/armor-tier-2-scout.png",
            7f, 4f, 1.02f, 0.94f),
    ARMY_GREEN("armor.line", "Army-green armor", 3, "graphics/ui/armory/armor-tier-3-combat.png",
            10f, 8f, 0.94f, 0.97f),
    MILITIA("armor.militia", "Militia kit", 2, "graphics/ui/armory/armor-tier-2-scout.png",
            5f, 4f, 1.00f, 0.96f);

    public final String id;
    public final String displayName;
    public final int tier;
    public final String iconPath;
    /** Ablative protection pool seeded in front of structure. */
    public final float armorPool;
    /** Resistance matched against weapon penetration while armor remains. */
    public final float armorRating;
    /** Multiplier on the marine's movement speed. */
    public final float moveSpeedMult;
    /** Multiplier on hostile hit rolls; lower is harder to hit. */
    public final float incomingAccuracyMult;

    MarineArmorPattern(String id, String displayName, int tier, String iconPath,
                       float armorPool, float armorRating, float moveSpeedMult,
                       float incomingAccuracyMult) {
        this.id = id;
        this.displayName = displayName;
        this.tier = tier;
        this.iconPath = iconPath;
        this.armorPool = armorPool;
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
