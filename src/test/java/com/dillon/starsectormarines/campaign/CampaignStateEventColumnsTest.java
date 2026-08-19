package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CampaignStateEventColumnsTest {

    @Test
    void appendSnapshotsPendingCivilianRescue() {
        CampaignState state = new CampaignState();

        long id = state.appendCampaignEvent(CampaignEventType.CIVILIAN_RESCUE,
                77L, 3, 20, 21, 40, 25, 120);

        assertEquals(1L, id);
        assertEquals(1, state.eventCount);
        assertEquals(0, state.eventIndex(id));
        assertEquals(CampaignEventType.CIVILIAN_RESCUE,
                CampaignEventType.fromByte(state.eventType[0]));
        assertEquals(77L, state.eventTriggerKey[0]);
        assertEquals(CampaignEventState.PENDING_CHOICE,
                CampaignEventState.fromByte(state.eventState[0]));
        assertEquals(3, state.eventMarketId[0]);
        assertEquals(20, state.eventCreatedTick[0]);
        assertEquals(21, state.eventDeadlineTick[0]);
        assertEquals(-1, state.eventDecisionTick[0]);
        assertEquals(-1, state.eventResolvedTick[0]);
        assertEquals(40, state.eventSuppliesRequired[0]);
        assertEquals(25, state.eventFuelRequired[0]);
        assertEquals(120, state.eventCiviliansAtRisk[0]);
        assertEquals(0, state.eventCiviliansRescued[0]);
        assertEquals(-1L, state.eventSourceChainId[0]);
        assertEquals(-1L, state.eventActorHouseId[0]);
        assertEquals(-1L, state.eventTargetHouseId[0]);
        assertEquals(-1, state.eventFollowupTick[0]);
        assertEquals(-1, state.eventFollowupDeadlineTick[0]);
        assertEquals(0, state.eventCreditsOffered[0]);
        assertEquals(DefectorAsylumOutcome.NONE,
                DefectorAsylumOutcome.fromByte(state.eventDefectorOutcome[0]));
        assertEquals(-1L, state.eventColonyThreatSeed[0]);
        assertEquals(AbandonedColonyArchiveOutcome.NONE,
                AbandonedColonyArchiveOutcome.fromByte(
                        state.eventColonyArchiveOutcome[0]));
    }

    @Test
    void growthInitializesUnusedIdentityAndTickSentinels() {
        CampaignState state = new CampaignState();
        for (int i = 0; i < 20; i++) {
            state.appendCampaignEvent(CampaignEventType.CIVILIAN_RESCUE,
                    i + 1L, i, i, i + 1, 10, 5, 100);
        }

        assertEquals(CampaignEventType.NONE,
                CampaignEventType.fromByte(state.eventType[20]));
        assertEquals(-1L, state.eventTriggerKey[20]);
        assertEquals(CampaignEventState.PENDING_CHOICE,
                CampaignEventState.fromByte(state.eventState[20]));
        assertEquals(-1, state.eventMarketId[20]);
        assertEquals(-1, state.eventCreatedTick[20]);
        assertEquals(-1, state.eventDeadlineTick[20]);
        assertEquals(-1, state.eventDecisionTick[20]);
        assertEquals(-1, state.eventResolvedTick[20]);
        assertEquals(0, state.eventSuppliesRequired[20]);
        assertEquals(0, state.eventCiviliansAtRisk[20]);
        assertEquals(-1L, state.eventSourceChainId[20]);
        assertEquals(-1L, state.eventActorHouseId[20]);
        assertEquals(-1L, state.eventTargetHouseId[20]);
        assertEquals(-1, state.eventFollowupTick[20]);
        assertEquals(-1, state.eventFollowupDeadlineTick[20]);
        assertEquals(0, state.eventCreditsOffered[20]);
        assertEquals(DefectorAsylumOutcome.NONE,
                DefectorAsylumOutcome.fromByte(state.eventDefectorOutcome[20]));
        assertEquals(-1L, state.eventColonyThreatSeed[20]);
        assertEquals(AbandonedColonyArchiveOutcome.NONE,
                AbandonedColonyArchiveOutcome.fromByte(
                        state.eventColonyArchiveOutcome[20]));
    }

    @Test
    void legacyStateBackfillsColumnsAndRebuildsIndexAndSequence() throws Exception {
        CampaignState state = new CampaignState();
        long existing = state.appendCampaignEvent(
                CampaignEventType.CIVILIAN_RESCUE,
                7L, 1, 10, 11, 10, 5, 100);
        state.eventType = null;
        state.eventTriggerKey = null;
        state.eventState = null;
        state.eventMarketId = null;
        state.eventCreatedTick = null;
        state.eventDeadlineTick = null;
        state.eventDecisionTick = null;
        state.eventResolvedTick = null;
        state.eventSuppliesRequired = null;
        state.eventFuelRequired = null;
        state.eventCiviliansAtRisk = null;
        state.eventCiviliansRescued = null;
        state.eventSourceChainId = null;
        state.eventActorHouseId = null;
        state.eventTargetHouseId = null;
        state.eventFollowupTick = null;
        state.eventFollowupDeadlineTick = null;
        state.eventCreditsOffered = null;
        state.eventDefectorOutcome = null;
        state.eventColonyThreatSeed = null;
        state.eventColonyArchiveOutcome = null;
        state.eventIndexById = null;
        Field nextId = CampaignState.class.getDeclaredField("nextEventId");
        nextId.setAccessible(true);
        nextId.setLong(state, 0L);

        readResolve(state);

        assertNotNull(state.eventType);
        assertNotNull(state.eventTriggerKey);
        assertNotNull(state.eventState);
        assertNotNull(state.eventMarketId);
        assertNotNull(state.eventDecisionTick);
        assertNotNull(state.eventCiviliansRescued);
        assertNotNull(state.eventSourceChainId);
        assertNotNull(state.eventActorHouseId);
        assertNotNull(state.eventTargetHouseId);
        assertNotNull(state.eventFollowupTick);
        assertNotNull(state.eventFollowupDeadlineTick);
        assertNotNull(state.eventCreditsOffered);
        assertNotNull(state.eventDefectorOutcome);
        assertNotNull(state.eventColonyThreatSeed);
        assertNotNull(state.eventColonyArchiveOutcome);
        assertNotNull(state.eventIndexById);
        assertEquals(0, state.eventIndex(existing));
        assertEquals(-1L, state.eventTriggerKey[0]);
        assertEquals(-1, state.eventDecisionTick[0]);
        assertEquals(-1L, state.eventSourceChainId[0]);
        assertEquals(-1, state.eventFollowupTick[0]);
        assertEquals(-1L, state.eventColonyThreatSeed[0]);
        assertEquals(2L, state.appendCampaignEvent(
                CampaignEventType.CIVILIAN_RESCUE,
                8L, 1, 12, 13, 10, 5, 100));
    }

    @Test
    void legacyEmptyStateBackfillsAbsentEventTable() throws Exception {
        CampaignState state = new CampaignState();
        state.eventId = null;
        state.eventType = null;
        state.eventTriggerKey = null;
        state.eventState = null;
        state.eventMarketId = null;
        state.eventCreatedTick = null;
        state.eventDeadlineTick = null;
        state.eventDecisionTick = null;
        state.eventResolvedTick = null;
        state.eventSuppliesRequired = null;
        state.eventFuelRequired = null;
        state.eventCiviliansAtRisk = null;
        state.eventCiviliansRescued = null;
        state.eventSourceChainId = null;
        state.eventActorHouseId = null;
        state.eventTargetHouseId = null;
        state.eventFollowupTick = null;
        state.eventFollowupDeadlineTick = null;
        state.eventCreditsOffered = null;
        state.eventDefectorOutcome = null;
        state.eventColonyThreatSeed = null;
        state.eventColonyArchiveOutcome = null;
        state.eventIndexById = null;

        readResolve(state);

        assertNotNull(state.eventId);
        assertEquals(CampaignEventType.NONE,
                CampaignEventType.fromByte(state.eventType[0]));
        assertEquals(-1L, state.eventTriggerKey[0]);
        assertEquals(-1, state.eventMarketId[0]);
        assertEquals(-1, state.eventCreatedTick[0]);
        assertEquals(-1, state.eventDecisionTick[0]);
        assertEquals(-1, state.eventResolvedTick[0]);
        assertEquals(-1L, state.eventSourceChainId[0]);
        assertEquals(-1L, state.eventActorHouseId[0]);
        assertEquals(-1L, state.eventTargetHouseId[0]);
        assertEquals(-1, state.eventFollowupTick[0]);
        assertEquals(-1, state.eventFollowupDeadlineTick[0]);
        assertEquals(DefectorAsylumOutcome.NONE,
                DefectorAsylumOutcome.fromByte(state.eventDefectorOutcome[0]));
        assertEquals(-1L, state.eventColonyThreatSeed[0]);
        assertEquals(AbandonedColonyArchiveOutcome.NONE,
                AbandonedColonyArchiveOutcome.fromByte(
                        state.eventColonyArchiveOutcome[0]));
        assertNotNull(state.eventIndexById);
    }

    private static void readResolve(CampaignState state) throws Exception {
        Method method = CampaignState.class.getDeclaredMethod("readResolve");
        method.setAccessible(true);
        method.invoke(state);
    }
}
