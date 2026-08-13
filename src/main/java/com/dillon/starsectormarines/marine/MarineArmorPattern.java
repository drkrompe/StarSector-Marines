package com.dillon.starsectormarines.marine;

/** Persisted, player-ownable visual armor patterns for modular infantry. */
public enum MarineArmorPattern {
    ARMORLESS("Armorless fatigues", 1, "graphics/ui/armory/armor-tier-1-field-kit.png"),
    CHARCOAL("Charcoal combat armor", 3, "graphics/ui/armory/armor-tier-3-combat.png"),
    BLUE_SCOUT("Navy scout armor", 2, "graphics/ui/armory/armor-tier-2-scout.png"),
    RED_ELITE("Crimson elite armor", 4, "graphics/ui/armory/armor-tier-4-heavy.png"),
    OUTLAW("Outlaw plate", 2, "graphics/ui/armory/armor-tier-2-scout.png"),
    ARMY_GREEN("Army-green armor", 3, "graphics/ui/armory/armor-tier-3-combat.png"),
    MILITIA("Militia kit", 2, "graphics/ui/armory/armor-tier-2-scout.png");

    public final String displayName;
    /** Visual equipment tier. Protection stats are intentionally unchanged. */
    public final int tier;
    public final String iconPath;

    MarineArmorPattern(String displayName, int tier, String iconPath) {
        this.displayName = displayName;
        this.tier = tier;
        this.iconPath = iconPath;
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
