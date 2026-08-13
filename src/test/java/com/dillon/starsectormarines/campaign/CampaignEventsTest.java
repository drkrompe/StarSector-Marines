package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignEventsTest {

    @Test
    void commonQueryRecognizesEveryOpenLifecyclePhase() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("market");
        long event = state.appendCampaignEvent(CampaignEventType.CIVILIAN_RESCUE,
                1L, market, 10, 13, 10, 5, 100);
        int row = state.eventIndex(event);

        assertTrue(CampaignEvents.hasOpenEvent(state));
        state.eventState[row] = CampaignEventState.COMMITTED.toByte();
        assertTrue(CampaignEvents.hasOpenEvent(state));
        state.eventState[row] = CampaignEventState.PENDING_FOLLOWUP.toByte();
        assertTrue(CampaignEvents.hasOpenEvent(state));

        state.eventState[row] = CampaignEventState.RESOLVED.toByte();
        assertFalse(CampaignEvents.hasOpenEvent(state));
        state.eventState[row] = CampaignEventState.REFUSED.toByte();
        assertFalse(CampaignEvents.hasOpenEvent(state));
        state.eventState[row] = CampaignEventState.EXPIRED.toByte();
        assertFalse(CampaignEvents.hasOpenEvent(state));
    }
}
