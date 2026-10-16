package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CampaignStateScript;
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
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Populates ordinary faction markets with rotating, collectible loadout cards and, on larger
 * or military markets, one mech pattern card.
 */
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

        Set<String> unavailable = LoadoutCardInventory.playerUnavailableCardIds();
        if (unavailable == null) {
            MarineRosterScript script = MarineRosterScript.getInstance();
            unavailable = script != null
                    ? LoadoutCardInventory.knownCardIds(script.roster().armory()) : Set.of();
        }
        CampaignStateScript campaign = CampaignStateScript.getInstance();
        List<String> listed = new ArrayList<>();
        if (campaign != null) {
            for (MarketListings.Listing listing : MarketListings.at(
                    campaign.state(), market.getId(), market.getFactionId())) {
                listed.add(listing.cardId());
            }
        }
        EquipmentAcquisitionEligibility.Progress progress =
                EquipmentAcquisitionEligibility.currentProgress();
        replaceStock(submarket, market.getFactionId(), market.getId(), market.getSize(),
                rotation, hasLicenseAccess, unavailable, progress, listed);

        Set<String> unavailablePatterns = MechPatternInventory.playerUnavailablePatternIds();
        if (unavailablePatterns == null) {
            unavailablePatterns = MechPatternInventory.knownPatternIds(MechPatternInventory.playerBay());
        }
        boolean military = market.hasIndustry(Industries.MILITARYBASE)
                || market.hasIndustry(Industries.HIGHCOMMAND);
        replaceMechPatternStock(submarket.getCargo(), FactionEquipmentMarketStockPlanner.mechPattern(
                market.getFactionId(), market.getId(), market.getSize(), military, rotation,
                hasLicenseAccess, unavailablePatterns, progress));
    }

    /** Replaces the market's mech pattern cards, a stack separate from its loadout cards. */
    static void replaceMechPatternStock(CargoAPI cargo, List<String> patternIds) {
        if (cargo == null) return;
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (MechPatternInventory.isPatternCard(stack.getSpecialDataIfSpecial())) {
                cargo.removeStack(stack);
            }
        }
        for (String patternId : patternIds) {
            cargo.addSpecial(MechPatternInventory.itemData(patternId), 1f);
        }
        cargo.removeEmptyStacks();
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
                             Set<String> unavailableCardIds,
                             EquipmentAcquisitionEligibility.Progress progress) {
        replaceStock(submarket, factionId, marketId, marketSize, rotation, hasLicenseAccess,
                unavailableCardIds, progress, List.of());
    }

    static void replaceStock(SubmarketAPI submarket, String factionId, String marketId,
                             int marketSize, long rotation, boolean hasLicenseAccess,
                             Set<String> unavailableCardIds,
                             EquipmentAcquisitionEligibility.Progress progress,
                             List<String> listedCardIds) {
        CargoAPI cargo = submarket.getCargo();
        if (cargo == null) return;
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData special = stack.getSpecialDataIfSpecial();
            if (special != null && (LoadoutCardItemPlugin.ITEM_ID.equals(special.getId())
                    || EquipmentTemplateCardItemPlugin.ITEM_ID.equals(special.getId()))) {
                cargo.removeStack(stack);
            }
        }

        FactionEquipmentMarketStockPlanner.StockPlan plan =
                FactionEquipmentMarketStockPlanner.plan(factionId, marketId, marketSize,
                        rotation, hasLicenseAccess, unavailableCardIds, progress, listedCardIds);
        for (String cardId : plan.allCardIds()) {
            cargo.addSpecial(LoadoutCardItemPlugin.itemData(cardId), 1f);
        }
        cargo.removeEmptyStacks();
    }

    static boolean hasLicenseAccess(RepLevel relationship) {
        return relationship != null && relationship.isAtWorst(MIN_LICENSE_RELATIONSHIP);
    }
}
