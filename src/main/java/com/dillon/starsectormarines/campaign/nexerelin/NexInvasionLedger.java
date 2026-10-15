package com.dillon.starsectormarines.campaign.nexerelin;

import com.dillon.starsectormarines.campaign.CampaignState;

/**
 * Makes a Marine Operations invasion settle exactly once. Every briefing opened draws a new serial into its mission
 * key; a result settles only when its serial is newer than the last one settled. A briefing backed out of simply
 * leaves its serial unused, and replaying Results, or an older briefing's result, settles nothing.
 */
public final class NexInvasionLedger {

    private NexInvasionLedger() {}

    /** The serial for a newly opened invasion briefing. */
    public static int issue(CampaignState state) {
        if (state == null) return 0;
        return ++state.nexInvasionIssued;
    }

    /** Claims the right to settle {@code serial}; false when it, or a later invasion, has already settled. */
    public static boolean claim(CampaignState state, int serial) {
        if (state == null || serial <= 0 || serial > state.nexInvasionIssued) return false;
        if (serial <= state.nexInvasionSettled) return false;
        state.nexInvasionSettled = serial;
        return true;
    }
}
