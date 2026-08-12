package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;

import java.util.Arrays;
import java.util.EnumSet;

/** Rebuilds each house's cached power as the sum of its current stake shares. */
public final class HousePowerSystem implements CampaignSystem {

    @Override
    public String name() {
        return "HousePower";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.STAKES);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.HOUSES);
    }

    @Override
    public void tick(CampaignState state, int day) {
        Arrays.fill(state.housePower, 0, state.houseCount, 0);
        for (int stakeRow = 0; stakeRow < state.stakeCount; stakeRow++) {
            int houseRow = state.houseIndex(state.stakeHouseId[stakeRow]);
            if (houseRow < 0) continue;
            int share = Math.max(0, state.stakeShare[stakeRow]);
            long power = (long) state.housePower[houseRow] + share;
            state.housePower[houseRow] = power > Integer.MAX_VALUE
                    ? Integer.MAX_VALUE : (int) power;
        }
    }
}
