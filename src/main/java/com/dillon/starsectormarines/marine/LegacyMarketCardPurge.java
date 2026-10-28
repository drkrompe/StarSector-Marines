package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;

/**
 * Takes the marine cards that older saves still have on market shelves back off them.
 *
 * <p>Loadout cards and mech patterns were once stocked as cargo on each open market. They are sold
 * by the company's store now ({@link EquipmentStore}), so a card left on a shelf would be a second,
 * unmanaged way to buy the same thing, and one the store would offer again.
 */
public final class LegacyMarketCardPurge {

    private LegacyMarketCardPurge() {}

    /** Removes every marine card from {@code submarket}'s cargo; returns how many stacks went. */
    static int purge(SubmarketAPI submarket) {
        CargoAPI cargo = submarket == null ? null : submarket.getCargo();
        if (cargo == null) return 0;
        int removed = 0;
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData special = stack.getSpecialDataIfSpecial();
            if (special != null && (LoadoutCardItemPlugin.ITEM_ID.equals(special.getId())
                    || EquipmentTemplateCardItemPlugin.ITEM_ID.equals(special.getId())
                    || MechPatternInventory.isPatternCard(special))) {
                cargo.removeStack(stack);
                removed++;
            }
        }
        if (removed > 0) cargo.removeEmptyStacks();
        return removed;
    }

    /** Purges every submarket of every market in the economy; safe to run on every load. */
    public static int purgeAllMarkets() {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getEconomy() == null) return 0;
        int removed = 0;
        for (MarketAPI market : sector.getEconomy().getMarketsCopy()) {
            if (market == null) continue;
            for (SubmarketAPI submarket : market.getSubmarketsCopy()) removed += purge(submarket);
        }
        return removed;
    }
}
