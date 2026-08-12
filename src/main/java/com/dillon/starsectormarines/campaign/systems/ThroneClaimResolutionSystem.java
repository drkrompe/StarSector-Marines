package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.HouseAmbition;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.ThroneClaimState;
import com.dillon.starsectormarines.campaign.ThroneClaimWriteback;

import java.util.EnumSet;

/** Consumes prepared throne claims behind the sole vanilla-writeback port. */
public final class ThroneClaimResolutionSystem implements CampaignSystem {

    private final ThroneClaimWriteback writeback;

    public ThroneClaimResolutionSystem(ThroneClaimWriteback writeback) {
        if (writeback == null) throw new IllegalArgumentException("writeback");
        this.writeback = writeback;
    }

    @Override
    public String name() {
        return "ThroneClaimResolution";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.THRONE_CLAIMS, CampaignTable.CHAINS,
                CampaignTable.HOUSES);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.THRONE_CLAIMS, CampaignTable.HOUSES);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null) return;
        for (int claimRow = 0; claimRow < state.throneClaimCount; claimRow++) {
            if (ThroneClaimState.fromByte(state.throneClaimState[claimRow])
                    != ThroneClaimState.PREPARED) {
                continue;
            }
            int houseRow = state.houseIndex(state.throneClaimHouseId[claimRow]);
            int chainRow = state.chainIndex(state.throneClaimSourceChainId[claimRow]);
            if (!validPreparedClaim(state, claimRow, houseRow, chainRow)) {
                fail(state, claimRow, day);
                continue;
            }
            String sourceFactionId = state.factionRegistry.get(
                    state.throneClaimSourceFactionId[claimRow]);
            String resultFactionId = state.factionRegistry.get(
                    state.throneClaimResultFactionId[claimRow]);
            String marketId = state.marketRegistry.get(state.throneClaimMarketId[claimRow]);
            if (sourceFactionId == null || resultFactionId == null || marketId == null
                    || sourceFactionId.equals(resultFactionId)) {
                fail(state, claimRow, day);
                continue;
            }

            ThroneClaimWriteback.Result result;
            try {
                result = writeback.apply(sourceFactionId, resultFactionId, marketId);
            } catch (RuntimeException ignored) {
                continue; // external state may be transient; retry next daily tick
            }
            if (result == ThroneClaimWriteback.Result.RETRY || result == null) continue;
            if (result == ThroneClaimWriteback.Result.REJECTED) {
                fail(state, claimRow, day);
                continue;
            }
            applyLocalResult(state, claimRow, houseRow, day);
        }
    }

    private static boolean validPreparedClaim(CampaignState state, int claimRow,
                                               int houseRow, int chainRow) {
        if (houseRow < 0 || chainRow < 0
                || HouseStatus.fromByte(state.houseStatus[houseRow])
                    != HouseStatus.ACTIVE
                || ChainArchetype.fromByte(state.chainArchetype[chainRow])
                    != ChainArchetype.CIVIL_WAR
                || ChainState.fromByte(state.chainState[chainRow])
                    != ChainState.RESOLVED
                || state.chainId[chainRow] != state.throneClaimSourceChainId[claimRow]
                || state.chainActorHouseId[chainRow] != state.houseId[houseRow]
                || state.chainMarketId[chainRow] != state.throneClaimMarketId[claimRow]) {
            return false;
        }
        HouseRank rank = HouseRank.fromByte(state.houseRank[houseRow]);
        int currentFaction = state.houseFactionId[houseRow];
        boolean awaiting = rank == HouseRank.TIER_3
                && state.housePromotionProgress[houseRow]
                    >= HouseRank.TIER_3.promotionThreshold
                && currentFaction == state.throneClaimSourceFactionId[claimRow];
        boolean locallyFinalized = rank == HouseRank.TIER_4
                && currentFaction == state.throneClaimResultFactionId[claimRow];
        return awaiting || locallyFinalized;
    }

    private static void applyLocalResult(CampaignState state, int claimRow,
                                         int houseRow, int day) {
        state.houseRank[houseRow] = HouseRank.TIER_4.toByte();
        state.housePromotionProgress[houseRow] = 0;
        state.houseFactionId[houseRow] = state.throneClaimResultFactionId[claimRow];
        state.houseAmbition[houseRow] = HouseAmbition.NONE.toByte();
        state.houseAmbitionTarget[houseRow] = -1L;
        state.throneClaimState[claimRow] = ThroneClaimState.APPLIED.toByte();
        state.throneClaimAppliedTick[claimRow] = day;
    }

    private static void fail(CampaignState state, int claimRow, int day) {
        state.throneClaimState[claimRow] = ThroneClaimState.FAILED.toByte();
        state.throneClaimAppliedTick[claimRow] = day;
    }
}
