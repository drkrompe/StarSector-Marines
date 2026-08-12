package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.HouseAmbition;
import com.dillon.starsectormarines.campaign.HouseStatus;

import java.util.EnumSet;

/** Assigns the minimal persisted ambition needed by the horizontal drift loop. */
public final class HouseAmbitionSystem implements CampaignSystem {

    @Override
    public String name() {
        return "HouseAmbition";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.HOUSES, CampaignTable.STAKES);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.HOUSES);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null) return;
        for (int houseRow = 0; houseRow < state.houseCount; houseRow++) {
            HouseStatus status = HouseStatus.fromByte(state.houseStatus[houseRow]);
            HouseAmbition ambition = HouseAmbition.fromByte(state.houseAmbition[houseRow]);
            if (status == HouseStatus.DORMANT) {
                if (ambition == HouseAmbition.CONSOLIDATE_STAKE) clearAmbition(state, houseRow);
                continue;
            }
            if (status != HouseStatus.ACTIVE) continue;
            if (ambition == HouseAmbition.CONSOLIDATE_STAKE
                    && !hasTargetedHomeStake(state, houseRow)) {
                clearAmbition(state, houseRow);
                ambition = HouseAmbition.NONE;
            }
            if (ambition != HouseAmbition.NONE) {
                continue;
            }
            int industryId = strongestHomeIndustry(state, houseRow);
            if (industryId < 0) continue;
            state.houseAmbition[houseRow] = HouseAmbition.CONSOLIDATE_STAKE.toByte();
            state.houseAmbitionTarget[houseRow] = industryId;
        }
    }

    private static boolean hasTargetedHomeStake(CampaignState state, int houseRow) {
        long target = state.houseAmbitionTarget[houseRow];
        if (target < 0L || target > Integer.MAX_VALUE) return false;
        long houseId = state.houseId[houseRow];
        int marketId = state.houseMarketId[houseRow];
        int industryId = (int) target;
        for (int stakeRow = 0; stakeRow < state.stakeCount; stakeRow++) {
            if (state.stakeHouseId[stakeRow] == houseId
                    && state.stakeMarketId[stakeRow] == marketId
                    && state.stakeIndustryId[stakeRow] == industryId
                    && state.stakeShare[stakeRow] > 0) {
                return true;
            }
        }
        return false;
    }

    private static void clearAmbition(CampaignState state, int houseRow) {
        state.houseAmbition[houseRow] = HouseAmbition.NONE.toByte();
        state.houseAmbitionTarget[houseRow] = -1L;
    }

    static int strongestHomeIndustry(CampaignState state, int houseRow) {
        if (state == null || houseRow < 0 || houseRow >= state.houseCount) return -1;
        long houseId = state.houseId[houseRow];
        int homeMarket = state.houseMarketId[houseRow];
        int bestIndustry = -1;
        int bestShare = 0;
        for (int stakeRow = 0; stakeRow < state.stakeCount; stakeRow++) {
            if (state.stakeHouseId[stakeRow] != houseId
                    || state.stakeMarketId[stakeRow] != homeMarket) {
                continue;
            }
            int share = Math.max(0, state.stakeShare[stakeRow]);
            int industry = state.stakeIndustryId[stakeRow];
            if (share > bestShare
                    || (share == bestShare && share > 0
                        && (bestIndustry < 0 || industry < bestIndustry))) {
                bestShare = share;
                bestIndustry = industry;
            }
        }
        return bestIndustry;
    }
}
