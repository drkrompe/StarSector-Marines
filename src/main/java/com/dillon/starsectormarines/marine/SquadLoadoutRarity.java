package com.dillon.starsectormarines.marine;

import java.util.Locale;

/** Authored campaign scarcity for a reusable squad loadout, never a random-roll weight. */
public enum SquadLoadoutRarity {
    COMMON("Common"),
    UNCOMMON("Uncommon"),
    RARE("Rare"),
    EXOTIC("Exotic");

    private final String displayName;

    SquadLoadoutRarity(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String cssClass() {
        return "rarity-" + name().toLowerCase(Locale.ROOT);
    }
}
