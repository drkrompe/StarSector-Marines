package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignEventType;
import com.dillon.starsectormarines.campaign.CampaignEvents;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.SilentColonyEvent;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** Selects one unused dead-colony site per sparse deterministic epoch. */
public final class SilentColonySpawnSystem implements CampaignSystem {

    static final int FIRST_EPOCH_DAY = 90;
    static final int EPOCH_DAYS = 90;
    static final int CHOICE_DAYS = 3;

    interface SiteSource {
        List<Site> eligibleSites();
    }

    static final class Site {
        final String marketId;
        final int ruinTier;

        Site(String marketId, int ruinTier) {
            this.marketId = marketId;
            this.ruinTier = ruinTier;
        }
    }

    private final SiteSource sites;

    public SilentColonySpawnSystem() {
        this(new SectorSites());
    }

    SilentColonySpawnSystem(SiteSource sites) {
        this.sites = sites;
    }

    @Override
    public String name() {
        return "SilentColonySpawn";
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
        if (state == null || sites == null || day < FIRST_EPOCH_DAY
                || day > Integer.MAX_VALUE - CHOICE_DAYS
                || CampaignEvents.hasOpenEvent(state)) {
            return;
        }
        int epoch = epoch(day);
        if (hasEpochEvent(state, epoch)) return;

        Site site = selectSite(state, sites.eligibleSites(), epoch);
        if (site == null) return;
        int marketSlot = state.marketRegistry.intern(site.marketId);
        prepareEvent(state, marketSlot, site.ruinTier, day);
    }

    static long prepareEvent(CampaignState state, int marketSlot,
                             int ruinTier, int day) {
        if (state == null || marketSlot < 0
                || state.marketRegistry.get(marketSlot) == null
                || ruinTier < 1 || ruinTier > 4 || day < 0
                || day > Integer.MAX_VALUE - CHOICE_DAYS) {
            return -1L;
        }
        String marketId = state.marketRegistry.get(marketSlot);
        long triggerKey = siteKey(marketId);
        return SilentColonyEvent.prepare(state, triggerKey, marketSlot,
                day, day + CHOICE_DAYS,
                20 + 10 * ruinTier,
                10 + 5 * ruinTier,
                4 + 2 * ruinTier,
                threatSeed(triggerKey));
    }

    static Site selectSite(CampaignState state, List<Site> candidates,
                           int epoch) {
        if (state == null || candidates == null || epoch < 0) return null;
        Site selected = null;
        long bestScore = Long.MAX_VALUE;
        for (Site candidate : candidates) {
            if (candidate == null || candidate.marketId == null
                    || candidate.marketId.trim().isEmpty()
                    || candidate.ruinTier < 1 || candidate.ruinTier > 4
                    || hasSourceEvent(state, candidate.marketId)) {
                continue;
            }
            long score = selectionScore(epoch, candidate.marketId);
            if (selected == null || score < bestScore
                    || (score == bestScore
                        && candidate.marketId.compareTo(selected.marketId) < 0)) {
                selected = candidate;
                bestScore = score;
            }
        }
        return selected;
    }

    static long selectionScore(int epoch, String marketId) {
        return mix(siteKey(marketId) ^ ((long) epoch << 32)
                ^ 0xD6E8FEB86659FD93L);
    }

    static long siteKey(String marketId) {
        if (marketId == null) return -1L;
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < marketId.length(); i++) {
            hash ^= marketId.charAt(i);
            hash *= 0x100000001b3L;
        }
        return mix(hash);
    }

    static long threatSeed(long triggerKey) {
        return triggerKey >= 0L
                ? mix(triggerKey ^ 0xA0761D6478BD642FL) : -1L;
    }

    static int eligibleTier(boolean conditionOnly, boolean validMissionTarget,
                            boolean hasPrimaryEntity, boolean decivilized,
                            boolean abandonedStation, int ruinsTier) {
        if (!validMissionTarget || !hasPrimaryEntity
                || ruinsTier < 0 || ruinsTier > 4) {
            return 0;
        }
        int tier = decivilized || abandonedStation ? 2 : 0;
        return conditionOnly ? Math.max(tier, ruinsTier) : tier;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value & Long.MAX_VALUE;
    }

    private static int epoch(int day) {
        return (day - FIRST_EPOCH_DAY) / EPOCH_DAYS;
    }

    private static boolean hasEpochEvent(CampaignState state, int epoch) {
        for (int row = 0; row < state.eventCount; row++) {
            int created = state.eventCreatedTick[row];
            if (CampaignEventType.fromByte(state.eventType[row])
                    == CampaignEventType.SILENT_COLONY
                    && created >= FIRST_EPOCH_DAY && epoch(created) == epoch) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSourceEvent(CampaignState state,
                                          String marketId) {
        for (int row = 0; row < state.eventCount; row++) {
            if (CampaignEventType.fromByte(state.eventType[row])
                    != CampaignEventType.SILENT_COLONY) {
                continue;
            }
            String existing = state.marketRegistry.get(state.eventMarketId[row]);
            if (marketId.equals(existing)) return true;
        }
        return false;
    }

    private static final class SectorSites implements SiteSource {
        @Override
        public List<Site> eligibleSites() {
            if (Global.getSector() == null
                    || Global.getSector().getEconomy() == null) {
                return Collections.emptyList();
            }
            List<Site> result = new ArrayList<>();
            for (MarketAPI market
                    : Global.getSector().getEconomy().getMarketsCopy()) {
                int tier = ruinTier(market);
                if (tier > 0) result.add(new Site(market.getId(), tier));
            }
            return result;
        }

        private static int ruinTier(MarketAPI market) {
            if (market == null || market.getId() == null) return 0;
            int tier = 0;
            if (market.hasCondition(Conditions.RUINS_SCATTERED)) {
                tier = Math.max(tier, 1);
            }
            if (market.hasCondition(Conditions.RUINS_WIDESPREAD)) {
                tier = Math.max(tier, 2);
            }
            if (market.hasCondition(Conditions.RUINS_EXTENSIVE)) {
                tier = Math.max(tier, 3);
            }
            if (market.hasCondition(Conditions.RUINS_VAST)) {
                tier = Math.max(tier, 4);
            }
            return eligibleTier(market.isPlanetConditionMarketOnly(),
                    !market.isInvalidMissionTarget(),
                    market.getPrimaryEntity() != null,
                    market.hasCondition(Conditions.DECIVILIZED),
                    market.hasCondition(Conditions.ABANDONED_STATION), tier);
        }
    }
}
