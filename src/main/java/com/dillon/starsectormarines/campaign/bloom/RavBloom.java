package com.dillon.starsectormarines.campaign.bloom;

import java.io.Serializable;

/**
 * One outbreak of the Rav Bloom at one market, from the first seepage to the day it ends.
 * Persisted on {@link RavBloomLedger}; the market is held by id so a save never depends on a
 * registry slot.
 */
public final class RavBloom implements Serializable {

    private static final long serialVersionUID = 1L;

    public final long id;
    public final String marketId;
    /** 0 for a bloom that woke from its own reservoir; the parent's + 1 for one seeded by spread. */
    public final int generation;
    /** The dark bloom that seeded this one, or -1. */
    public final long parentId;
    public final int wakeDay;
    /** Days of seepage before the bores open; 0 for a seeded bloom. */
    public final int stirDays;

    BloomStage stage = BloomStage.STIRRING;
    BloomEnd end = BloomEnd.NONE;
    int eruptDay = -1;
    int darkDay = -1;
    int starvedDay = -1;
    int endDay = -1;
    /** The Civilian Rescue event this bloom's distress call raised, or -1 before it got through. */
    long rescueEventId = -1L;
    boolean quarantined;
    int sizeLost;
    int lastSizeLossDay = -1;
    int seeded;
    int evacuated = -1;
    int atRisk = -1;
    boolean motherCollapsed;
    boolean resultApplied;
    boolean refused;

    RavBloom(long id, String marketId, int generation, long parentId, int wakeDay, int stirDays) {
        this.id = id;
        this.marketId = marketId;
        this.generation = generation;
        this.parentId = parentId;
        this.wakeDay = wakeDay;
        this.stirDays = stirDays;
    }

    public BloomStage stage() { return stage; }
    public BloomEnd end() { return end; }
    public int eruptDay() { return eruptDay; }
    public int darkDay() { return darkDay; }
    public int starvedDay() { return starvedDay; }
    public int endDay() { return endDay; }
    public long rescueEventId() { return rescueEventId; }
    /** The Hegemony has posted a quarantine notice on the world. */
    public boolean quarantined() { return quarantined; }
    public int sizeLost() { return sizeLost; }
    /** Neighbours this bloom has seeded. */
    public int seeded() { return seeded; }
    /** Evacuees the player lifted out, or -1 when no evacuation was reported. */
    public int evacuated() { return evacuated; }
    public int atRisk() { return atRisk; }
    public boolean motherCollapsed() { return motherCollapsed; }
    /** The distress call was declined. */
    public boolean refused() { return refused; }

    public boolean active() {
        return stage != BloomStage.ENDED;
    }

    /** Erupted and not yet over: the market carries the bloom condition. */
    public boolean onTheSurface() {
        return stage == BloomStage.ERUPTED || stage == BloomStage.DARK || stage == BloomStage.STARVED;
    }
}
