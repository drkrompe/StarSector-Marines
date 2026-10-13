package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;
import java.util.Locale;

/** Authored campaign scarcity for a reusable squad loadout, never a random-roll weight. */
public enum SquadLoadoutRarity {
    COMMON("@gearSquadLoadoutRarityCOMMON"),
    UNCOMMON("@gearSquadLoadoutRarityUNCOMMON"),
    RARE("@gearSquadLoadoutRarityRARE"),
    EXOTIC("@gearSquadLoadoutRarityEXOTIC");

    private final String displayName;

    SquadLoadoutRarity(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return Strings.resolve(displayName);
    }

    public String cssClass() {
        return "rarity-" + name().toLowerCase(Locale.ROOT);
    }
}
