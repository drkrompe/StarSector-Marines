package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.HouseAmbition;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;

import java.util.EnumSet;

/** Assigns the minimal persisted ambition needed by the horizontal drift loop. */
public final class HouseAmbitionSystem implements CampaignSystem {

    static final int REVIEW_CADENCE_DAYS = 30;
    static final int PROMOTE_PROGRESS_PERCENT = 75;
    static final int CLAIM_THRONE_PROGRESS_PERCENT = 75;

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
            if (ambition == HouseAmbition.NONE) {
                int industryId = strongestHomeIndustry(state, houseRow);
                if (industryId >= 0) {
                    state.houseAmbition[houseRow] = HouseAmbition.CONSOLIDATE_STAKE.toByte();
                    state.houseAmbitionTarget[houseRow] = industryId;
                    ambition = HouseAmbition.CONSOLIDATE_STAKE;
                }
            }
            if (!reviewDue(state.houseLastAmbitionReviewTick[houseRow], day)) continue;
            state.houseLastAmbitionReviewTick[houseRow] = day;
            if (ambition == HouseAmbition.PROMOTE
                    && reachedPromotionTarget(state, houseRow)) {
                clearAmbition(state, houseRow);
                int industryId = strongestHomeIndustry(state, houseRow);
                if (industryId >= 0) {
                    state.houseAmbition[houseRow] = HouseAmbition.CONSOLIDATE_STAKE.toByte();
                    state.houseAmbitionTarget[houseRow] = industryId;
                    ambition = HouseAmbition.CONSOLIDATE_STAKE;
                } else {
                    ambition = HouseAmbition.NONE;
                }
            }
            if (ambition == HouseAmbition.CONSOLIDATE_STAKE
                    && readyToPromote(state, houseRow)) {
                HouseRank rank = HouseRank.fromByte(state.houseRank[houseRow]);
                state.houseAmbition[houseRow] = HouseAmbition.PROMOTE.toByte();
                state.houseAmbitionTarget[houseRow] = rank.next().ordinal();
            } else if (ambition == HouseAmbition.CONSOLIDATE_STAKE
                    && readyToClaimThrone(state, houseRow)) {
                state.houseAmbition[houseRow] = HouseAmbition.CLAIM_THRONE.toByte();
                state.houseAmbitionTarget[houseRow] = state.houseFactionId[houseRow];
            }
        }
    }

    private static boolean reviewDue(int lastReviewTick, int day) {
        return lastReviewTick < 0 || day < lastReviewTick
                || day - lastReviewTick >= REVIEW_CADENCE_DAYS;
    }

    static boolean readyToPromote(CampaignState state, int houseRow) {
        HouseRank rank = HouseRank.fromByte(state.houseRank[houseRow]);
        if (rank != HouseRank.TIER_1 && rank != HouseRank.TIER_2) return false;
        int progressFloor = (rank.promotionThreshold * PROMOTE_PROGRESS_PERCENT + 99) / 100;
        return state.housePromotionProgress[houseRow] >= progressFloor
                && state.housePower[houseRow] >= rank.promotionThreshold;
    }

    private static boolean reachedPromotionTarget(CampaignState state, int houseRow) {
        long target = state.houseAmbitionTarget[houseRow];
        if (target < HouseRank.TIER_1.ordinal() || target > HouseRank.TIER_4.ordinal()) {
            return false;
        }
        HouseRank rank = HouseRank.fromByte(state.houseRank[houseRow]);
        return rank.ordinal() >= target;
    }

    static boolean readyToClaimThrone(CampaignState state, int houseRow) {
        HouseRank rank = HouseRank.fromByte(state.houseRank[houseRow]);
        if (rank != HouseRank.TIER_3) return false;
        int progressFloor = (rank.promotionThreshold * CLAIM_THRONE_PROGRESS_PERCENT + 99) / 100;
        return state.housePromotionProgress[houseRow] >= progressFloor
                && state.housePower[houseRow] >= rank.promotionThreshold;
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
