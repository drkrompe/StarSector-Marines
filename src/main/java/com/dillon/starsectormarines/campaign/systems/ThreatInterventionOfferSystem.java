package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ChainDiscovery;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.ContractEligibility;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;

import java.util.EnumSet;

/** Creates and withdraws threatened-house Strike offers tied to learned plots. */
public final class ThreatInterventionOfferSystem implements CampaignSystem {

    static final int MAX_OFFER_DAYS = 7;

    @Override
    public String name() {
        return "ThreatInterventionOffer";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.CHAINS, CampaignTable.HOUSES,
                CampaignTable.PLAYER_REP, CampaignTable.CONTRACTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null) return;
        withdrawMootOffers(state);
        for (int chainRow = 0; chainRow < state.chainCount; chainRow++) {
            if (!ChainDiscovery.isDiscoveredActive(state, chainRow)
                    || ChainArchetype.fromByte(state.chainArchetype[chainRow])
                        == ChainArchetype.CIVIL_WAR
                    || hasIntervention(state, state.chainId[chainRow])) {
                continue;
            }
            createOffer(state, chainRow, day);
        }
    }

    private static void createOffer(CampaignState state, int chainRow, int day) {
        long patronId = state.chainTarget[chainRow];
        long targetId = state.chainActorHouseId[chainRow];
        int patronRow = state.houseIndex(patronId);
        int targetRow = state.houseIndex(targetId);
        if (!activeHouse(state, patronRow) || !activeHouse(state, targetRow)
                || !ContractEligibility.patronEligible(state, patronId)) {
            return;
        }

        HouseRank patronRank = HouseRank.fromByte(state.houseRank[patronRow]);
        ContractOfferTemplate template = ContractOfferTemplate.forType(
                patronRank, ContractType.STRIKE);
        if (template == null) return;

        int remaining = Math.max(1,
                state.chainThreshold[chainRow] - state.chainProgress[chainRow]);
        int offerDays = Math.max(1, Math.min(MAX_OFFER_DAYS, remaining - 1));
        long contractId = state.addContract(patronId, targetId, -1L,
                ContractType.STRIKE, ContractState.OFFERED,
                day, -1, day + offerDays, template.phasesTotal, -1,
                state.chainMarketId[chainRow], state.chainIndustryId[chainRow],
                template.payout, 0, template.salvageBaseline,
                template.salvageBaseline, (byte) 100);
        int contractRow = state.contractIndex(contractId);
        state.contractOpposedChainId[contractRow] = state.chainId[chainRow];
    }

    private static void withdrawMootOffers(CampaignState state) {
        for (int contractRow = 0; contractRow < state.contractCount; contractRow++) {
            if (state.contractOpposedChainId[contractRow] < 0L
                    || ContractState.fromByte(state.contractState[contractRow])
                        != ContractState.OFFERED) {
                continue;
            }
            int chainRow = state.chainIndex(state.contractOpposedChainId[contractRow]);
            if (chainRow < 0
                    || ChainState.fromByte(state.chainState[chainRow]) != ChainState.ACTIVE) {
                state.contractState[contractRow] = ContractState.EXPIRED.toByte();
            }
        }
    }

    private static boolean hasIntervention(CampaignState state, long chainId) {
        for (int row = 0; row < state.contractCount; row++) {
            if (state.contractOpposedChainId[row] == chainId) return true;
        }
        return false;
    }

    private static boolean activeHouse(CampaignState state, int houseRow) {
        return houseRow >= 0
                && HouseStatus.fromByte(state.houseStatus[houseRow]) == HouseStatus.ACTIVE;
    }
}
