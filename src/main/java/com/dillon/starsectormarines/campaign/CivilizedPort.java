package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.start.OpeningPort;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/**
 * Where the company can buy: a civilized market the fleet is docked at.
 *
 * <p>Civilized is read from the vanilla market: a visible ({@link MarketAPI#isHidden}, which pirate
 * and Pather bases are), populated colony rather than a planet's bare conditions, with a spaceport,
 * whose owner is not hostile to the player. Derelicts, ruins and empty worlds have no such market;
 * a hostile port will not trade. A disrupted spaceport still counts, as it does for the company's
 * yard ({@link OpeningPort}). Planets and stations count alike.
 */
public final class CivilizedPort {
    private CivilizedPort() { }

    public static boolean civilized(MarketAPI market, FactionAPI player) {
        if (market == null || market.isHidden() || market.isPlanetConditionMarketOnly()
                || market.getSize() <= 0 || !OpeningPort.hasSpaceport(market)) return false;
        return market.getFaction() != null && (player == null || !market.getFaction().isHostileTo(player));
    }

    /** The civilized market the fleet is docked at now, or null. */
    public static MarketAPI docked() {
        var sector = Global.getSector();
        if (sector == null || sector.getCampaignUI() == null) return null;
        InteractionDialogAPI dialog = sector.getCampaignUI().getCurrentInteractionDialog();
        MarketAPI market = dialog == null || dialog.getInteractionTarget() == null
                ? null : dialog.getInteractionTarget().getMarket();
        return civilized(market, sector.getPlayerFaction()) ? market : null;
    }
}
