package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.SilentColonyEvent;
import com.dillon.starsectormarines.campaign.SilentColonyMissionKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SilentColonyMissionFactoryTest {

    @Test
    void committedEventBuildsStableZeroEconomyMissionWithFrozenThreat() {
        Fixture fixture = new Fixture();

        Mission first = SilentColonyMissionFactory.create(
                fixture.state, fixture.eventId, fixture.market,
                "Eidolon", "neutral");
        Mission replay = SilentColonyMissionFactory.create(
                fixture.state, fixture.eventId, fixture.market,
                "Eidolon", "neutral");

        assertNotNull(first);
        assertEquals(first.id, replay.id);
        assertEquals(first.normalizedX, replay.normalizedX);
        assertEquals(first.normalizedY, replay.normalizedY);
        assertEquals(MissionSource.CAMPAIGN_EVENT, first.source);
        assertEquals(MissionType.EXTRACTION, first.type);
        assertEquals(fixture.eventId, first.campaignEventId);
        assertEquals(fixture.market, first.campaignEventMarketId);
        assertEquals(10, first.civiliansAtRisk);
        assertEquals(9_876L, first.campaignEventThreatSeed);
        assertEquals(0, first.payout);
        assertEquals(0, first.salvageNegotiated & 0xFF);
    }

    @Test
    void localEmissionRequiresTheFrozenSiteAndCommittedState() {
        Fixture fixture = new Fixture();

        Mission mission = SilentColonyLocalMission.find(
                fixture.state, "dead-site", "Eidolon", "neutral");

        assertNotNull(mission);
        assertEquals(fixture.eventId,
                SilentColonyMissionKey.parse(mission.id).eventId);
        assertNull(SilentColonyLocalMission.find(
                fixture.state, "elsewhere", "Elsewhere", "neutral"));

        fixture.state.eventState[fixture.row] =
                CampaignEventState.PENDING_CHOICE.toByte();
        assertNull(SilentColonyLocalMission.find(
                fixture.state, "dead-site", "Eidolon", "neutral"));
    }

    @Test
    void missionKeyRejectsForeignAndInvalidLineage() {
        assertEquals(42L,
                SilentColonyMissionKey.parse("silent-colony:42").eventId);
        assertNull(SilentColonyMissionKey.parse("silent-colony:0"));
        assertNull(SilentColonyMissionKey.parse("silent-colony:nope"));
        assertNull(SilentColonyMissionKey.parse("civilian-rescue:42"));
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final int market = state.marketRegistry.intern("dead-site");
        final long eventId = SilentColonyEvent.prepare(state, 77L, market,
                90, 93, 50, 25, 10, 9_876L);
        final int row = state.eventIndex(eventId);

        Fixture() {
            state.eventState[row] = CampaignEventState.COMMITTED.toByte();
            state.eventDecisionTick[row] = 90;
        }
    }
}
