package com.dillon.starsectormarines.campaign.nexerelin;

import com.dillon.starsectormarines.campaign.CampaignState;

/**
 * Whether Nexerelin's Invade offers the Marine Operations fork, set on the Settings page (shown only with Nexerelin)
 * and saved with the game ({@code nexerelin-nouns.md}). On, every supported invasion asks; off, every invasion is
 * Nexerelin's own, untouched. There is no remembered answer: the player chooses each invasion.
 *
 * <p>The one reader and writer of {@code CampaignState.groundCombatPreference}. Off is stored as 2; every other value
 * asks, so a save from before the setting, and one that remembered fighting invasions by hand, asks.
 */
public final class InvasionForkSetting {

    static final int OFF = 2;

    private InvasionForkSetting() {}

    public static boolean asks(CampaignState state) {
        return state == null || state.groundCombatPreference != OFF;
    }

    public static void setAsks(CampaignState state, boolean asks) {
        if (state != null) state.groundCombatPreference = asks ? 0 : OFF;
    }
}
