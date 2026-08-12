package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CivilianRescueEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CampaignEventLifecycleSystemTest {

    @Test
    void pendingChoiceExpiresAfterDeadlineButNotOnDeadline() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("market");
        long eventId = CivilianRescueEvent.prepare(
                state, 1L, market, 20, 21, 10, 5, 100);
        int row = state.eventIndex(eventId);
        CampaignEventLifecycleSystem system = new CampaignEventLifecycleSystem();

        system.tick(state, 21);
        assertEquals(CampaignEventState.PENDING_CHOICE,
                CampaignEventState.fromByte(state.eventState[row]));

        system.tick(state, 22);
        assertEquals(CampaignEventState.EXPIRED,
                CampaignEventState.fromByte(state.eventState[row]));
        assertEquals(-1, state.eventDecisionTick[row]);
        assertEquals(-1, state.eventResolvedTick[row]);
    }
}
