package com.dillon.starsectormarines.campaign;

/** Shared queries across concrete black-swan event archetypes. */
public final class CampaignEvents {

    private CampaignEvents() {}

    public static boolean hasOpenEvent(CampaignState state) {
        if (state == null) return false;
        for (int row = 0; row < state.eventCount; row++) {
            CampaignEventState eventState = CampaignEventState.fromByte(
                    state.eventState[row]);
            if (eventState == CampaignEventState.PENDING_CHOICE
                    || eventState == CampaignEventState.COMMITTED
                    || eventState == CampaignEventState.PENDING_FOLLOWUP) {
                return true;
            }
        }
        return false;
    }
}
