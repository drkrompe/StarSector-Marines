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
            if (HouseStatus.fromByte(state.houseStatus[houseRow]) != HouseStatus.ACTIVE
                    || HouseAmbition.fromByte(state.houseAmbition[houseRow])
                        != HouseAmbition.NONE) {
                continue;
            }
            int industryId = strongestHomeIndustry(state, houseRow);
            if (industryId < 0) continue;
            state.houseAmbition[houseRow] = HouseAmbition.CONSOLIDATE_STAKE.toByte();
            state.houseAmbitionTarget[houseRow] = industryId;
        }
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
