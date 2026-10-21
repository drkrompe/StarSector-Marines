package com.dillon.starsectormarines.campaign.refit;

/**
 * Where a refit track stands against neutral. Neutral is {@link #RESTORED}: the
 * figures every mission, scene and matchup is tuned at, so a deficit or an
 * improvement is a known offset from tuned ground.
 */
public enum RefitBand {
    DEFICIT, RESTORED, IMPROVED_I, IMPROVED_II;

    public boolean atLeast(RefitBand other) { return ordinal() >= other.ordinal(); }

    public RefitBand next() { return this == IMPROVED_II ? null : values()[ordinal() + 1]; }
}
