package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignEventType;
import com.dillon.starsectormarines.campaign.CampaignEvents;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.DefectorAsylumEvent;

/** Debug forcing seams for the production defector lifecycle. */
@DebugOnly
public final class DebugDefectorAsylumSpawner {

    private static final long DEBUG_TRIGGER_BASE = 1L << 61;

    private DebugDefectorAsylumSpawner() {}

    public static long spawn(CampaignState state, int day) {
        if (state == null || day < 0 || CampaignEvents.hasOpenEvent(state)) {
            return -1L;
        }
        int chainRow = DefectorAsylumSpawnSystem.selectChain(state, 0, day);
        if (chainRow < 0) return -1L;
        long triggerKey = DEBUG_TRIGGER_BASE
                + ((long) day << 20) + state.eventCount;
        return DefectorAsylumSpawnSystem.prepareEvent(
                state, triggerKey, chainRow, day);
    }

    public static long advanceCommitted(CampaignState state, int day) {
        if (state == null || day < 0
                || day > Integer.MAX_VALUE
                    - DefectorAsylumEvent.FOLLOWUP_CHOICE_DAYS) {
            return -1L;
        }
        for (int row = state.eventCount - 1; row >= 0; row--) {
            if (CampaignEventType.fromByte(state.eventType[row])
                    != CampaignEventType.DEFECTOR_ASYLUM
                    || CampaignEventState.fromByte(state.eventState[row])
                    != CampaignEventState.COMMITTED) {
                continue;
            }
            state.eventFollowupTick[row] = day;
            state.eventFollowupDeadlineTick[row] =
                    day + DefectorAsylumEvent.FOLLOWUP_CHOICE_DAYS;
            DefectorAsylumEvent.Result result =
                    DefectorAsylumEvent.advanceToFollowup(
                            state, state.eventId[row], day);
            return result == DefectorAsylumEvent.Result.FOLLOWUP_READY
                    ? state.eventId[row] : -1L;
        }
        return -1L;
    }
}
