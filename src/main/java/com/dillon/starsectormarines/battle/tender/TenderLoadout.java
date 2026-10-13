package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.warship.StockVariantLoadout;
import com.dillon.starsectormarines.battle.warship.VariantApiLoadout;
import com.dillon.starsectormarines.battle.warship.WarshipLoadout;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/**
 * The Shepherd as a tender carries it: its hull's slots and none of its guns.
 *
 * <p>The hull table and spec still come from the game's own data, through whichever
 * loadout names the hull: a campaign fleet member's fitted variant, or the stock
 * Shepherd variant when no member is at hand. What is left off is the vanilla
 * variant's missile rack and machine gun. A tender's weapons are its drones, and a
 * gun it could fire on its own would make the drones a decoration rather than the
 * thing the ship is for.
 */
final class TenderLoadout implements WarshipLoadout {

    /** The stock variant a tender is fitted from when no campaign ship supplies one. */
    static final String STOCK_VARIANT = "shepherd_Frontier";

    private final String hullId;

    private TenderLoadout(WarshipLoadout source) {
        this.hullId = source.hullId();
    }

    /** The stock Shepherd. */
    static TenderLoadout stock() {
        return new TenderLoadout(new StockVariantLoadout(Tender.HULL_ID, STOCK_VARIANT));
    }

    /**
     * A campaign member's own hull when it is a Shepherd (or a skin of one); the
     * stock Shepherd for a member that is absent, has no variant, or is another hull.
     */
    static TenderLoadout of(FleetMemberAPI member) {
        if (member == null || member.getVariant() == null) return stock();
        WarshipLoadout fitted = new VariantApiLoadout(member.getVariant());
        return Tender.HULL_ID.equals(fitted.hullId()) ? new TenderLoadout(fitted) : stock();
    }

    @Override public String hullId() {
        return hullId;
    }

    @Override public String weaponInSlot(String slotId) {
        return null;
    }
}
