package com.dillon.starsectormarines.battle.warship;

import java.util.Map;

/** A loadout read from a stock {@code .variant} file through {@link WarshipVariants}. */
public final class StockVariantLoadout implements WarshipLoadout {

    private final String hullId;
    private final Map<String, String> weapons;

    public StockVariantLoadout(String hullId, String variantId) {
        this.hullId = hullId;
        this.weapons = WarshipVariants.weaponsBySlot(hullId, variantId);
    }

    @Override public String hullId() {
        return hullId;
    }

    @Override public String weaponInSlot(String slotId) {
        return weapons.get(slotId);
    }
}
