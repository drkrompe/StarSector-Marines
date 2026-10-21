package com.dillon.starsectormarines.campaign.refit;

import com.dillon.starsectormarines.i18n.Strings;
import java.util.ArrayList;
import java.util.List;

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

    /** Castle on how the system works aboard her, for the systems tree. */
    public static String lore(RefitTrack track) { return Strings.get("openingRefitLore" + track.name()); }

    public static String band(RefitBand band) { return Strings.get("openingRefitBand" + band.name()); }

    /** Castle's reason a tier is locked: the core it needs, in the core's own terms. */
    public static String lock(RefitTrack needed, RefitBand band) {
        String key = needed.core != null ? "openingRefitLock" + needed.name() : "openingRefitLockOther";
        return Strings.format(key, tier(needed, band));
    }

    /**
     * The briefing's line for what the company's home is worth in this
     * operation, frozen at launch: the mess's morale band; while the scrubbers
     * are degraded, the extra day every wound will take; once the staff college
     * is running, the officer XP it adds; and each training or medical room once
     * it changes the fight (the range from its target machinery, the gym and
     * sick bay from opening). Null off the founding hull, where every track is
     * neutral.
     */
    public static String briefingLine(ShipCondition condition) {
        if (!condition.applies()) return null;
        List<String> parts = new ArrayList<>();
        for (RefitTrack track : new RefitTrack[]{RefitTrack.MESS, RefitTrack.LIFE_SUPPORT,
                RefitTrack.WARDROOM, RefitTrack.RANGE, RefitTrack.GYM, RefitTrack.SICK_BAY}) {
            RefitBand band = condition.band(track);
            if (!onBriefing(track, band)) continue;
            parts.add(Strings.format("openingRefitTooltipRow", track(track), tier(track, band), effect(track, band)));
        }
        return String.join("  ·  ", parts);
    }

    private static boolean onBriefing(RefitTrack track, RefitBand band) {
        return switch (track) {
            case MESS -> true;
            case LIFE_SUPPORT -> band == RefitBand.DEFICIT;
            case WARDROOM -> band.atLeast(RefitBand.IMPROVED_I);
            // An open range trains aboard but does not yet change the fight.
            case RANGE -> band.atLeast(RefitBand.IMPROVED_I);
            default -> band.atLeast(RefitBand.RESTORED);
        };
    }
}
