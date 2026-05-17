package com.dillon.starsectormarines.ops.intel;

/**
 * Compact snapshot of one industry on a planet — only the fields the mission system
 * needs to read. Decouples the rest of the mod from {@code com.fs.starfarer.api.campaign.econ.Industry}
 * so tests and our generator don't need a live MarketAPI in scope.
 */
public final class IndustryEntry {

    public final String  id;
    public final String  displayName;
    public final boolean disrupted;
    /** Special item id (e.g. PRISTINE_NANOFORGE) or null. */
    public final String  specialItemId;

    public IndustryEntry(String id, String displayName, boolean disrupted, String specialItemId) {
        this.id            = id;
        this.displayName   = displayName;
        this.disrupted     = disrupted;
        this.specialItemId = specialItemId;
    }
}
