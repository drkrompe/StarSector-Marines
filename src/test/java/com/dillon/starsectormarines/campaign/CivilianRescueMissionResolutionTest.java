package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.ops.MissionOutcome;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CivilianRescueMissionResolutionTest {

    @Test
    void missingEvacuationReportLeavesCommittedEventUntouched() {
        Fixture fixture = new Fixture();

        CivilianRescueMissionResolution.Result result =
                CivilianRescueMissionResolution.apply(
                        fixture.state, fixture.outcome(-1), 22);

        assertEquals(CivilianRescueMissionResolution.Result.NO_REPORT, result);
        assertEquals(CampaignEventState.COMMITTED, fixture.eventState());
        assertEquals(0, fixture.state.eventCiviliansRescued[fixture.row]);
        assertEquals(-1, fixture.state.eventResolvedTick[fixture.row]);
    }

    @Test
    void explicitZeroPartialAndFullReportsResolve() {
        assertResolved(0);
        assertResolved(60);
        assertResolved(120);
    }

    @Test
    void replayAfterResolutionIsTerminalAndDoesNotReplaceFacts() {
        Fixture fixture = new Fixture();
        assertEquals(CivilianRescueMissionResolution.Result.RESOLVED,
                CivilianRescueMissionResolution.apply(
                        fixture.state, fixture.outcome(60), 22));

        CivilianRescueMissionResolution.Result replay =
                CivilianRescueMissionResolution.apply(
                        fixture.state, fixture.outcome(120), 23);

        assertEquals(CivilianRescueMissionResolution.Result.ALREADY_TERMINAL,
                replay);
        assertEquals(60, fixture.state.eventCiviliansRescued[fixture.row]);
        assertEquals(22, fixture.state.eventResolvedTick[fixture.row]);
    }

    @Test
    void mismatchedOrOutOfRangeReportsDoNotMutate() {
        Fixture fixture = new Fixture();

        assertEquals(CivilianRescueMissionResolution.Result.INVALID,
                CivilianRescueMissionResolution.apply(fixture.state,
                        fixture.outcome("civilian-rescue:999",
                                MissionSource.CAMPAIGN_EVENT,
                                fixture.eventId, fixture.market, 120, 60), 22));
        assertEquals(CivilianRescueMissionResolution.Result.INVALID,
                CivilianRescueMissionResolution.apply(fixture.state,
                        fixture.outcome(CivilianRescueMissionKey.encode(
                                        fixture.eventId),
                                MissionSource.GENERATED,
                                fixture.eventId, fixture.market, 120, 60), 22));
        assertEquals(CivilianRescueMissionResolution.Result.INVALID,
                CivilianRescueMissionResolution.apply(fixture.state,
                        fixture.outcome(CivilianRescueMissionKey.encode(
                                        fixture.eventId),
                                MissionSource.CAMPAIGN_EVENT,
                                fixture.eventId, fixture.market + 1, 120, 60), 22));
        assertEquals(CivilianRescueMissionResolution.Result.INVALID,
                CivilianRescueMissionResolution.apply(fixture.state,
                        fixture.outcome(121), 22));
        assertEquals(CivilianRescueMissionResolution.Result.INVALID,
                CivilianRescueMissionResolution.apply(fixture.state,
                        fixture.outcome(-2), 22));
        assertEquals(CampaignEventState.COMMITTED, fixture.eventState());
    }

    @Test
    void pendingEventRejectsOtherwiseValidReport() {
        Fixture fixture = new Fixture();
        fixture.state.eventState[fixture.row] =
                CampaignEventState.PENDING_CHOICE.toByte();

        assertEquals(CivilianRescueMissionResolution.Result.NOT_COMMITTED,
                CivilianRescueMissionResolution.apply(
                        fixture.state, fixture.outcome(60), 22));
        assertEquals(CampaignEventState.PENDING_CHOICE, fixture.eventState());
    }

    private static void assertResolved(int rescued) {
        Fixture fixture = new Fixture();

        CivilianRescueMissionResolution.Result result =
                CivilianRescueMissionResolution.apply(
                        fixture.state, fixture.outcome(rescued), 22);

        assertEquals(CivilianRescueMissionResolution.Result.RESOLVED, result);
        assertEquals(CampaignEventState.RESOLVED, fixture.eventState());
        assertEquals(rescued,
                fixture.state.eventCiviliansRescued[fixture.row]);
        assertEquals(22, fixture.state.eventResolvedTick[fixture.row]);
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final int market = state.marketRegistry.intern("market");
        final long eventId = CivilianRescueEvent.prepare(
                state, 77L, market, 20, 23, 40, 25, 120);
        final int row = state.eventIndex(eventId);

        Fixture() {
            state.eventState[row] = CampaignEventState.COMMITTED.toByte();
            state.eventDecisionTick[row] = 20;
        }

        CampaignEventState eventState() {
            return CampaignEventState.fromByte(state.eventState[row]);
        }

        MissionOutcome outcome(int rescued) {
            return outcome(CivilianRescueMissionKey.encode(eventId),
                    MissionSource.CAMPAIGN_EVENT, eventId, market,
                    120, rescued);
        }

        MissionOutcome outcome(String missionId, MissionSource source,
                               long outcomeEventId, int outcomeMarket,
                               int atRisk, int rescued) {
            return new MissionOutcome(false, missionId,
                    "Civilian Evacuation", MissionType.EXTRACTION,
                    RiskLevel.HIGH, source,
                    0, 0, 8, 2,
                    null, null, null, null,
                    0, 0f, null,
                    "Arcadia", null, "independent",
                    -1L, outcomeEventId, outcomeMarket, atRisk, rescued,
                    0, 0, 0);
        }
    }
}
