package com.dillon.starsectormarines.campaign.polity;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@link MarketProductionSignals} off the player's own colonies.
 *
 * <p>The whole adapter is four vanilla reads per market — two industries and two
 * commodity deficits — because the ladder they feed is the ground-relevant subset of
 * what vanilla's ship-quality manager consults, rather than
 * {@code PRODUCTION_QUALITY_MOD} inherited whole. Everything downstream of the four
 * booleans is pure.
 *
 * <p>Tolerant of an absent sector or economy: this class is reachable from a headless
 * rebuild, where {@code Global.getSector()} is null and the honest answer is
 * {@link GroundProductionQuality#NONE} rather than an exception.
 */
public final class VanillaProductionSignals implements ProductionSignals {

    @Override
    public GroundProductionQuality bestQuality() {
        return MarketProductionSignals.bestQuality(playerMarkets());
    }

    /** Every player-owned market's signals, in economy order. */
    public static List<MarketProductionSignals> playerMarkets() {
        List<MarketProductionSignals> out = new ArrayList<>();
        SectorAPI sector = Global.getSector();
        EconomyAPI economy = sector != null ? sector.getEconomy() : null;
        if (economy == null) return out;
        for (MarketAPI market : economy.getMarketsCopy()) {
            if (market == null || !market.isPlayerOwned()) continue;
            out.add(signalsOf(market));
        }
        return out;
    }

    /** One market's four facts. Public so the colony panel can show what it read. */
    public static MarketProductionSignals signalsOf(MarketAPI market) {
        return MarketProductionSignals.of(
                market.hasIndustry(Industries.HEAVYINDUSTRY),
                market.hasIndustry(Industries.ORBITALWORKS),
                isShort(market, Commodities.SUPPLIES),
                isShort(market, Commodities.HAND_WEAPONS));
    }

    private static boolean isShort(MarketAPI market, String commodityId) {
        CommodityOnMarketAPI commodity = market.getCommodityData(commodityId);
        return commodity != null && commodity.getDeficitQuantity() > 0;
    }
}
