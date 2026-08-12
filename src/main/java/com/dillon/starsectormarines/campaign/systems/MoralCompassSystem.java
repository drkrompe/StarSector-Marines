package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.CivilWarAllegiance;
import com.dillon.starsectormarines.campaign.CivilWarPlayerConsequenceState;
import com.dillon.starsectormarines.campaign.MoralChoiceRecorder;
import com.dillon.starsectormarines.campaign.MoralChoiceSource;

import java.util.EnumSet;

/** Records hidden moral meaning from fully attributed terminal outcomes. */
public final class MoralCompassSystem implements CampaignSystem {

    @Override
    public String name() {
        return "MoralCompass";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.THRONE_CLAIMS, CampaignTable.CHAINS,
                CampaignTable.MORAL_COMPASS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.MORAL_COMPASS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null) return;
        recordClaimantOutcomes(state, day);
        recordIncumbentOutcomes(state, day);
    }

    private static void recordClaimantOutcomes(CampaignState state, int day) {
        for (int row = 0; row < state.throneClaimCount; row++) {
            if (CivilWarPlayerConsequenceState.fromByte(
                    state.throneClaimPlayerConsequenceState[row])
                    != CivilWarPlayerConsequenceState.APPLIED
                    || CivilWarAllegiance.fromByte(
                        state.throneClaimPlayerAllegiance[row])
                        != CivilWarAllegiance.CLAIMANT) {
                continue;
            }
            int magnitude = magnitude(state.throneClaimPlayerContribution[row] & 0xFFFF);
            if (magnitude <= 0) continue;
            MoralChoiceRecorder.record(state, MoralChoiceSource.CIVIL_WAR_CLAIMANT,
                    state.throneClaimSourceChainId[row], 0, 0, 0, -magnitude,
                    state.throneClaimAppliedTick[row], day);
        }
    }

    private static void recordIncumbentOutcomes(CampaignState state, int day) {
        for (int row = 0; row < state.chainCount; row++) {
            if (CivilWarPlayerConsequenceState.fromByte(
                    state.chainPlayerConsequenceState[row])
                    != CivilWarPlayerConsequenceState.APPLIED
                    || CivilWarAllegiance.fromByte(state.chainPlayerAllegiance[row])
                        != CivilWarAllegiance.INCUMBENT) {
                continue;
            }
            int magnitude = magnitude(state.chainPlayerContribution[row] & 0xFFFF);
            if (magnitude <= 0) continue;
            MoralChoiceRecorder.record(state, MoralChoiceSource.CIVIL_WAR_INCUMBENT,
                    state.chainId[row], 0, 0, 0, magnitude,
                    state.chainResolvedTick[row], day);
        }
    }

    static int magnitude(int contribution) {
        if (contribution >= 60) return 20;
        if (contribution >= 30) return 10;
        return contribution > 0 ? 5 : 0;
    }
}
