package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.HousePromotion;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;

import java.util.EnumSet;

/**
 * Tick phase 1: autonomous house promotion via stake-based progress.
 *
 * <p>Per <code>mechanics.md</code>: every ACTIVE house accrues
 * {@code promotionProgress} based on its current stake holdings vs the
 * market's total. Crossing {@code rankThreshold} promotes the house.
 *
 * <p>The autonomous rate is intentionally glacial: a house receives one point
 * per day only while it owns a strict majority of all claimed stake on its
 * home market. T1 therefore needs about 100 uninterrupted days to reach T2;
 * player-backed contract progress remains the decisive accelerant.
 */
public final class AutonomousPromotionSystem implements CampaignSystem {

    @Override
    public String name() {
        return "AutonomousPromotion";
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
                    || HouseRank.fromByte(state.houseRank[houseRow]) == HouseRank.TIER_4) {
                continue;
            }
            int delta = stakeBasedDelta(state, houseRow);
            if (delta > 0) {
                HousePromotion.addProgressAndPromote(state, houseRow, delta, day);
            }
        }
    }

    static int stakeBasedDelta(CampaignState state, int houseRow) {
        if (state == null || houseRow < 0 || houseRow >= state.houseCount) return 0;
        long houseId = state.houseId[houseRow];
        int homeMarket = state.houseMarketId[houseRow];
        long held = 0L;
        long claimed = 0L;
        for (int stakeRow = 0; stakeRow < state.stakeCount; stakeRow++) {
            if (state.stakeMarketId[stakeRow] != homeMarket) continue;
            int share = Math.max(0, state.stakeShare[stakeRow]);
            claimed += share;
            if (state.stakeHouseId[stakeRow] == houseId) held += share;
        }
        return claimed > 0L && held * 2L > claimed ? 1 : 0;
    }
}
