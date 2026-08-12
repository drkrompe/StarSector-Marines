package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.HouseAmbition;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.StakeLedger;

import java.util.EnumSet;

/** Monthly creation pass for location-bound NPC political chains. */
public final class AutonomousChainCreationSystem implements CampaignSystem {

    static final int CADENCE_DAYS = 30;
    static final short CHAIN_THRESHOLD = 45;
    static final byte DISCOVERY_RISK = 32;

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
            if (!eligibleActor(state, houseRow)) continue;
            long actorHouseId = state.houseId[houseRow];
            if (hasActiveChain(state, actorHouseId)) continue;

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

    private static boolean eligibleActor(CampaignState state, int houseRow) {
        return HouseStatus.fromByte(state.houseStatus[houseRow]) == HouseStatus.ACTIVE
                && HouseAmbition.fromByte(state.houseAmbition[houseRow])
                    == HouseAmbition.CONSOLIDATE_STAKE;
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
