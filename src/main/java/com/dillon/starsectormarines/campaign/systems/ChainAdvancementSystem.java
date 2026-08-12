package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.HousePromotion;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.StakeLedger;

import java.util.EnumSet;

/**
 * Tick phase 3: advance active autonomous chains.
 *
 * <p>Per <code>mechanics.md</code>: autonomous chains ({@code patron == -1})
 * advance on tick; player chains advance only on mission completion (the
 * mission resolver pokes them directly). This system handles the autonomous
 * side. Terminal state and resolution day provide the persisted seam consumed
 * by the later Chronicle/discovery pass.
 */
public final class ChainAdvancementSystem implements CampaignSystem {

    static final int DAILY_PROGRESS = 1;
    static final int RESOLUTION_STAKE_SEIZE = 40;
    static final int RESOLUTION_PROMOTION_PROGRESS = 30;
    static final int PROMOTION_STAKE_SEIZE = 20;
    static final int PROMOTION_PROGRESS_GAIN = 90;
    static final int PROMOTION_RIVAL_SUPPRESSION = 30;

    @Override
    public String name() {
        return "ChainAdvancement";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.HOUSES, CampaignTable.STAKES,
                CampaignTable.CHAINS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CHAINS, CampaignTable.HOUSES,
                CampaignTable.STAKES);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null) return;
        for (int chainRow = 0; chainRow < state.chainCount; chainRow++) {
            if (state.chainPatron[chainRow] != -1L
                    || ChainState.fromByte(state.chainState[chainRow]) != ChainState.ACTIVE
                    || state.chainLastAdvanceTick[chainRow] == day) {
                continue;
            }

            long actorHouseId = state.chainActorHouseId[chainRow];
            if (actorHouseId < 0L) continue; // legacy autonomous row: actor unrecoverable
            int actorRow = state.houseIndex(actorHouseId);
            int targetRow = state.houseIndex(state.chainTarget[chainRow]);
            if (!activeHouse(state, actorRow) || !activeHouse(state, targetRow)
                    || state.chainMarketId[chainRow] < 0
                    || state.chainIndustryId[chainRow] < 0) {
                terminate(state, chainRow, ChainState.FAILED, day);
                continue;
            }
            ChainArchetype archetype = ChainArchetype.fromByte(
                    state.chainArchetype[chainRow]);
            if (archetype == ChainArchetype.PROMOTE
                    && promotionAlreadyAchieved(state, chainRow, actorRow)) {
                terminate(state, chainRow, ChainState.RESOLVED, day);
                continue;
            }
            if (!validPayload(state, chainRow, actorRow, targetRow, archetype)) {
                terminate(state, chainRow, ChainState.FAILED, day);
                continue;
            }

            state.chainLastAdvanceTick[chainRow] = day;
            int progress = Math.min(Short.MAX_VALUE,
                    state.chainProgress[chainRow] + DAILY_PROGRESS);
            state.chainProgress[chainRow] = (short) progress;
            if (progress < state.chainThreshold[chainRow]) continue;

            resolvePayload(state, chainRow, actorRow, targetRow, archetype, day);
            terminate(state, chainRow, ChainState.RESOLVED, day);
        }
    }

    private static boolean promotionAlreadyAchieved(CampaignState state, int chainRow,
                                                      int actorRow) {
        int startingTier = state.chainTier[chainRow] & 0xFF;
        return HouseRank.fromByte(state.houseRank[actorRow]).ordinal() > startingTier;
    }

    private static boolean validPayload(CampaignState state, int chainRow, int actorRow,
                                        int targetRow, ChainArchetype archetype) {
        if (archetype == ChainArchetype.CONSOLIDATE_STAKE) return true;
        if (archetype != ChainArchetype.PROMOTE) return false;
        int startingTier = state.chainTier[chainRow] & 0xFF;
        HouseRank actorRank = HouseRank.fromByte(state.houseRank[actorRow]);
        return (startingTier == HouseRank.TIER_1.ordinal()
                || startingTier == HouseRank.TIER_2.ordinal())
                && actorRank.ordinal() == startingTier
                && state.houseFactionId[actorRow] == state.houseFactionId[targetRow]
                && StakeLedger.shareOf(state, state.houseId[actorRow],
                    state.chainMarketId[chainRow], state.chainIndustryId[chainRow]) > 0
                && StakeLedger.shareOf(state, state.houseId[targetRow],
                    state.chainMarketId[chainRow], state.chainIndustryId[chainRow]) > 0;
    }

    private static void resolvePayload(CampaignState state, int chainRow, int actorRow,
                                       int targetRow, ChainArchetype archetype, int day) {
        int stakeSeize = archetype == ChainArchetype.PROMOTE
                ? PROMOTION_STAKE_SEIZE : RESOLUTION_STAKE_SEIZE;
        StakeLedger.seizeShare(state, state.chainTarget[chainRow],
                state.chainActorHouseId[chainRow], state.chainMarketId[chainRow],
                state.chainIndustryId[chainRow], stakeSeize);
        if (archetype == ChainArchetype.PROMOTE) {
            HousePromotion.addProgress(state, targetRow, -PROMOTION_RIVAL_SUPPRESSION);
            HousePromotion.addProgressAndPromote(state, actorRow,
                    PROMOTION_PROGRESS_GAIN, day);
        } else {
            HousePromotion.addProgressAndPromote(state, actorRow,
                    RESOLUTION_PROMOTION_PROGRESS, day);
        }
    }

    private static boolean activeHouse(CampaignState state, int houseRow) {
        return houseRow >= 0
                && HouseStatus.fromByte(state.houseStatus[houseRow]) == HouseStatus.ACTIVE;
    }

    private static void terminate(CampaignState state, int chainRow,
                                  ChainState terminalState, int day) {
        state.chainState[chainRow] = terminalState.toByte();
        state.chainResolvedTick[chainRow] = day;
    }
}
