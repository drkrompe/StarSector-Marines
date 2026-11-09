package com.dillon.starsectormarines.campaign.raid;

import com.dillon.starsectormarines.campaign.CampaignState;

/**
 * Whether the player's raids offer the Marine Operations fork, set on the Settings page and saved with the game
 * ({@code raid-resolution.md}). On, every supported raid asks; off, every raid is the game's standard one, untouched.
 * There is no remembered answer: the player chooses each raid.
 *
 * <p>The one reader and writer of {@code CampaignState.raidResolutionPreference}. Off is stored as 2; every other
 * value asks, so a save from before the setting, and one that remembered leading raids by hand, asks.
 */
public final class RaidForkSetting {

    static final int OFF = 2;

    private RaidForkSetting() {}

    public static boolean asks(CampaignState state) {
        return state == null || state.raidResolutionPreference != OFF;
    }

    public static void setAsks(CampaignState state, boolean asks) {
        if (state != null) state.raidResolutionPreference = asks ? 0 : OFF;
    }
}
