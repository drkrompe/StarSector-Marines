package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainDiscovery;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.CivilWarAllegiance;
import com.dillon.starsectormarines.campaign.CivilWarBand;
import com.dillon.starsectormarines.campaign.ContractEligibility;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.StakeLedger;

import java.util.EnumSet;

/** Creates paired, band-specific offers for discovered active civil wars. */
public final class CivilWarParticipationOfferSystem implements CampaignSystem {

    static final int OFFER_DAYS = 7;

    @Override
    public String name() {
        return "CivilWarParticipationOffer";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.CHAINS, CampaignTable.HOUSES,
                CampaignTable.STAKES, CampaignTable.PLAYER_REP,
                CampaignTable.CONTRACTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null) return;
        withdrawStaleOffers(state);
        for (int chainRow = 0; chainRow < state.chainCount; chainRow++) {
            if (!ChainDiscovery.isDiscoveredActive(state, chainRow)
                    || ChainArchetype.fromByte(state.chainArchetype[chainRow])
                        != ChainArchetype.CIVIL_WAR
                    || hasActiveParticipation(state, state.chainId[chainRow])) {
                continue;
            }
            CivilWarBand band = CivilWarBand.forProgress(
                    state.chainProgress[chainRow]);
            int industryId = objectiveIndustry(state, chainRow);
            if (band == CivilWarBand.NONE || industryId < 0) continue;

            CivilWarAllegiance locked = CivilWarAllegiance.fromByte(
                    state.chainPlayerAllegiance[chainRow]);
            if (locked != CivilWarAllegiance.INCUMBENT) {
                createOfferIfAbsent(state, chainRow, band,
                        CivilWarAllegiance.CLAIMANT, industryId, day);
            }
            if (locked != CivilWarAllegiance.CLAIMANT) {
                createOfferIfAbsent(state, chainRow, band,
                        CivilWarAllegiance.INCUMBENT, industryId, day);
            }
        }
    }

    private static void createOfferIfAbsent(CampaignState state, int chainRow,
                                            CivilWarBand band,
                                            CivilWarAllegiance side,
                                            int industryId, int day) {
        long chainId = state.chainId[chainRow];
        if (hasHistoricalOffer(state, chainId, band, side)) return;
        long claimant = state.chainActorHouseId[chainRow];
        long incumbent = state.chainTarget[chainRow];
        long patron = side == CivilWarAllegiance.CLAIMANT ? claimant : incumbent;
        long target = side == CivilWarAllegiance.CLAIMANT ? incumbent : claimant;
        int patronRow = state.houseIndex(patron);
        int targetRow = state.houseIndex(target);
        if (!activeHouse(state, patronRow) || !activeHouse(state, targetRow)
                || !ContractEligibility.patronEligible(state, patron)) {
            return;
        }

        ContractType type = contractType(band, side);
        HouseRank rank = HouseRank.fromByte(state.houseRank[patronRow]);
        ContractOfferTemplate template = ContractOfferTemplate.forType(rank, type);
        if (template == null) return;
        long parentChain = side == CivilWarAllegiance.CLAIMANT ? chainId : -1L;
        long contractId = state.addContract(patron, target, parentChain,
                type, ContractState.OFFERED, day, -1, day + OFFER_DAYS,
                template.phasesTotal, -1, state.chainMarketId[chainRow], industryId,
                template.payout, 0, template.salvageBaseline,
                template.salvageBaseline, (byte) 100);
        int contractRow = state.contractIndex(contractId);
        if (side == CivilWarAllegiance.INCUMBENT) {
            state.contractOpposedChainId[contractRow] = chainId;
        }
        state.contractCivilWarBand[contractRow] = band.toByte();
    }

    static int objectiveIndustry(CampaignState state, int chainRow) {
        if (state == null || chainRow < 0 || chainRow >= state.chainCount) return -1;
        int market = state.chainMarketId[chainRow];
        long claimant = state.chainActorHouseId[chainRow];
        long incumbent = state.chainTarget[chainRow];
        int best = -1;
        boolean bestContested = false;
        int bestCombined = -1;
        for (int industry = 0; industry < state.industryRegistry.size(); industry++) {
            if (state.industryRegistry.get(industry) == null) continue;
            int claimantShare = StakeLedger.shareOf(
                    state, claimant, market, industry);
            int incumbentShare = StakeLedger.shareOf(
                    state, incumbent, market, industry);
            int combined = claimantShare + incumbentShare;
            if (combined <= 0) continue;
            boolean contested = claimantShare > 0 && incumbentShare > 0;
            if (best < 0 || (contested && !bestContested)
                    || (contested == bestContested && combined > bestCombined)) {
                best = industry;
                bestContested = contested;
                bestCombined = combined;
            }
        }
        return best;
    }

    private static ContractType contractType(CivilWarBand band,
                                             CivilWarAllegiance side) {
        switch (band) {
            case COALITION_BUILDING:
                return side == CivilWarAllegiance.CLAIMANT
                        ? ContractType.ESCORT : ContractType.STRIKE;
            case MOBILIZATION:
                return side == CivilWarAllegiance.CLAIMANT
                        ? ContractType.CADRE : ContractType.GARRISON;
            case OPEN_CONFLICT:
                return ContractType.PLANETARY_ASSAULT;
            case NONE:
            default:
                throw new IllegalArgumentException("band");
        }
    }

    private static boolean hasHistoricalOffer(CampaignState state, long chainId,
                                              CivilWarBand band,
                                              CivilWarAllegiance side) {
        for (int row = 0; row < state.contractCount; row++) {
            if (CivilWarBand.fromByte(state.contractCivilWarBand[row]) != band) continue;
            if (side == CivilWarAllegiance.CLAIMANT
                    && state.contractChainId[row] == chainId
                    && state.contractOpposedChainId[row] < 0L) {
                return true;
            }
            if (side == CivilWarAllegiance.INCUMBENT
                    && state.contractOpposedChainId[row] == chainId
                    && state.contractChainId[row] < 0L) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasActiveParticipation(CampaignState state, long chainId) {
        for (int row = 0; row < state.contractCount; row++) {
            ContractState contractState = ContractState.fromByte(state.contractState[row]);
            if (contractState != ContractState.ACTIVE
                    && contractState != ContractState.IN_PROGRESS) {
                continue;
            }
            if (CivilWarBand.fromByte(state.contractCivilWarBand[row]) == CivilWarBand.NONE) {
                continue;
            }
            if (state.contractChainId[row] == chainId
                    || state.contractOpposedChainId[row] == chainId) {
                return true;
            }
        }
        return false;
    }

    private static void withdrawStaleOffers(CampaignState state) {
        for (int row = 0; row < state.contractCount; row++) {
            CivilWarBand offeredBand = CivilWarBand.fromByte(
                    state.contractCivilWarBand[row]);
            if (offeredBand == CivilWarBand.NONE
                    || ContractState.fromByte(state.contractState[row])
                        != ContractState.OFFERED) {
                continue;
            }
            long chainId = state.contractChainId[row] >= 0L
                    ? state.contractChainId[row] : state.contractOpposedChainId[row];
            int chainRow = state.chainIndex(chainId);
            CivilWarAllegiance side = state.contractChainId[row] >= 0L
                    ? CivilWarAllegiance.CLAIMANT : CivilWarAllegiance.INCUMBENT;
            if (chainRow < 0
                    || ChainState.fromByte(state.chainState[chainRow])
                        != ChainState.ACTIVE
                    || CivilWarBand.forProgress(state.chainProgress[chainRow]) != offeredBand
                    || allegianceConflicts(state, chainRow, side)) {
                state.contractState[row] = ContractState.EXPIRED.toByte();
            }
        }
    }

    private static boolean allegianceConflicts(CampaignState state, int chainRow,
                                               CivilWarAllegiance side) {
        CivilWarAllegiance locked = CivilWarAllegiance.fromByte(
                state.chainPlayerAllegiance[chainRow]);
        return locked != CivilWarAllegiance.NONE && locked != side;
    }

    private static boolean activeHouse(CampaignState state, int row) {
        return row >= 0 && HouseStatus.fromByte(state.houseStatus[row])
                == HouseStatus.ACTIVE;
    }
}
