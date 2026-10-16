package com.dillon.starsectormarines.battle.warship;

import java.util.Map;

/** A loadout read from a stock {@code .variant} file through {@link WarshipVariants}. */
public final class StockVariantLoadout implements WarshipLoadout {

    private final String hullId;
    private final Map<String, String> weapons;
    private final int[] fittings;

    public StockVariantLoadout(String hullId, String variantId) {
        this.hullId = hullId;
        this.weapons = WarshipVariants.weaponsBySlot(hullId, variantId);
        this.fittings = WarshipVariants.fluxFittings(hullId, variantId);
    }

    @Override public int fluxCapacitors() {
        return fittings[0];
    }

    @Override public int fluxVents() {
        return fittings[1];
    }

    @Override public String hullId() {
        return hullId;
    }

    @Override public String weaponInSlot(String slotId) {
        return weapons.get(slotId);
    }
}
