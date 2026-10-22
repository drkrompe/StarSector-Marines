package com.dillon.starsectormarines.campaign.bloom;

/**
 * What the bloom rules read about one market on one day: never persisted, rebuilt from the
 * sector (or from a synthetic sector in evidence) whenever the system needs it.
 */
public final class BloomMarket {

    public final String id;
    public final String name;
    public final String systemId;
    public final String factionId;
    public final int size;
    public final int daysInExistence;
    public final boolean hidden;
    public final boolean playerOwned;
    /** Ruins on the world: old seeding sites, by the spacers' rumour. */
    public final boolean ruins;
    /** Farmland or another habitable reading: a world someone once seeded green. */
    public final boolean habitable;
    /** Mining, refining, farming or aquaculture: mine workings, aquifers, biomass. */
    public final boolean workings;
    /** A core-tagged system, policed and relieved quickly. */
    public final boolean core;
    /** Hyperspace position in light-years. */
    public final float x;
    public final float y;
    /** Light-years from the core's centre. */
    public final float lyFromCore;
    /** Trade traffic through the port, 0 (none) to about 2 (a busy megaport). */
    public final float traffic;
    /** Population inflow, 0 (none or shrinking) to 1 (growing fast). */
    public final float inflow;

    public BloomMarket(String id, String name, String systemId, String factionId, int size,
                       int daysInExistence, boolean hidden, boolean playerOwned, boolean ruins,
                       boolean habitable, boolean workings, boolean core,
                       float x, float y, float lyFromCore, float traffic, float inflow) {
        this.id = id;
        this.name = name;
        this.systemId = systemId;
        this.factionId = factionId;
        this.size = size;
        this.daysInExistence = daysInExistence;
        this.hidden = hidden;
        this.playerOwned = playerOwned;
        this.ruins = ruins;
        this.habitable = habitable;
        this.workings = workings;
        this.core = core;
        this.x = x;
        this.y = y;
        this.lyFromCore = lyFromCore;
        this.traffic = traffic;
        this.inflow = inflow;
    }

    public float distanceLy(BloomMarket other) {
        float dx = x - other.x;
        float dy = y - other.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
