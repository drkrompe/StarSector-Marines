package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivilianEvacuationTrackerTest {

    @Test
    void registrationIsBoundedIdentityUniqueAndPreSeal() {
        CivilianEvacuationTracker tracker = new CivilianEvacuationTracker(2);

        assertFalse(tracker.register(0L));
        assertTrue(tracker.register(41L));
        assertFalse(tracker.register(41L));
        assertTrue(tracker.register(42L));
        assertFalse(tracker.register(43L));
        assertEquals(2, tracker.registeredCount());
        assertEquals(2, tracker.activeCount());
    }

    @Test
    void transitionsAreOneWayAndReplaySafe() {
        CivilianEvacuationTracker tracker = fullTracker(3);

        assertTrue(tracker.markEvacuated(1L));
        assertFalse(tracker.markEvacuated(1L));
        assertFalse(tracker.markLost(1L));
        assertTrue(tracker.markLost(2L));
        assertFalse(tracker.markEvacuated(99L));

        assertEquals(CivilianEvacuationTracker.State.EVACUATED,
                tracker.state(1L));
        assertEquals(CivilianEvacuationTracker.State.LOST,
                tracker.state(2L));
        assertEquals(CivilianEvacuationTracker.State.ACTIVE,
                tracker.state(3L));
        assertNull(tracker.state(99L));
        assertEquals(1, tracker.activeCount());
        assertEquals(1, tracker.evacuatedCount());
        assertEquals(1, tracker.lostCount());
    }

    @Test
    void onlyACompleteSealedCohortProducesAReport() {
        CivilianEvacuationTracker tracker = new CivilianEvacuationTracker(3);
        tracker.register(1L);
        tracker.register(2L);

        assertFalse(tracker.seal());
        assertFalse(tracker.isSealed());
        assertNull(tracker.report());

        tracker.register(3L);
        tracker.markEvacuated(1L);
        assertTrue(tracker.seal());
        assertTrue(tracker.seal());
        assertFalse(tracker.markEvacuated(2L));
        assertFalse(tracker.register(4L));

        CivilianEvacuationReport report = tracker.report();
        assertEquals(3, report.initial);
        assertEquals(1, report.evacuated);
        assertEquals(2, report.lost);
        assertEquals(0, tracker.activeCount());
    }

    @Test
    void campaignScalingFloorsPartialAndPreservesExactFullRescue() {
        CivilianEvacuationTracker partial = fullTracker(8);
        partial.markEvacuated(1L);
        partial.markEvacuated(2L);
        partial.markEvacuated(3L);
        partial.seal();

        assertEquals(37, partial.report().campaignRescued(100));
        assertEquals(805_306_367,
                partial.report().campaignRescued(Integer.MAX_VALUE));
        assertEquals(-1, partial.report().campaignRescued(-1));

        CivilianEvacuationTracker full = fullTracker(8);
        for (long id = 1L; id <= 8L; id++) full.markEvacuated(id);
        full.seal();
        assertEquals(Integer.MAX_VALUE,
                full.report().campaignRescued(Integer.MAX_VALUE));
    }

    @Test
    void expectedCountMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> new CivilianEvacuationTracker(0));
    }

    private static CivilianEvacuationTracker fullTracker(int size) {
        CivilianEvacuationTracker tracker =
                new CivilianEvacuationTracker(size);
        for (long id = 1L; id <= size; id++) {
            assertTrue(tracker.register(id));
        }
        return tracker;
    }
}
