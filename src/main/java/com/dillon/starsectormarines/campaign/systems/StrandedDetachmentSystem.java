package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.StrandedDetachment;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.EnumSet;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Runs the clock on a stranded detachment nobody has come for: the daily attrition while
 * it waits, and the end of the recovery at its deadline or once nobody is left to collect.
 * Collection itself happens through the planet interaction.
 */
public final class StrandedDetachmentSystem implements CampaignSystem {

    private final Supplier<MarineRoster> rosterSource;
    private final Function<String, String> marketName;

    public StrandedDetachmentSystem() {
        this(StrandedDetachmentSystem::liveRoster, StrandedDetachmentSystem::liveMarketName);
    }

    StrandedDetachmentSystem(Supplier<MarineRoster> rosterSource,
                             Function<String, String> marketName) {
        this.rosterSource = rosterSource;
        this.marketName = marketName;
    }

    @Override
    public String name() {
        return "StrandedDetachment";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        MarineRoster roster = null;
        for (int row = 0; row < state.contractCount; row++) {
            if (!StrandedDetachment.isStranded(state, row)) continue;
            if (roster == null) roster = rosterSource.get();
            if (roster == null) return;
            StrandedDetachment.attrition(state, row, roster, day);
            boolean nobodyLeft = roster.stationedLivingCount(state.contractId[row]) == 0;
            if (nobodyLeft || day >= state.contractStrandedUntilTick[row]) {
                StrandedDetachment.expire(state, row, roster, day, marketName.apply(
                        state.marketRegistry.get(state.contractMarketId[row])));
            }
        }
    }

    private static MarineRoster liveRoster() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster() : null;
    }

    static String liveMarketName(String marketId) {
        if (marketId == null || Global.getSector() == null
                || Global.getSector().getEconomy() == null) {
            return null;
        }
        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
        return market != null ? market.getName() : null;
    }
}
