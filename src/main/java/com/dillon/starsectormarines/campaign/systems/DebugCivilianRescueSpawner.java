package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CivilianRescueEvent;

/** Debug forcing seam that preserves production rescue terms and lifecycle. */
@DebugOnly
public final class DebugCivilianRescueSpawner {

    private static final long DEBUG_TRIGGER_BASE = 1L << 62;

    private DebugCivilianRescueSpawner() {}

    public static long spawn(CampaignState state, int marketSlot,
                             int marketSize, int day) {
        if (state == null || marketSlot < 0
                || state.marketRegistry.get(marketSlot) == null
                || marketSize < 3 || day < 0
                || CivilianRescueEvent.hasOpenRescue(state)) {
            return -1L;
        }
        long triggerKey = DEBUG_TRIGGER_BASE
                + ((long) day << 20) + state.eventCount;
        return CivilianRescueSpawnSystem.prepareEvent(
                state, triggerKey, marketSlot, marketSize, day);
    }
}
