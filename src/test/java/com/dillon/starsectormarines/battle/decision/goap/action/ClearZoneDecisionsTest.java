package com.dillon.starsectormarines.battle.decision.goap.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClearZoneDecisionsTest {
    private final Object squad = new Object();
    private final Object assignment = new Object();
    private final Object goal = new Object();

    private void remember(ClearZoneDecisions cache, long member, boolean negative) {
        cache.remember(member, squad, assignment, goal, 7, 100, 2, 3, 9, 8, 3, 10f, negative);
    }

    private ClearZoneDecisions.Entry lookup(ClearZoneDecisions cache, long member, int tick, long target) {
        return cache.lookup(member, squad, assignment, goal, 7, tick, 2, 3, target, 10f);
    }

    @Test void positiveCadenceDoesNotSlideAndNegativeRetryIsShorter() {
        var cache = new ClearZoneDecisions(64);
        remember(cache, 42, false); // 30 + 42 % 7 == 30
        var entry = lookup(cache, 42, 100, 9);
        for (int tick = 101; tick < 130; tick++) assertSame(entry, lookup(cache, 42, tick, 9));
        assertNull(lookup(cache, 42, 130, 9));
        remember(cache, 42, true); // 15 + 42 % 6 == 15
        for (int tick = 100; tick < 115; tick++) assertNotNull(lookup(cache, 42, tick, 0));
        assertNull(lookup(cache, 42, 115, 0));
    }

    @Test void negativeIdentityNeverReplacesAnotherExternallySelectedTarget() {
        var cache = new ClearZoneDecisions(64);
        remember(cache, 42, true);
        assertNotNull(lookup(cache, 42, 101, 9));
        assertNull(lookup(cache, 42, 102, 10));
        assertNull(lookup(cache, 42, 103, 0), "invalidated entries do not resurrect");
        remember(cache, 42, false);
        assertNull(lookup(cache, 42, 101, 0), "positive decisions cannot resurrect a cleared target");
    }

    @Test void changesToMissionTopologyRangeAndTimeInvalidateBothKinds() {
        for (boolean negative : new boolean[]{false, true}) {
            var cache = new ClearZoneDecisions(64);
            remember(cache, 42, negative);
            assertNull(cache.lookup(42, new Object(), assignment, goal, 7, 101, 2, 3, 9, 10f));
            remember(cache, 42, negative);
            assertNull(cache.lookup(42, squad, new Object(), goal, 7, 101, 2, 3, 9, 10f));
            remember(cache, 42, negative);
            assertNull(cache.lookup(42, squad, assignment, new Object(), 7, 101, 2, 3, 9, 10f));
            remember(cache, 42, negative);
            assertNull(cache.lookup(42, squad, assignment, goal, 8, 101, 2, 3, 9, 10f));
            remember(cache, 42, negative);
            assertNull(cache.lookup(42, squad, assignment, goal, 7, 101, 2, 3, 9, 11f));
            remember(cache, 42, negative);
            assertNull(lookup(cache, 42, 99, 9));
        }
    }

    @Test void sourceAndTargetDisplacementHaveExplicitTwoCellThresholds() {
        var cache = new ClearZoneDecisions(64);
        remember(cache, 42, true);
        var entry = cache.lookup(42, squad, assignment, goal, 7, 101, 3, 4, 0, 10f);
        assertNotNull(entry);
        assertFalse(entry.targetMoved(9, 4));
        assertTrue(entry.targetMoved(10, 3));
        assertTrue(entry.targetMoved(8, 1));
        assertNull(cache.lookup(42, squad, assignment, goal, 7, 102, 4, 3, 0, 10f));
    }

    @Test void collisionsAndLateInvalidationNeverExposeOrRemoveAnotherEntry() {
        var cache = new ClearZoneDecisions(1);
        remember(cache, 42, true);
        var old = lookup(cache, 42, 101, 0);
        remember(cache, 43, false);
        assertNull(lookup(cache, 42, 101, 0));
        cache.forget(old);
        assertNotNull(lookup(cache, 43, 101, 9));
    }

    @Test void capacityMustBePositivePowerOfTwo() {
        assertThrows(IllegalArgumentException.class, () -> new ClearZoneDecisions(0));
        assertThrows(IllegalArgumentException.class, () -> new ClearZoneDecisions(3));
    }
}
