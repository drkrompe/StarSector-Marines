package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignEventType;
import com.dillon.starsectormarines.campaign.CampaignEvents;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ChainState;
import com.dillon.starsectormarines.campaign.DefectorAsylumEvent;

import java.util.EnumSet;

/** Selects one eligible discovered political chain per defector epoch. */
public final class DefectorAsylumSpawnSystem implements CampaignSystem {

    static final int FIRST_EPOCH_DAY = 60;
    static final int EPOCH_DAYS = 60;
    static final int MINIMUM_DISCOVERY_AGE = 5;
    static final int INITIAL_CHOICE_DAYS = 3;

    @Override
    public String name() {
        return "DefectorAsylumSpawn";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.CHAINS, CampaignTable.HOUSES,
                CampaignTable.EVENTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.EVENTS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null || day < FIRST_EPOCH_DAY
                || day > Integer.MAX_VALUE - INITIAL_CHOICE_DAYS
                || CampaignEvents.hasOpenEvent(state)) {
            return;
        }

        int epoch = (day - FIRST_EPOCH_DAY) / EPOCH_DAYS;
        long triggerKey = triggerKey(epoch);
        if (hasEpochEvent(state, triggerKey)) return;
        int chainRow = selectChain(state, epoch, day);
        if (chainRow < 0) return;

        int tier = (state.chainTier[chainRow] & 0xFF) + 1;
        DefectorAsylumEvent.prepare(state, triggerKey,
                state.chainId[chainRow], state.chainActorHouseId[chainRow],
                state.chainTarget[chainRow], state.chainMarketId[chainRow],
                day, day + INITIAL_CHOICE_DAYS,
                10 * tier, 5 * tier, 20_000 * tier);
    }

    static int selectChain(CampaignState state, int epoch, int day) {
        if (state == null || epoch < 0 || day < 0) return -1;
        int bestRow = -1;
        long bestScore = Long.MAX_VALUE;
        long bestChainId = Long.MAX_VALUE;
        for (int row = 0; row < state.chainCount; row++) {
            if (!eligible(state, row, day)) continue;
            long chainId = state.chainId[row];
            long score = selectionScore(epoch, chainId);
            if (bestRow < 0 || score < bestScore
                    || (score == bestScore && chainId < bestChainId)) {
                bestRow = row;
                bestScore = score;
                bestChainId = chainId;
            }
        }
        return bestRow;
    }

    static long selectionScore(int epoch, long chainId) {
        long value = chainId ^ ((long) epoch << 32) ^ 0xD1B54A32D192ED03L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value & Long.MAX_VALUE;
    }

    static long triggerKey(int epoch) {
        return Math.max(0, epoch);
    }

    private static boolean eligible(CampaignState state, int row, int day) {
        if (ChainState.fromByte(state.chainState[row]) != ChainState.ACTIVE) {
            return false;
        }
        int discovered = state.chainDiscoveredTick[row];
        if (discovered < 0 || day - discovered < MINIMUM_DISCOVERY_AGE) {
            return false;
        }
        long sourceChainId = state.chainId[row];
        long actorHouseId = state.chainActorHouseId[row];
        long targetHouseId = state.chainTarget[row];
        int marketId = state.chainMarketId[row];
        return sourceChainId >= 0L && actorHouseId >= 0L && targetHouseId >= 0L
                && actorHouseId != targetHouseId && marketId >= 0
                && state.houseIndex(actorHouseId) >= 0
                && state.houseIndex(targetHouseId) >= 0
                && state.marketRegistry.get(marketId) != null
                && !hasSourceEvent(state, sourceChainId);
    }

    private static boolean hasEpochEvent(CampaignState state, long triggerKey) {
        for (int row = 0; row < state.eventCount; row++) {
            if (CampaignEventType.fromByte(state.eventType[row])
                    == CampaignEventType.DEFECTOR_ASYLUM
                    && state.eventTriggerKey[row] == triggerKey) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSourceEvent(CampaignState state, long sourceChainId) {
        for (int row = 0; row < state.eventCount; row++) {
            if (CampaignEventType.fromByte(state.eventType[row])
                    == CampaignEventType.DEFECTOR_ASYLUM
                    && state.eventSourceChainId[row] == sourceChainId) {
                return true;
            }
        }
        return false;
    }
}
