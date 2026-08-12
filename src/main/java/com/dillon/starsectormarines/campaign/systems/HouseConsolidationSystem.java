package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.HouseStatus;

import java.util.EnumSet;

/** Transitions politically exhausted houses to stable-id DORMANT tombstones. */
public final class HouseConsolidationSystem implements CampaignSystem {

    @Override
    public String name() {
        return "HouseConsolidation";
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
            if (HouseStatus.fromByte(state.houseStatus[houseRow]) != HouseStatus.ACTIVE) {
                continue;
            }
            long houseId = state.houseId[houseRow];
            boolean hasHistory = false;
            boolean hasPositiveStake = false;
            for (int stakeRow = 0; stakeRow < state.stakeCount; stakeRow++) {
                if (state.stakeHouseId[stakeRow] != houseId) continue;
                hasHistory = true;
                if (state.stakeShare[stakeRow] > 0) {
                    hasPositiveStake = true;
                    break;
                }
            }
            if (hasHistory && !hasPositiveStake) {
                state.houseStatus[houseRow] = HouseStatus.DORMANT.toByte();
            }
        }
    }
}
