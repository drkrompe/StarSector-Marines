package com.dillon.starsectormarines.campaign.raid;

import com.dillon.starsectormarines.campaign.CampaignState;

/** The one reader and writer of the save's {@link RaidResolutionPreference} on {@link CampaignState}. */
public final class RaidResolutionPreferenceLedger {

    private RaidResolutionPreferenceLedger() {}

    public static RaidResolutionPreference read(CampaignState state) {
        return state == null ? RaidResolutionPreference.ASK : RaidResolutionPreference.of(state.raidResolutionPreference);
    }

    public static void write(CampaignState state, RaidResolutionPreference preference) {
        if (state == null) return;
        state.raidResolutionPreference = (preference != null ? preference : RaidResolutionPreference.ASK).ordinal();
    }
}
