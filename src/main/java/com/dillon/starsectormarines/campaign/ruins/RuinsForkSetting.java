package com.dillon.starsectormarines.campaign.ruins;

import com.dillon.starsectormarines.campaign.CampaignState;

/**
 * Whether Explore the ruins offers the company or a salvage team, set on the Settings page and saved with the game
 * ({@code ruins-expedition.md}). On, every supported site asks; off, vanilla's salvage team goes, untouched. There is
 * no remembered answer: the player chooses each time.
 *
 * <p>The one reader and writer of {@code CampaignState.ruinsExpeditionPreference}. Off is stored as 2; every other
 * value asks, so a save from before the setting, and one that remembered sending the company, asks.
 */
public final class RuinsForkSetting {

    static final int OFF = 2;

    private RuinsForkSetting() {}

    public static boolean asks(CampaignState state) {
        return state == null || state.ruinsExpeditionPreference != OFF;
    }

    public static void setAsks(CampaignState state, boolean asks) {
        if (state != null) state.ruinsExpeditionPreference = asks ? 0 : OFF;
    }
}
