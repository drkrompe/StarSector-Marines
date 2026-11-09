package com.dillon.starsectormarines.campaign.ruins;

import com.dillon.starsectormarines.campaign.CampaignState;

/** The one reader and writer of the save's {@link RuinsExpeditionPreference} on {@link CampaignState}. */
public final class RuinsExpeditionPreferenceLedger {

    private RuinsExpeditionPreferenceLedger() {}

    public static RuinsExpeditionPreference read(CampaignState state) {
        return state == null ? RuinsExpeditionPreference.ASK
                : RuinsExpeditionPreference.of(state.ruinsExpeditionPreference);
    }

    public static void write(CampaignState state, RuinsExpeditionPreference preference) {
        if (state == null) return;
        state.ruinsExpeditionPreference = (preference != null ? preference : RuinsExpeditionPreference.ASK).ordinal();
    }
}
