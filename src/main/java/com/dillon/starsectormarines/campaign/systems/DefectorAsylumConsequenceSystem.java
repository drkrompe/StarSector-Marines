package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.DefectorAsylumConsequences;

import java.util.EnumSet;

/** Consumes terminal defector-asylum outcomes exactly once. */
public final class DefectorAsylumConsequenceSystem implements CampaignSystem {

    @Override
    public String name() {
        return "DefectorAsylumConsequence";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.EVENTS, CampaignTable.CHAINS,
                CampaignTable.HOUSES, CampaignTable.PLAYER_REP,
                CampaignTable.MORAL_COMPASS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CHAINS, CampaignTable.PLAYER_REP,
                CampaignTable.MORAL_COMPASS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null) return;
        for (int row = 0; row < state.eventCount; row++) {
            DefectorAsylumConsequences.apply(state, row, day);
        }
    }
}
