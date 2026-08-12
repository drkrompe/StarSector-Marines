package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.HousePromotion;
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
                    || state.chainIndustryId[chainRow] < 0
                    || ChainArchetype.fromByte(state.chainArchetype[chainRow])
                        != ChainArchetype.CONSOLIDATE_STAKE) {
                terminate(state, chainRow, ChainState.FAILED, day);
                continue;
            }

            state.chainLastAdvanceTick[chainRow] = day;
            int progress = Math.min(Short.MAX_VALUE,
                    state.chainProgress[chainRow] + DAILY_PROGRESS);
            state.chainProgress[chainRow] = (short) progress;
            if (progress < state.chainThreshold[chainRow]) continue;

            StakeLedger.seizeShare(state, state.chainTarget[chainRow], actorHouseId,
                    state.chainMarketId[chainRow], state.chainIndustryId[chainRow],
                    RESOLUTION_STAKE_SEIZE);
            HousePromotion.addProgressAndPromote(state, actorRow,
                    RESOLUTION_PROMOTION_PROGRESS, day);
            terminate(state, chainRow, ChainState.RESOLVED, day);
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
