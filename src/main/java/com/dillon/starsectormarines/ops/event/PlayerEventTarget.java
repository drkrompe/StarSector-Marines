package com.dillon.starsectormarines.ops.event;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.PlayerEventNotice;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/**
 * Resolves a notice's persisted market registry slot back to live campaign objects.
 *
 * <p>Split out because two callers need the same answer for different reasons: the card
 * needs a display name and needs to know whether Deploy is even possible, and the
 * presenter needs the {@link PlanetAPI} to build the Marine Ops context against. Both
 * must agree — an enabled Deploy button that then finds no planet is a dead end.
 */
public final class PlayerEventTarget {

    private PlayerEventTarget() {}

    public static MarketAPI market(PlayerEventNotice notice) {
        if (notice == null) return null;
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) return null;
        CampaignState state = script.state();
        String marketId = state.marketRegistry.get(notice.marketId);
        SectorAPI sector = Global.getSector();
        if (marketId == null || sector == null || sector.getEconomy() == null) return null;
        return sector.getEconomy().getMarket(marketId);
    }

    /**
     * The planet the stationed detachment defends, or null when the market has no
     * planet entity — an orbital station contract has nowhere for our ground battle to
     * happen, so Deploy is withheld rather than opening a Marine Ops screen that cannot
     * launch anything.
     */
    public static PlanetAPI planet(PlayerEventNotice notice) {
        MarketAPI market = market(notice);
        return market != null ? market.getPlanetEntity() : null;
    }

    public static String displayName(PlayerEventNotice notice) {
        MarketAPI market = market(notice);
        return market != null ? market.getName() : null;
    }
}
