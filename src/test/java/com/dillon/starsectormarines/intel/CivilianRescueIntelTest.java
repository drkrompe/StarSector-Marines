package com.dillon.starsectormarines.intel;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CivilianRescueEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void newestResolvedCallRemainsAsDurableDispatch() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("market");
        long first = CivilianRescueEvent.prepare(
                state, 1L, market, 10, 13, 25, 15, 100);
        int firstRow = state.eventIndex(first);
        state.eventState[firstRow] = CampaignEventState.RESOLVED.toByte();
        state.eventCiviliansRescued[firstRow] = 40;
        state.eventResolvedTick[firstRow] = 15;
        long second = CivilianRescueEvent.prepare(
                state, 2L, market, 20, 23, 50, 30, 800);
        int secondRow = state.eventIndex(second);
        state.eventState[secondRow] = CampaignEventState.RESOLVED.toByte();
        state.eventCiviliansRescued[secondRow] = 300;
        state.eventResolvedTick[secondRow] = 22;

        assertEquals(secondRow, CivilianRescueIntel.latestResolvedRow(state));
        assertEquals("Evacuation concluded: 300 of 800 civilians rescued.",
                CivilianRescueIntel.resolvedSummary(state, secondRow));
    }

    @Test
    void invalidOrNonterminalRowsHaveNoResolutionSummary() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("market");
        long event = CivilianRescueEvent.prepare(
                state, 1L, market, 10, 13, 25, 15, 100);

        assertNull(CivilianRescueIntel.resolvedSummary(
                state, state.eventIndex(event)));
        assertNull(CivilianRescueIntel.resolvedSummary(state, -1));
    }
}
