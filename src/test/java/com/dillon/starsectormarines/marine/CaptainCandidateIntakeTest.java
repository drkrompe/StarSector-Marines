package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptainCandidateIntakeTest {

    private static final String SOURCE = "derelict:salvage_entity_17";

    @Test
    void discoveryFreezesOneCandidatePerNormalizedSource() {
        MarineRoster roster = new MarineRoster();

        CaptainCandidate first = roster.discoverCaptainCandidate(
                "  " + SOURCE + "  ", "Mara Venn", "portrait_1",
                Rank.CORPORAL, Trait.SALVAGE_EXPERT, 73.5f);
        CaptainCandidate replay = roster.discoverCaptainCandidate(
                SOURCE, "Replacement Name", "portrait_2",
                Rank.GENERAL, Trait.VETERAN, 90f);

        assertSame(first, replay);
        assertEquals(1, roster.captainCandidates().size());
        assertEquals("Mara Venn", first.name());
        assertEquals("portrait_1", first.portraitSprite());
        assertEquals(Rank.CORPORAL, first.startingRank());
        assertEquals(Trait.SALVAGE_EXPERT, first.startingTrait());
        assertEquals(73.5f, first.discoveredAtDay());
        assertEquals(CaptainCandidateState.AVAILABLE, first.state());
    }

    @Test
    void acceptanceCreatesOneFrozenCaptainAndReplaysSafely() {
        MarineRoster roster = new MarineRoster();
        CaptainCandidate candidate = roster.discoverCaptainCandidate(
                SOURCE, "Mara Venn", "portrait_1",
                Rank.CORPORAL, Trait.SALVAGE_EXPERT, 73.5f);

        MarineCaptain accepted = roster.acceptCaptainCandidate(SOURCE);
        MarineCaptain replay = roster.acceptCaptainCandidate(SOURCE);

        assertSame(accepted, replay);
        assertEquals(1, roster.size());
        assertEquals(candidate.id(), accepted.id());
        assertEquals("Mara Venn", accepted.name());
        assertEquals("portrait_1", accepted.portraitSprite());
        assertEquals(Rank.CORPORAL, accepted.rank());
        assertEquals(Status.ACTIVE, accepted.status());
        assertEquals(List.of(Trait.SALVAGE_EXPERT), accepted.traits());
        assertEquals(73.5f, accepted.createdAtDay());
        assertEquals(CaptainCandidateState.ACCEPTED, candidate.state());
        assertTrue(roster.availableCaptainCandidates().isEmpty());
    }

    @Test
    void fullRosterLeavesCandidateAvailableUntilAdmissionCanSucceed() {
        MarineRoster roster = new MarineRoster();
        roster.setCapacity(1);
        roster.add(new MarineCaptain("Incumbent", null, Rank.PRIVATE, 0f));
        CaptainCandidate candidate = roster.discoverCaptainCandidate(
                SOURCE, "Mara Venn", null, Rank.PRIVATE, null, 12f);

        assertNull(roster.acceptCaptainCandidate(SOURCE));
        assertEquals(1, roster.size());
        assertEquals(CaptainCandidateState.AVAILABLE, candidate.state());

        roster.setCapacity(2);
        assertEquals(candidate.id(), roster.acceptCaptainCandidate(SOURCE).id());
        assertEquals(2, roster.size());
    }

    @Test
    void declineIsIrreversibleAndDoesNotConsumeCapacity() {
        MarineRoster roster = new MarineRoster();
        CaptainCandidate candidate = roster.discoverCaptainCandidate(
                SOURCE, "Mara Venn", null, Rank.PRIVATE, null, 12f);

        assertTrue(roster.declineCaptainCandidate(SOURCE));
        assertFalse(roster.declineCaptainCandidate(SOURCE));
        assertNull(roster.acceptCaptainCandidate(SOURCE));
        assertEquals(0, roster.size());
        assertEquals(CaptainCandidateState.DECLINED, candidate.state());
    }

    @Test
    void discoveryRejectsMalformedOrMoralOutlookOffers() {
        MarineRoster roster = new MarineRoster();

        assertNull(roster.discoverCaptainCandidate(
                "not-namespaced", "Mara", null, Rank.PRIVATE, null, 1f));
        assertNull(roster.discoverCaptainCandidate(
                "derelict:", "Mara", null, Rank.PRIVATE, null, 1f));
        assertNull(roster.discoverCaptainCandidate(
                SOURCE, "  ", null, Rank.PRIVATE, null, 1f));
        assertNull(roster.discoverCaptainCandidate(
                SOURCE, "Mara", null, null, null, 1f));
        assertNull(roster.discoverCaptainCandidate(
                SOURCE, "Mara", null, Rank.PRIVATE, Trait.IDEALIST, 1f));
        assertNull(roster.discoverCaptainCandidate(
                SOURCE, "Mara", null, Rank.PRIVATE, Trait.CYNICAL, 1f));
        assertNull(roster.discoverCaptainCandidate(
                SOURCE, "Mara", null, Rank.PRIVATE, null, Float.NaN));
        assertTrue(roster.captainCandidates().isEmpty());
    }

    @Test
    void candidateViewsCannotMutateRosterAuthority() {
        MarineRoster roster = new MarineRoster();
        roster.discoverCaptainCandidate(
                SOURCE, "Mara Venn", null, Rank.PRIVATE, null, 12f);

        assertThrows(UnsupportedOperationException.class,
                () -> roster.captainCandidates().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> roster.availableCaptainCandidates().clear());
    }

    @Test
    void legacySaveBackfillsAnEmptyCandidateCollection() throws Exception {
        MarineRoster roster = new MarineRoster();
        setField(roster, "captainCandidates", null);

        readResolve(roster);

        assertTrue(roster.captainCandidates().isEmpty());
        assertNull(roster.captainCandidateBySource(SOURCE));
    }

    @Test
    void repairKeepsFirstValidSourceAndAdoptsExistingCaptain() throws Exception {
        MarineRoster roster = new MarineRoster();
        CaptainCandidate first = new CaptainCandidate(
                SOURCE, "First", null, Rank.PRIVATE, null, 1f);
        CaptainCandidate duplicate = new CaptainCandidate(
                SOURCE, "Duplicate", null, Rank.CAPTAIN, Trait.VETERAN, 2f);
        List<CaptainCandidate> persisted = new ArrayList<>();
        persisted.add(first);
        persisted.add(duplicate);
        setField(roster, "captainCandidates", persisted);
        MarineCaptain existing = new MarineCaptain(
                first.id(), first.name(), null, first.startingRank(), 1f);
        roster.add(existing);

        readResolve(roster);

        assertEquals(1, roster.captainCandidates().size());
        assertSame(first, roster.captainCandidates().get(0));
        assertEquals(CaptainCandidateState.ACCEPTED, first.state());
        assertSame(existing, roster.acceptCaptainCandidate(SOURCE));
        assertEquals(1, roster.size());
    }

    @Test
    void repairDoesNotInventCaptainForDanglingAcceptedCandidate() throws Exception {
        MarineRoster roster = new MarineRoster();
        CaptainCandidate candidate = roster.discoverCaptainCandidate(
                SOURCE, "Mara Venn", null, Rank.PRIVATE, null, 12f);
        candidate.markAccepted();

        readResolve(roster);

        assertEquals(CaptainCandidateState.ACCEPTED, candidate.state());
        assertNull(roster.acceptCaptainCandidate(SOURCE));
        assertEquals(0, roster.size());
    }

    private static void readResolve(MarineRoster roster) throws Exception {
        Method method = MarineRoster.class.getDeclaredMethod("readResolve");
        method.setAccessible(true);
        method.invoke(roster);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
