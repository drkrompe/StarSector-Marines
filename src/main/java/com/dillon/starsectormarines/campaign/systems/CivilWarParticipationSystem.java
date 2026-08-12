package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.CivilWarBand;
import com.dillon.starsectormarines.campaign.CivilWarParticipation;
import com.dillon.starsectormarines.campaign.ContractState;

import java.util.EnumSet;

/** Recovers and applies completed civil-war contract contributions once. */
public final class CivilWarParticipationSystem implements CampaignSystem {

    @Override
    public String name() {
        return "CivilWarParticipation";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.CONTRACTS, CampaignTable.CHAINS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CONTRACTS, CampaignTable.CHAINS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null) return;
        for (int contractRow = 0; contractRow < state.contractCount; contractRow++) {
            if (CivilWarBand.fromByte(state.contractCivilWarBand[contractRow])
                    == CivilWarBand.NONE
                    || state.contractCivilWarContributionAppliedTick[contractRow] >= 0
                    || ContractState.fromByte(state.contractState[contractRow])
                        != ContractState.COMPLETED) {
                continue;
            }
            CivilWarParticipation.applyCompleted(
                    state, state.contractId[contractRow], day);
        }
    }
}
