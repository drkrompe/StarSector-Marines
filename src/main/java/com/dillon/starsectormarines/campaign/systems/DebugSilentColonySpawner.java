package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.campaign.CampaignEvents;
import com.dillon.starsectormarines.campaign.CampaignState;

/** Debug forcing seam that preserves production Silent Colony terms. */
@DebugOnly
public final class DebugSilentColonySpawner {

    private DebugSilentColonySpawner() {}

    public static long spawn(CampaignState state, int marketSlot,
                             int ruinTier, int day) {
        if (state == null || marketSlot < 0
                || state.marketRegistry.get(marketSlot) == null
                || ruinTier < 1 || ruinTier > 4 || day < 0
                || CampaignEvents.hasOpenEvent(state)) {
            return -1L;
        }
        return SilentColonySpawnSystem.prepareEvent(
                state, marketSlot, ruinTier, day);
    }
}
