package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.HouseAmbition;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.StakeLedger;

import java.util.EnumSet;

/** Monthly creation pass for location-bound NPC political chains. */
public final class AutonomousChainCreationSystem implements CampaignSystem {

    static final int CADENCE_DAYS = 30;
    static final short CHAIN_THRESHOLD = 45;
    static final byte DISCOVERY_RISK = 32;
    static final short PROMOTION_CHAIN_THRESHOLD = 60;
    static final byte PROMOTION_DISCOVERY_RISK = 64;
    static final short CIVIL_WAR_CHAIN_THRESHOLD = 180;
    static final byte CIVIL_WAR_DISCOVERY_RISK = (byte) 128;

    @Override
    public String name() {
        return "AutonomousChainCreation";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.HOUSES, CampaignTable.STAKES,
                CampaignTable.CHAINS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CHAINS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null || Math.floorMod(day, CADENCE_DAYS) != 0) return;
        for (int houseRow = 0; houseRow < state.houseCount; houseRow++) {
            if (HouseStatus.fromByte(state.houseStatus[houseRow]) != HouseStatus.ACTIVE) {
                continue;
            }
            long actorHouseId = state.houseId[houseRow];
            if (hasActiveChain(state, actorHouseId)) continue;

            HouseAmbition ambition = HouseAmbition.fromByte(state.houseAmbition[houseRow]);
            if (ambition == HouseAmbition.CLAIM_THRONE) {
                createCivilWarChain(state, houseRow, day);
                continue;
            }
            if (ambition == HouseAmbition.PROMOTE) {
                createPromotionChain(state, houseRow, day);
                continue;
            }
            if (ambition != HouseAmbition.CONSOLIDATE_STAKE) continue;
            int marketId = state.houseMarketId[houseRow];
            int industryId = ambitionIndustry(state.houseAmbitionTarget[houseRow]);
            if (industryId < 0
                    || StakeLedger.shareOf(state, actorHouseId, marketId, industryId) <= 0) {
                continue;
            }
            long targetHouseId = strongestRival(state, actorHouseId, marketId, industryId);
            if (targetHouseId < 0L) continue;

            state.addAutonomousChain(actorHouseId, targetHouseId, marketId, industryId,
                    state.houseRank[houseRow], ChainArchetype.CONSOLIDATE_STAKE,
                    CHAIN_THRESHOLD, DISCOVERY_RISK, day);
        }
    }

    private static void createCivilWarChain(CampaignState state, int actorRow, int day) {
        if (HouseRank.fromByte(state.houseRank[actorRow]) != HouseRank.TIER_3
                || state.housePromotionProgress[actorRow]
                    < HouseRank.TIER_3.promotionThreshold
                || state.houseAmbitionTarget[actorRow] != state.houseFactionId[actorRow]) {
            return;
        }
        long targetHouseId = strongestFactionRival(state, actorRow);
        if (targetHouseId < 0L) return;
        state.addAutonomousChain(state.houseId[actorRow], targetHouseId,
                state.houseMarketId[actorRow], -1, state.houseRank[actorRow],
                ChainArchetype.CIVIL_WAR, CIVIL_WAR_CHAIN_THRESHOLD,
                CIVIL_WAR_DISCOVERY_RISK, day);
    }

    static long strongestFactionRival(CampaignState state, int actorRow) {
        long actorHouseId = state.houseId[actorRow];
        int factionId = state.houseFactionId[actorRow];
        long bestHouseId = -1L;
        int bestPower = -1;
        for (int candidateRow = 0; candidateRow < state.houseCount; candidateRow++) {
            long candidateId = state.houseId[candidateRow];
            if (candidateId == actorHouseId
                    || state.houseFactionId[candidateRow] != factionId
                    || HouseStatus.fromByte(state.houseStatus[candidateRow])
                        != HouseStatus.ACTIVE) {
                continue;
            }
            int power = Math.max(0, state.housePower[candidateRow]);
            if (power > bestPower || (power == bestPower
                    && (bestHouseId < 0L || candidateId < bestHouseId))) {
                bestPower = power;
                bestHouseId = candidateId;
            }
        }
        return bestHouseId;
    }

    private static void createPromotionChain(CampaignState state, int actorRow, int day) {
        HouseRank rank = HouseRank.fromByte(state.houseRank[actorRow]);
        if ((rank != HouseRank.TIER_1 && rank != HouseRank.TIER_2)
                || state.houseAmbitionTarget[actorRow] != rank.next().ordinal()
                || AutonomousPromotionSystem.stakeBasedDelta(state, actorRow) > 0) {
            return;
        }
        long actorHouseId = state.houseId[actorRow];
        int marketId = state.houseMarketId[actorRow];
        int industryId = promotionIndustry(state, actorRow);
        if (industryId < 0) return;
        long targetHouseId = strongestSameFactionRival(state, actorRow, industryId);
        if (targetHouseId < 0L) return;

        state.addAutonomousChain(actorHouseId, targetHouseId, marketId, industryId,
                state.houseRank[actorRow], ChainArchetype.PROMOTE,
                PROMOTION_CHAIN_THRESHOLD, PROMOTION_DISCOVERY_RISK, day);
    }

    static int promotionIndustry(CampaignState state, int actorRow) {
        long actorHouseId = state.houseId[actorRow];
        int marketId = state.houseMarketId[actorRow];
        int factionId = state.houseFactionId[actorRow];
        int bestIndustryId = -1;
        long bestRivalId = -1L;
        int bestRivalShare = 0;
        for (int actorStakeRow = 0; actorStakeRow < state.stakeCount; actorStakeRow++) {
            if (state.stakeHouseId[actorStakeRow] != actorHouseId
                    || state.stakeMarketId[actorStakeRow] != marketId
                    || state.stakeShare[actorStakeRow] <= 0) {
                continue;
            }
            int industryId = state.stakeIndustryId[actorStakeRow];
            for (int rivalStakeRow = 0; rivalStakeRow < state.stakeCount; rivalStakeRow++) {
                if (state.stakeMarketId[rivalStakeRow] != marketId
                        || state.stakeIndustryId[rivalStakeRow] != industryId
                        || state.stakeHouseId[rivalStakeRow] == actorHouseId) {
                    continue;
                }
                long rivalId = state.stakeHouseId[rivalStakeRow];
                int rivalRow = state.houseIndex(rivalId);
                int rivalShare = Math.max(0, state.stakeShare[rivalStakeRow]);
                if (rivalRow < 0 || state.houseFactionId[rivalRow] != factionId
                        || HouseStatus.fromByte(state.houseStatus[rivalRow])
                            != HouseStatus.ACTIVE
                        || rivalShare <= 0) {
                    continue;
                }
                if (rivalShare > bestRivalShare
                        || (rivalShare == bestRivalShare
                            && (bestIndustryId < 0 || industryId < bestIndustryId))
                        || (rivalShare == bestRivalShare && industryId == bestIndustryId
                            && (bestRivalId < 0L || rivalId < bestRivalId))) {
                    bestRivalShare = rivalShare;
                    bestIndustryId = industryId;
                    bestRivalId = rivalId;
                }
            }
        }
        return bestIndustryId;
    }

    private static long strongestSameFactionRival(CampaignState state, int actorRow,
                                                   int industryId) {
        long actorHouseId = state.houseId[actorRow];
        int marketId = state.houseMarketId[actorRow];
        int factionId = state.houseFactionId[actorRow];
        long bestHouseId = -1L;
        int bestShare = 0;
        for (int stakeRow = 0; stakeRow < state.stakeCount; stakeRow++) {
            if (state.stakeMarketId[stakeRow] != marketId
                    || state.stakeIndustryId[stakeRow] != industryId
                    || state.stakeHouseId[stakeRow] == actorHouseId) {
                continue;
            }
            long candidateId = state.stakeHouseId[stakeRow];
            int candidateRow = state.houseIndex(candidateId);
            int share = Math.max(0, state.stakeShare[stakeRow]);
            if (candidateRow < 0 || state.houseFactionId[candidateRow] != factionId
                    || HouseStatus.fromByte(state.houseStatus[candidateRow])
                        != HouseStatus.ACTIVE
                    || share <= 0) {
                continue;
            }
            if (share > bestShare || (share == bestShare
                    && (bestHouseId < 0L || candidateId < bestHouseId))) {
                bestShare = share;
                bestHouseId = candidateId;
            }
        }
        return bestHouseId;
    }

    static long strongestRival(CampaignState state, long actorHouseId,
                                int marketId, int industryId) {
        long bestHouseId = -1L;
        int bestShare = 0;
        for (int stakeRow = 0; stakeRow < state.stakeCount; stakeRow++) {
            if (state.stakeMarketId[stakeRow] != marketId
                    || state.stakeIndustryId[stakeRow] != industryId
                    || state.stakeHouseId[stakeRow] == actorHouseId) {
                continue;
            }
            long candidateId = state.stakeHouseId[stakeRow];
            int candidateRow = state.houseIndex(candidateId);
            int share = Math.max(0, state.stakeShare[stakeRow]);
            if (candidateRow < 0
                    || HouseStatus.fromByte(state.houseStatus[candidateRow])
                        != HouseStatus.ACTIVE
                    || share <= 0) {
                continue;
            }
            if (share > bestShare || (share == bestShare
                    && (bestHouseId < 0L || candidateId < bestHouseId))) {
                bestShare = share;
                bestHouseId = candidateId;
            }
        }
        return bestHouseId;
    }

    private static boolean hasActiveChain(CampaignState state, long actorHouseId) {
        for (int chainRow = 0; chainRow < state.chainCount; chainRow++) {
            if (state.chainActorHouseId[chainRow] == actorHouseId
                    && ChainState.fromByte(state.chainState[chainRow]) == ChainState.ACTIVE) {
                return true;
            }
        }
        return false;
    }

    private static int ambitionIndustry(long target) {
        return target >= 0L && target <= Integer.MAX_VALUE ? (int) target : -1;
    }
}
