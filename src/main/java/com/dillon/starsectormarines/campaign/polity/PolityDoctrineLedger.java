package com.dillon.starsectormarines.campaign.polity;

import com.dillon.starsectormarines.campaign.CampaignState;

/**
 * The one reader and writer of the polity's three doctrine axes on
 * {@link CampaignState} ({@code polity-ground-doctrine.md}).
 *
 * <p>The state holds three plain ints, in the primitive shape every column there
 * uses; {@link PolityDoctrine} is the value type everything else works in. The read
 * goes through {@link PolityDoctrine#clamped}, so a save whose ints are out of range
 * — a legacy save with no ints at all, or one written before an axis ceiling
 * changed — loads as a legal allocation rather than refusing to load.
 */
public final class PolityDoctrineLedger {

    private PolityDoctrineLedger() {}

    /** The polity's doctrine as saved, coerced into a legal allocation. */
    public static PolityDoctrine read(CampaignState state) {
        if (state == null) return PolityDoctrine.NONE;
        return PolityDoctrine.clamped(state.polityDoctrineQuality,
                state.polityDoctrineNumbers, state.polityDoctrineHeavySupport);
    }

    /** Records a validated allocation. A null doctrine spends nothing. */
    public static void write(CampaignState state, PolityDoctrine doctrine) {
        if (state == null) return;
        PolityDoctrine written = doctrine != null ? doctrine : PolityDoctrine.NONE;
        state.polityDoctrineQuality = written.quality();
        state.polityDoctrineNumbers = written.numbers();
        state.polityDoctrineHeavySupport = written.heavySupport();
    }
}
