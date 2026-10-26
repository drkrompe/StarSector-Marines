package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.campaign.start.OpeningPort;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

/**
 * Where the company can buy: a civilized market the fleet is docked at, which would let the player trade.
 *
 * <p>Civilized is read from the vanilla market: a visible ({@link MarketAPI#isHidden}, which pirate
 * and Pather bases are), populated colony rather than a planet's bare conditions, with a spaceport.
 * Derelicts, ruins and empty worlds have no such market. A disrupted spaceport still counts, as it
 * does for the company's yard ({@link OpeningPort}). Planets and stations count alike.
 *
 * <p>Whether that market will trade is the base game's own decision, not re-derived here. Docking
 * runs vanilla's {@code MarketPostOpen} rules ({@code data/campaign/rules.csv}), which weigh the
 * transponder, the faction's standing and its patrols, and leave the result in the dialog's local
 * {@link #TRADE_MODE}: {@code OPEN} with docking clearance, {@code SNEAK} when the fleet came in
 * dark and unseen, {@code NONE} when refused. Vanilla then opens every submarket in {@code OPEN}
 * mode and only the black market otherwise ({@code BaseSubmarketPlugin.isEnabled}). So the company
 * buys wherever the player could: at a hostile port too, through its black market, with the
 * transponder off.
 */
public final class CivilizedPort {
    /** The dialog-local memory key vanilla's market rules set on docking. */
    public static final String TRADE_MODE = "$tradeMode";

    private CivilizedPort() { }

    public static boolean civilized(MarketAPI market) {
        return market != null && !market.isHidden() && !market.isPlanetConditionMarketOnly()
                && market.getSize() > 0 && OpeningPort.hasSpaceport(market);
    }

    /**
     * Whether vanilla lets the player trade at {@code market} in {@code tradeMode}: everything in
     * {@code OPEN} mode, only the black market in {@code SNEAK}, nothing otherwise.
     */
    public static boolean trades(MarketAPI market, String tradeMode) {
        if (market == null) return false;
        if ("OPEN".equals(tradeMode)) return true;
        return "SNEAK".equals(tradeMode) && market.hasSubmarket(Submarkets.SUBMARKET_BLACK);
    }

    /** The civilized market the fleet is docked at now and could trade with, or null. */
    public static MarketAPI docked() {
        var sector = Global.getSector();
        if (sector == null || sector.getCampaignUI() == null) return null;
        InteractionDialogAPI dialog = sector.getCampaignUI().getCurrentInteractionDialog();
        MarketAPI market = dialog == null || dialog.getInteractionTarget() == null
                ? null : dialog.getInteractionTarget().getMarket();
        return civilized(market) && trades(market, tradeMode(dialog)) ? market : null;
    }

    private static String tradeMode(InteractionDialogAPI dialog) {
        if (dialog.getPlugin() == null || dialog.getPlugin().getMemoryMap() == null) return null;
        MemoryAPI local = dialog.getPlugin().getMemoryMap().get(MemKeys.LOCAL);
        return local == null ? null : local.getString(TRADE_MODE);
    }
}
