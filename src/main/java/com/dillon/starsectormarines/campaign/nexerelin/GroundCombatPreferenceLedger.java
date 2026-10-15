package com.dillon.starsectormarines.campaign.nexerelin;

import com.dillon.starsectormarines.campaign.CampaignState;

/** The one reader and writer of the save's {@link GroundCombatPreference} on {@link CampaignState}. */
public final class GroundCombatPreferenceLedger {

    private GroundCombatPreferenceLedger() {}

    public static GroundCombatPreference read(CampaignState state) {
        return state == null ? GroundCombatPreference.ASK : GroundCombatPreference.of(state.groundCombatPreference);
    }

    public static void write(CampaignState state, GroundCombatPreference preference) {
        if (state == null) return;
        state.groundCombatPreference = (preference != null ? preference : GroundCombatPreference.ASK).ordinal();
    }
}
