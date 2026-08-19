package com.dillon.starsectormarines.intel;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.ChainArchetype;
import com.dillon.starsectormarines.campaign.DefectorAsylumEvent;
import com.dillon.starsectormarines.campaign.DefectorAsylumOutcome;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefectorAsylumIntelTest {

    @Test
    void allThreeOpenStatesReconstructAsActive() {
        Fixture fixture = new Fixture();

        assertEquals(fixture.row,
                DefectorAsylumIntel.activeEventRow(fixture.state));

        fixture.state.eventState[fixture.row] =
                CampaignEventState.COMMITTED.toByte();
        assertEquals(fixture.row,
                DefectorAsylumIntel.activeEventRow(fixture.state));

        fixture.state.eventState[fixture.row] =
                CampaignEventState.PENDING_FOLLOWUP.toByte();
        assertEquals(fixture.row,
                DefectorAsylumIntel.activeEventRow(fixture.state));
    }

    @Test
    void choiceCopyUsesFrozenIdentitiesAndHidesTheLaterOfferInitially() {
        Fixture fixture = new Fixture();

        String initial = DefectorAsylumIntel.initialNarrative(
                "House Actor", "House Target", "event_market");
        String followup = DefectorAsylumIntel.followupNarrative(
                "House Actor", fixture.state.eventCreditsOffered[fixture.row]);

        assertTrue(initial.contains("House Actor"));
        assertTrue(initial.contains("House Target"));
        assertTrue(initial.contains("event_market"));
        assertTrue(initial.contains("discovered operation"));
        assertFalse(initial.contains("40000"));
        assertFalse(initial.toLowerCase().contains("promise"));
        assertTrue(followup.contains("40000 credits"));
        assertTrue(followup.contains("break that promise"));
    }

    @Test
    void deadlinesFollowTheCurrentChoiceStageAndClamp() {
        Fixture fixture = new Fixture();

        assertEquals(3, DefectorAsylumIntel.daysRemaining(
                fixture.state, fixture.row, 20));
        fixture.state.eventState[fixture.row] =
                CampaignEventState.PENDING_FOLLOWUP.toByte();
        fixture.state.eventFollowupDeadlineTick[fixture.row] = 34;
        assertEquals(2, DefectorAsylumIntel.daysRemaining(
                fixture.state, fixture.row, 32));
        assertEquals(0, DefectorAsylumIntel.daysRemaining(
                fixture.state, fixture.row, 40));
    }

    @Test
    void terminalCopyDistinguishesAllHonestOutcomes() {
        Fixture fixture = new Fixture();

        fixture.state.eventState[fixture.row] = CampaignEventState.REFUSED.toByte();
        assertTrue(DefectorAsylumIntel.terminalSummary(
                fixture.state, fixture.row).contains("refused"));

        fixture.state.eventState[fixture.row] = CampaignEventState.EXPIRED.toByte();
        assertTrue(DefectorAsylumIntel.terminalSummary(
                fixture.state, fixture.row).contains("Contact was lost"));

        fixture.state.eventState[fixture.row] = CampaignEventState.RESOLVED.toByte();
        fixture.state.eventDefectorOutcome[fixture.row] =
                DefectorAsylumOutcome.PROTECTED.toByte();
        assertTrue(DefectorAsylumIntel.terminalSummary(
                fixture.state, fixture.row).contains("kept its word"));

        fixture.state.eventDefectorOutcome[fixture.row] =
                DefectorAsylumOutcome.BETRAYED.toByte();
        assertTrue(DefectorAsylumIntel.terminalSummary(
                fixture.state, fixture.row).contains("House Actor"));
    }

    @Test
    void malformedResolutionFailsClosedAndIsNotSelected() {
        Fixture fixture = new Fixture();
        fixture.state.eventState[fixture.row] = CampaignEventState.RESOLVED.toByte();

        assertNull(DefectorAsylumIntel.terminalSummary(
                fixture.state, fixture.row));
        assertEquals(fixture.row,
                DefectorAsylumIntel.latestTerminalRow(fixture.state));
    }

    @Test
    void feedbackNeverExposesHiddenConsequences() {
        String protectedFeedback = DefectorAsylumIntel.feedback(
                DefectorAsylumEvent.Result.PROTECTED);
        String betrayedFeedback = DefectorAsylumIntel.feedback(
                DefectorAsylumEvent.Result.BETRAYED);

        assertFalse(protectedFeedback.toLowerCase().contains("integrity"));
        assertFalse(protectedFeedback.toLowerCase().contains("reputation"));
        assertFalse(betrayedFeedback.toLowerCase().contains("stewardship"));
        assertFalse(betrayedFeedback.toLowerCase().contains("progress"));
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final int market = state.marketRegistry.intern("event_market");
        final long actor = house(state, market, "House Actor");
        final long target = house(state, market, "House Target");
        final long chain = state.addAutonomousChain(actor, target, market, 7,
                (byte) 1, ChainArchetype.CONSOLIDATE_STAKE,
                (short) 45, (byte) 32, 1);
        final long event = DefectorAsylumEvent.prepare(state, 77L,
                chain, actor, target, market,
                20, 23, 20, 10, 40_000);
        final int row = state.eventIndex(event);
    }

    private static long house(CampaignState state, int market, String name) {
        return state.addHouse(market, 1, HouseFlavor.FEUDAL, HouseRank.TIER_1,
                HouseStatus.ACTIVE, PatronArchetype.NEWCOMER, name);
    }
}
