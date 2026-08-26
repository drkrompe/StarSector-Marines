package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.SubmarketUpdateListener;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

import java.util.Set;

/** Populates ordinary faction markets with rotating, collectible equipment templates. */
public final class FactionEquipmentMarketStock implements SubmarketUpdateListener {

    public static final RepLevel MIN_LICENSE_RELATIONSHIP = RepLevel.FAVORABLE;

    @Override
    public void reportSubmarketCargoAndShipsUpdated(SubmarketAPI submarket) {
        if (submarket == null || !Submarkets.SUBMARKET_OPEN.equals(submarket.getSpecId())) return;
        MarketAPI market = submarket.getMarket();
        if (market == null || market.isHidden() || !market.isInEconomy()) return;

        SectorAPI sector = Global.getSector();
        if (sector == null) return;
        CampaignClockAPI clock = sector.getClock();
        long rotation = clock != null ? (long) clock.getCycle() * 12L + clock.getMonth() : 0L;
        FactionAPI playerFaction = sector.getPlayerFaction();
        RepLevel relationship = playerFaction != null
                ? playerFaction.getRelationshipLevel(market.getFactionId()) : null;
        boolean hasLicenseAccess = hasLicenseAccess(relationship);

        Set<String> unavailable = EquipmentTemplateCardInventory.playerUnavailableTemplateIds();
        if (unavailable == null) {
            MarineRosterScript script = MarineRosterScript.getInstance();
            unavailable = script != null
                    ? script.roster().armory().ownedEquipmentTemplateIds() : Set.of();
        }
        replaceStock(submarket, market.getFactionId(), market.getId(), market.getSize(),
                rotation, hasLicenseAccess, unavailable);
    }

    /** Seeds existing saves immediately; later rotations follow vanilla submarket refreshes. */
    public static void refreshAllMarkets() {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getEconomy() == null) return;
        FactionEquipmentMarketStock stock = new FactionEquipmentMarketStock();
        for (MarketAPI market : sector.getEconomy().getMarketsCopy()) {
            if (market == null) continue;
            SubmarketAPI openMarket = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
            if (openMarket != null) stock.reportSubmarketCargoAndShipsUpdated(openMarket);
        }
    }

    static void replaceStock(SubmarketAPI submarket, String factionId, String marketId,
                             int marketSize, long rotation, boolean hasLicenseAccess,
                             Set<String> ownedTemplateIds) {
        CargoAPI cargo = submarket.getCargo();
        if (cargo == null) return;
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData special = stack.getSpecialDataIfSpecial();
            if (special != null && EquipmentTemplateCardItemPlugin.ITEM_ID.equals(special.getId())) {
                cargo.removeStack(stack);
            }
        }

        FactionEquipmentMarketStockPlanner.StockPlan plan =
                FactionEquipmentMarketStockPlanner.plan(factionId, marketId, marketSize,
                        rotation, hasLicenseAccess, ownedTemplateIds);
        for (String templateId : plan.allTemplateIds()) {
            cargo.addSpecial(EquipmentTemplateCardItemPlugin.itemData(templateId), 1f);
        }
        cargo.removeEmptyStacks();
    }

    static boolean hasLicenseAccess(RepLevel relationship) {
        return relationship != null && relationship.isAtWorst(MIN_LICENSE_RELATIONSHIP);
    }
}
