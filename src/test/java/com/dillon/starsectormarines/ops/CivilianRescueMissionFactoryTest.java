package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CivilianRescueEvent;
import com.dillon.starsectormarines.campaign.CivilianRescueMissionKey;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CivilianRescueMissionFactoryTest {

    @Test
    void legacyMissionConstructorKeepsEventLineageEmpty() {
        Mission mission = new Mission("id", "name", MissionType.ASSAULT,
                MissionSource.GENERATED, 100, RiskLevel.LOW,
                "requirements", "flavor", 0.5f, 0.5f,
                FlybyRoster.EMPTY, FlybyRoster.EMPTY, 2, 0,
                "planet", null, null, -1L,
                (byte) 0, (byte) 0, (byte) 100,
                Collections.emptyList());

        assertEquals(-1L, mission.campaignEventId);
        assertEquals(-1, mission.campaignEventMarketId);
        assertEquals(0, mission.civiliansAtRisk);
    }

    @Test
    void committedEventBuildsStableZeroEconomyMission() {
        Fixture fixture = new Fixture();

        Mission first = CivilianRescueMissionFactory.create(
                fixture.state, fixture.eventId, fixture.market,
                "Arcadia", "independent");
        Mission replay = CivilianRescueMissionFactory.create(
                fixture.state, fixture.eventId, fixture.market,
                "Arcadia", "independent");

        assertNotNull(first);
        assertEquals(first.id, replay.id);
        assertEquals(first.normalizedX, replay.normalizedX);
        assertEquals(first.normalizedY, replay.normalizedY);
        assertEquals(MissionSource.CAMPAIGN_EVENT, first.source);
        assertEquals(MissionType.EXTRACTION, first.type);
        assertEquals(-1L, first.contractId);
        assertEquals(fixture.eventId, first.campaignEventId);
        assertEquals(fixture.market, first.campaignEventMarketId);
        assertEquals(120, first.civiliansAtRisk);
        assertEquals(0, first.payout);
        assertEquals(0, first.salvageBaseline & 0xFF);
        assertEquals(0, first.salvageNegotiated & 0xFF);
    }

    @Test
    void factoryRejectsWrongMarketAndNonCommittedStates() {
        Fixture fixture = new Fixture();

        assertNull(CivilianRescueMissionFactory.create(
                fixture.state, fixture.eventId, fixture.market + 1,
                "Arcadia", "independent"));

        fixture.state.eventState[fixture.row] =
                CampaignEventState.PENDING_CHOICE.toByte();
        assertNull(CivilianRescueMissionFactory.create(
                fixture.state, fixture.eventId, fixture.market,
                "Arcadia", "independent"));
    }

    @Test
    void missionKeyRoundTripsOnlyPositiveEventIds() {
        String encoded = CivilianRescueMissionKey.encode(42L);

        assertEquals(42L, CivilianRescueMissionKey.parse(encoded).eventId);
        assertNull(CivilianRescueMissionKey.parse("civilian-rescue:0"));
        assertNull(CivilianRescueMissionKey.parse("civilian-rescue:nope"));
        assertNull(CivilianRescueMissionKey.parse("contract:42"));
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
    }
}
