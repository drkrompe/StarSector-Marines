package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.CivilWarPlayerConsequences;

import java.util.EnumSet;

/** Consumes attributed terminal civil-war outcomes exactly once. */
public final class CivilWarPlayerConsequenceSystem implements CampaignSystem {

    @Override
    public String name() {
        return "CivilWarPlayerConsequence";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.THRONE_CLAIMS, CampaignTable.CHAINS,
                CampaignTable.HOUSES, CampaignTable.PLAYER_REP);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.THRONE_CLAIMS, CampaignTable.CHAINS,
                CampaignTable.PLAYER_REP);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null) return;
        for (int claimRow = 0; claimRow < state.throneClaimCount; claimRow++) {
            CivilWarPlayerConsequences.applyClaimant(state, claimRow, day);
        }
        for (int chainRow = 0; chainRow < state.chainCount; chainRow++) {
            if (ChainArchetype.fromByte(state.chainArchetype[chainRow])
                    == ChainArchetype.CIVIL_WAR) {
                CivilWarPlayerConsequences.applyIncumbent(state, chainRow, day);
            }
        }
    }
}
