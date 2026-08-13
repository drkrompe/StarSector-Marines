package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignEventType;
import com.dillon.starsectormarines.campaign.CampaignEvents;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.CivilianRescueEvent;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.EnumSet;

/** Deterministically prepares sparse civilian-rescue black-swan events. */
public final class CivilianRescueSpawnSystem implements CampaignSystem {

    static final int FIRST_EPOCH_DAY = 30;
    static final int EPOCH_DAYS = 45;
    static final int CHOICE_DAYS = 3;

    interface MarketSource {
        /** Eligible market size, or {@code -1} when the market is ineligible. */
        int eligibleSize(String marketId);
    }

    private final MarketSource markets;

    public CivilianRescueSpawnSystem() {
        this(new SectorMarkets());
    }

    CivilianRescueSpawnSystem(MarketSource markets) {
        this.markets = markets;
    }

    @Override
    public String name() {
        return "CivilianRescueSpawn";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.EVENTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.EVENTS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null || markets == null || day < FIRST_EPOCH_DAY) return;
        int epoch = (day - FIRST_EPOCH_DAY) / EPOCH_DAYS;
        long triggerKey = triggerKey(epoch);
        if (hasTrigger(state, triggerKey)
                || CampaignEvents.hasOpenEvent(state)) return;

        int marketSlot = selectMarket(state, epoch);
        if (marketSlot < 0) return;
        int marketSize = markets.eligibleSize(
                state.marketRegistry.get(marketSlot));
        if (marketSize < 3) return;

        prepareEvent(state, triggerKey, marketSlot, marketSize, day);
    }

    public static long prepareEvent(CampaignState state, long triggerKey,
                                    int marketSlot, int marketSize, int day) {
        long tier = (long) marketSize - 2L;
        if (tier < 1L || tier > 4_634L
                || day < 0 || day > Integer.MAX_VALUE - CHOICE_DAYS) {
            return -1L;
        }
        return CivilianRescueEvent.prepare(state, triggerKey, marketSlot,
                day, day + CHOICE_DAYS, (int) (25L * tier),
                (int) (15L * tier), (int) (100L * tier * tier));
    }

    static long triggerKey(int epoch) {
        return Math.max(0, epoch);
    }

    static long selectionScore(int epoch, String marketId) {
        long value = ((long) marketId.hashCode() << 32)
                ^ (epoch & 0xFFFFFFFFL) ^ 0x9E3779B97F4A7C15L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value & Long.MAX_VALUE;
    }

    private int selectMarket(CampaignState state, int epoch) {
        int bestSlot = -1;
        long bestScore = Long.MAX_VALUE;
        String bestId = null;
        for (int slot = 0; slot < state.marketRegistry.size(); slot++) {
            String marketId = state.marketRegistry.get(slot);
            if (marketId == null || markets.eligibleSize(marketId) < 3) continue;
            long score = selectionScore(epoch, marketId);
            if (bestSlot < 0 || score < bestScore
                    || (score == bestScore && marketId.compareTo(bestId) < 0)) {
                bestSlot = slot;
                bestScore = score;
                bestId = marketId;
            }
        }
        return bestSlot;
    }

    private static boolean hasTrigger(CampaignState state, long triggerKey) {
        for (int row = 0; row < state.eventCount; row++) {
            if (CampaignEventType.fromByte(state.eventType[row])
                    == CampaignEventType.CIVILIAN_RESCUE
                    && state.eventTriggerKey[row] == triggerKey) {
                return true;
            }
        }
        return false;
    }

    private static final class SectorMarkets implements MarketSource {
        @Override
        public int eligibleSize(String marketId) {
            if (marketId == null || Global.getSector() == null) return -1;
            MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
            if (market == null || market.isHidden()
                    || market.getPrimaryEntity() == null
                    || market.getSize() < 3) {
                return -1;
            }
            return market.getSize();
        }
    }
}
