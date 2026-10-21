package com.dillon.starsectormarines.campaign.refit;

import com.dillon.starsectormarines.i18n.Strings;

/** Player-facing names for tracks, tiers and lock reasons; keys are built from the enums. */
public final class RefitText {
    private RefitText() { }

    public static String track(RefitTrack track) { return Strings.get("openingRefitTrack" + track.name()); }

    public static String tier(RefitTrack track, RefitBand band) {
        return Strings.get("openingRefitTier" + track.name() + band.name());
    }

    /** What a band of a track means, in plain figures. */
    public static String effect(RefitTrack track, RefitBand band) {
        return Strings.get("openingRefitEffect" + track.name() + band.name());
    }

    public static String band(RefitBand band) { return Strings.get("openingRefitBand" + band.name()); }

    /** Castle's reason a tier is locked: the core it needs, in the core's own terms. */
    public static String lock(RefitTrack needed, RefitBand band) {
        String key = needed.core != null ? "openingRefitLock" + needed.name() : "openingRefitLockOther";
        return Strings.format(key, tier(needed, band));
    }
}
