package com.dillon.starsectormarines.campaign.bloom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every Rav Bloom outbreak in this save and what each left behind. Null on {@code CampaignState}
 * in older saves and created on first read, so an old save starts with no blooms and nothing to
 * migrate. Reservoirs are not stored here: they are recomputed from the save seed.
 */
public final class RavBloomLedger implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<RavBloom> blooms = new ArrayList<>();
    /** Market id to the first day its reservoir may wake again. */
    private final Map<String, Integer> restUntil = new HashMap<>();
    /** Rescue events whose battle reported the mother bore collapsed. */
    private final Set<Long> motherCollapsedEvents = new HashSet<>();
    private long nextId = 1L;
    private int lastWeek = -1;
    private boolean playerAnswered;

    public List<RavBloom> blooms() {
        return Collections.unmodifiableList(blooms);
    }

    public RavBloom bloom(long id) {
        for (RavBloom bloom : blooms) if (bloom.id == id) return bloom;
        return null;
    }

    /** The running bloom at a market, or null. */
    public RavBloom activeAt(String marketId) {
        for (RavBloom bloom : blooms) {
            if (bloom.active() && bloom.marketId.equals(marketId)) return bloom;
        }
        return null;
    }

    /** The bloom whose distress call raised this rescue event, or null. */
    public RavBloom forRescue(long eventId) {
        if (eventId <= 0L) return null;
        for (RavBloom bloom : blooms) if (bloom.rescueEventId == eventId) return bloom;
        return null;
    }

    public int activeCount() {
        int n = 0;
        for (RavBloom bloom : blooms) if (bloom.active()) n++;
        return n;
    }

    public boolean resting(String marketId, int day) {
        Integer until = restUntil.get(marketId);
        return until != null && day < until;
    }

    /** Whether the player has ever answered a bloom's call with an evacuation. */
    public boolean playerAnswered() {
        return playerAnswered;
    }

    /** Records the battle's mother-bore report for a rescue; the bloom reads it on its next tick. */
    public void recordMotherBoreCollapsed(long rescueEventId) {
        if (rescueEventId > 0L) motherCollapsedEvents.add(rescueEventId);
    }

    public boolean motherCollapsedFor(long rescueEventId) {
        return motherCollapsedEvents.contains(rescueEventId);
    }

    RavBloom open(String marketId, int generation, long parentId, int day, int stirDays) {
        RavBloom bloom = new RavBloom(nextId++, marketId, generation, parentId, day, stirDays);
        blooms.add(bloom);
        return bloom;
    }

    void rest(String marketId, int untilDay) {
        Integer current = restUntil.get(marketId);
        if (current == null || current < untilDay) restUntil.put(marketId, untilDay);
    }

    void markAnswered() {
        playerAnswered = true;
    }

    int lastWeek() {
        return lastWeek;
    }

    void lastWeek(int week) {
        lastWeek = week;
    }
}
