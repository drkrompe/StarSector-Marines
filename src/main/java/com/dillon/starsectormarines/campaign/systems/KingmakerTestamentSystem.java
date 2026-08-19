package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.CivilWarAllegiance;
import com.dillon.starsectormarines.campaign.CivilWarPlayerConsequenceState;
import com.dillon.starsectormarines.campaign.MoralChoiceSource;
import com.dillon.starsectormarines.campaign.ThroneClaimState;

import java.util.EnumSet;

/** Seals immutable moral-history snapshots for decisive attributed kingmakers. */
public final class KingmakerTestamentSystem implements CampaignSystem {

    static final int DECISIVE_CONTRIBUTION = 60;

    @Override
    public String name() {
        return "KingmakerTestament";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.THRONE_CLAIMS, CampaignTable.CHAINS,
                CampaignTable.HOUSES, CampaignTable.MORAL_COMPASS,
                CampaignTable.KINGMAKER_TESTAMENTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.KINGMAKER_TESTAMENTS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null || day < 0) return;
        for (int claimRow = 0; claimRow < state.throneClaimCount; claimRow++) {
            if (state.kingmakerTestamentIndexForClaim(
                    state.throneClaimId[claimRow]) >= 0) {
                continue;
            }
            int chainRow = qualifyingChainRow(state, claimRow);
            if (chainRow < 0) continue;

            state.sealKingmakerTestament(
                    state.throneClaimId[claimRow],
                    state.throneClaimSourceChainId[claimRow],
                    state.throneClaimHouseId[claimRow],
                    state.chainTarget[chainRow],
                    state.throneClaimSourceFactionId[claimRow],
                    state.throneClaimResultFactionId[claimRow],
                    state.throneClaimMarketId[claimRow],
                    state.throneClaimPlayerContribution[claimRow],
                    state.moralMercy,
                    state.moralIntegrity,
                    state.moralStewardship,
                    state.moralInstitutionalism,
                    state.moralChoiceCount,
                    day);
        }
    }

    private static int qualifyingChainRow(CampaignState state, int claimRow) {
        if (ThroneClaimState.fromByte(state.throneClaimState[claimRow])
                != ThroneClaimState.APPLIED
                || CivilWarPlayerConsequenceState.fromByte(
                    state.throneClaimPlayerConsequenceState[claimRow])
                    != CivilWarPlayerConsequenceState.APPLIED
                || CivilWarAllegiance.fromByte(
                    state.throneClaimPlayerAllegiance[claimRow])
                    != CivilWarAllegiance.CLAIMANT
                || (state.throneClaimPlayerContribution[claimRow] & 0xFFFF)
                    < DECISIVE_CONTRIBUTION
                || state.throneClaimAppliedTick[claimRow] < 0) {
            return -1;
        }

        long sourceChainId = state.throneClaimSourceChainId[claimRow];
        int chainRow = state.chainIndex(sourceChainId);
        if (chainRow < 0
                || ChainArchetype.fromByte(state.chainArchetype[chainRow])
                    != ChainArchetype.CIVIL_WAR
                || ChainState.fromByte(state.chainState[chainRow])
                    != ChainState.RESOLVED
                || state.chainActorHouseId[chainRow]
                    != state.throneClaimHouseId[claimRow]
                || state.chainMarketId[chainRow]
                    != state.throneClaimMarketId[claimRow]
                || state.chainTarget[chainRow] < 0L
                || state.chainTarget[chainRow]
                    == state.throneClaimHouseId[claimRow]
                || state.houseIndex(state.throneClaimHouseId[claimRow]) < 0
                || state.houseIndex(state.chainTarget[chainRow]) < 0
                || !hasClaimantMoralRow(state, sourceChainId,
                    state.throneClaimAppliedTick[claimRow])) {
            return -1;
        }
        return chainRow;
    }

    private static boolean hasClaimantMoralRow(CampaignState state,
                                               long sourceChainId,
                                               int appliedTick) {
        for (int row = 0; row < state.moralChoiceCount; row++) {
            if (MoralChoiceSource.fromByte(state.moralChoiceSourceType[row])
                    == MoralChoiceSource.CIVIL_WAR_CLAIMANT
                    && state.moralChoiceSourceId[row] == sourceChainId
                    && state.moralChoiceHappenedTick[row] == appliedTick) {
                return true;
            }
        }
        return false;
    }
}
