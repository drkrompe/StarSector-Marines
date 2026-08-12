package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.HouseAmbition;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.StakeLedger;

import java.util.EnumSet;

/** Weekly, deterministic share creep among ambitious same-market houses. */
public final class StakeDriftSystem implements CampaignSystem {

    static final int CADENCE_DAYS = 7;
    static final int MIN_DRIFT = 3;
    static final int MAX_DRIFT = 5;

    @Override
    public String name() {
        return "StakeDrift";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.HOUSES, CampaignTable.STAKES);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.HOUSES, CampaignTable.STAKES);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null || Math.floorMod(day, CADENCE_DAYS) != 0) return;
        for (int houseRow = 0; houseRow < state.houseCount; houseRow++) {
            if (state.houseLastDriftTick[houseRow] == day
                    || HouseStatus.fromByte(state.houseStatus[houseRow])
                        != HouseStatus.ACTIVE
                    || HouseAmbition.fromByte(state.houseAmbition[houseRow])
                        != HouseAmbition.CONSOLIDATE_STAKE) {
                continue;
            }
            state.houseLastDriftTick[houseRow] = day;
            int industryId = ambitionIndustry(state.houseAmbitionTarget[houseRow]);
            if (industryId < 0) continue;
            int marketId = state.houseMarketId[houseRow];
            long houseId = state.houseId[houseRow];
            int held = StakeLedger.shareOf(state, houseId, marketId, industryId);
            if (held <= 0) continue;
            long rivalId = strongestWeakerRival(
                    state, houseRow, marketId, industryId, held);
            StakeLedger.seizeShare(state, rivalId, houseId, marketId, industryId,
                    driftAmount(houseId, industryId, day));
        }
    }

    static long strongestWeakerRival(CampaignState state, int houseRow,
                                     int marketId, int industryId, int held) {
        long winnerId = state.houseId[houseRow];
        long bestId = -1L;
        int bestShare = 0;
        for (int stakeRow = 0; stakeRow < state.stakeCount; stakeRow++) {
            if (state.stakeMarketId[stakeRow] != marketId
                    || state.stakeIndustryId[stakeRow] != industryId
                    || state.stakeHouseId[stakeRow] == winnerId) {
                continue;
            }
            long candidateId = state.stakeHouseId[stakeRow];
            int candidateRow = state.houseIndex(candidateId);
            int share = Math.max(0, state.stakeShare[stakeRow]);
            if (candidateRow < 0
                    || HouseStatus.fromByte(state.houseStatus[candidateRow])
                        != HouseStatus.ACTIVE
                    || share <= 0 || share >= held) {
                continue;
            }
            if (share > bestShare || (share == bestShare
                    && (bestId < 0L || candidateId < bestId))) {
                bestShare = share;
                bestId = candidateId;
            }
        }
        return bestId;
    }

    static int driftAmount(long houseId, int industryId, int day) {
        long mixed = houseId * 0x9e3779b97f4a7c15L;
        mixed ^= (long) industryId * 0xbf58476d1ce4e5b9L;
        mixed ^= (long) day * 0x94d049bb133111ebL;
        return MIN_DRIFT + Math.floorMod(mixed, MAX_DRIFT - MIN_DRIFT + 1);
    }

    private static int ambitionIndustry(long target) {
        return target >= 0L && target <= Integer.MAX_VALUE ? (int) target : -1;
    }
}
