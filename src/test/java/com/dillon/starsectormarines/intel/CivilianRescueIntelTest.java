package com.dillon.starsectormarines.intel;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CivilianRescueEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CivilianRescueIntelTest {

    @Test
    void newestPendingOrCommittedRescueIsActive() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("market");
        long first = CivilianRescueEvent.prepare(
                state, 1L, market, 10, 13, 25, 15, 100);
        int firstRow = state.eventIndex(first);
        state.eventState[firstRow] = CampaignEventState.REFUSED.toByte();
        long second = CivilianRescueEvent.prepare(
                state, 2L, market, 20, 23, 50, 30, 400);
        int secondRow = state.eventIndex(second);

        assertEquals(secondRow, CivilianRescueIntel.activeEventRow(state));

        state.eventState[secondRow] = CampaignEventState.COMMITTED.toByte();
        assertEquals(secondRow, CivilianRescueIntel.activeEventRow(state));
    }

    @Test
    void terminalRescuesAreNotPresentedAsActive() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("market");
        long event = CivilianRescueEvent.prepare(
                state, 1L, market, 10, 13, 25, 15, 100);
        int row = state.eventIndex(event);

        state.eventState[row] = CampaignEventState.EXPIRED.toByte();

        assertEquals(-1, CivilianRescueIntel.activeEventRow(state));
    }

    @Test
    void daysRemainingClampsAfterDeadline() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("market");
        long event = CivilianRescueEvent.prepare(
                state, 1L, market, 10, 13, 25, 15, 100);
        int row = state.eventIndex(event);

        assertEquals(3, CivilianRescueIntel.daysRemaining(state, row, 10));
        assertEquals(0, CivilianRescueIntel.daysRemaining(state, row, 20));
    }
}
