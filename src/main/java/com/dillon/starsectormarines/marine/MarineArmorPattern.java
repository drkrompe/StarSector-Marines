package com.dillon.starsectormarines.marine;

/** Persisted, player-ownable visual armor patterns for modular infantry. */
public enum MarineArmorPattern {
    ARMORLESS("Armorless fatigues"),
    CHARCOAL("Charcoal combat armor"),
    BLUE_SCOUT("Navy scout armor"),
    RED_ELITE("Crimson elite armor"),
    OUTLAW("Outlaw plate"),
    ARMY_GREEN("Army-green armor"),
    MILITIA("Militia kit");

    public final String displayName;

    MarineArmorPattern(String displayName) {
        this.displayName = displayName;
    }
}
