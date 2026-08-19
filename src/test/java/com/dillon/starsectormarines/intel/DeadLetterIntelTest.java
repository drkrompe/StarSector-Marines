package com.dillon.starsectormarines.intel;

import com.dillon.starsectormarines.campaign.AbandonedColonyArchiveOutcome;
import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.SilentColonyEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadLetterIntelTest {

    @Test
    void intelStaysHiddenUntilAValidSignalExists() {
        assertTrue(DeadLetterIntel.shouldHide(null));
        assertTrue(DeadLetterIntel.shouldHide(new CampaignState()));

        Fixture fixture = new Fixture();
        assertFalse(DeadLetterIntel.shouldHide(fixture.state));

        fixture.state.eventColonyThreatSeed[fixture.row] = -1L;
        assertTrue(DeadLetterIntel.shouldHide(fixture.state));
    }

    @Test
    void pendingAndCommittedRowsReconstructAsActive() {
        Fixture fixture = new Fixture();

        assertEquals(fixture.row,
                DeadLetterIntel.activeEventRow(fixture.state));
        fixture.state.eventState[fixture.row] =
                CampaignEventState.COMMITTED.toByte();
        assertEquals(fixture.row,
                DeadLetterIntel.activeEventRow(fixture.state));
    }

    @Test
    void initialCopyNamesOnlyKnownSiteAndUncertainty() {
        String narrative = DeadLetterIntel.initialNarrative("Hesperus Ruins");

        assertTrue(narrative.contains("Hesperus Ruins"));
        assertTrue(narrative.contains("cannot confirm survivors"));
        assertTrue(narrative.contains("what ended the colony"));
        assertFalse(narrative.contains("991"));
        assertFalse(narrative.toLowerCase().contains("archive"));
        assertFalse(narrative.toLowerCase().contains("automated threat"));
    }

    @Test
    void deadlineClampsAndTerminalSilenceDoesNotInventFacts() {
        Fixture fixture = new Fixture();
        assertEquals(3, DeadLetterIntel.daysRemaining(
                fixture.state, fixture.row, 20));
        assertEquals(0, DeadLetterIntel.daysRemaining(
                fixture.state, fixture.row, 30));

        fixture.state.eventState[fixture.row] =
                CampaignEventState.REFUSED.toByte();
        String refused = DeadLetterIntel.terminalSummary(
                fixture.state, fixture.row);
        assertTrue(refused.contains("left unanswered"));
        assertTrue(refused.contains("Nothing further is known"));

        fixture.state.eventState[fixture.row] =
                CampaignEventState.EXPIRED.toByte();
        String expired = DeadLetterIntel.terminalSummary(
                fixture.state, fixture.row);
        assertTrue(expired.contains("faded"));
        assertFalse(expired.toLowerCase().contains("survivor"));
    }

    @Test
    void resolvedSummaryRequiresExplicitBoundedDualReport() {
        Fixture fixture = new Fixture();
        fixture.state.eventState[fixture.row] =
                CampaignEventState.RESOLVED.toByte();
        fixture.state.eventResolvedTick[fixture.row] = 30;

        assertNull(DeadLetterIntel.terminalSummary(
                fixture.state, fixture.row));
        assertEquals(-1, DeadLetterIntel.latestTerminalRow(fixture.state));

        fixture.state.eventColonyArchiveOutcome[fixture.row] =
                AbandonedColonyArchiveOutcome.RECOVERED.toByte();
        fixture.state.eventCiviliansRescued[fixture.row] = 4;
        String summary = DeadLetterIntel.terminalSummary(
                fixture.state, fixture.row);
        assertTrue(summary.contains("4 of 8 survivors"));
        assertTrue(summary.contains("archive was recovered"));

        fixture.state.eventCiviliansRescued[fixture.row] = 9;
        assertNull(DeadLetterIntel.terminalSummary(
                fixture.state, fixture.row));
    }

    @Test
    void feedbackDoesNotPreviewThreatRewardOrMoralMeaning() {
        String committed = DeadLetterIntel.feedback(
                SilentColonyEvent.Result.COMMITTED);
        String poor = DeadLetterIntel.feedback(
                SilentColonyEvent.Result.INSUFFICIENT_RESOURCES);

        assertTrue(committed.contains("field team is committed"));
        assertTrue(poor.contains("nothing was transferred"));
        assertFalse(committed.toLowerCase().contains("archive"));
        assertFalse(committed.toLowerCase().contains("reward"));
        assertFalse(committed.toLowerCase().contains("integrity"));
        assertFalse(committed.toLowerCase().contains("threat"));
    }

    private static final class Fixture {
        final CampaignState state = new CampaignState();
        final int market = state.marketRegistry.intern("hesperus_ruins");
        final long event = SilentColonyEvent.prepare(state, 77L, market,
                20, 23, 40, 25, 8, 991L);
        final int row = state.eventIndex(event);
    }
}
