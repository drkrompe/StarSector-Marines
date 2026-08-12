package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CivilianRescueEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CivilianRescueLocalMissionTest {

    @Test
    void exposesNewestCommittedMissionOnlyAtItsFrozenMarket() {
        CampaignState state = new CampaignState();
        int local = state.marketRegistry.intern("local");
        int remote = state.marketRegistry.intern("remote");
        long older = prepareCommitted(state, 11L, local, 100);
        prepareCommitted(state, 12L, remote, 200);
        long newest = prepareCommitted(state, 13L, local, 300);

        int row = CivilianRescueLocalMission.committedRow(state, "local");
        Mission mission = CivilianRescueLocalMission.find(state,
                "local", "Arcadia", "independent");

        assertEquals(newest, state.eventId[row]);
        assertNotNull(mission);
        assertEquals(newest, mission.campaignEventId);
        assertEquals(300, mission.civiliansAtRisk);
        assertEquals(MissionSource.CAMPAIGN_EVENT, mission.source);
        assertNull(CivilianRescueLocalMission.find(state,
                "elsewhere", "Elsewhere", "independent"));

        state.eventState[state.eventIndex(newest)] =
                CampaignEventState.RESOLVED.toByte();
        assertEquals(older, state.eventId[
                CivilianRescueLocalMission.committedRow(state, "local")]);
    }

    @Test
    void pendingAndTerminalRowsNeverEmit() {
        CampaignState state = new CampaignState();
        int market = state.marketRegistry.intern("local");
        long eventId = CivilianRescueEvent.prepare(
                state, 21L, market, 10, 13, 25, 15, 100);

        assertNull(CivilianRescueLocalMission.find(state,
                "local", "Arcadia", "independent"));

        int row = state.eventIndex(eventId);
        state.eventState[row] = CampaignEventState.REFUSED.toByte();
        assertNull(CivilianRescueLocalMission.find(state,
                "local", "Arcadia", "independent"));
    }

    private static long prepareCommitted(CampaignState state, long trigger,
                                         int market, int atRisk) {
        long eventId = CivilianRescueEvent.prepare(state, trigger, market,
                10, 13, 25, 15, atRisk);
        int row = state.eventIndex(eventId);
        state.eventState[row] = CampaignEventState.COMMITTED.toByte();
        state.eventDecisionTick[row] = 10;
        return eventId;
    }
}
