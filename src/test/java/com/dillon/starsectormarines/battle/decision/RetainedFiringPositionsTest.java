package com.dillon.starsectormarines.battle.decision;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RetainedFiringPositionsTest {
    private static RetainedFiringPositions.Key key(long target) {
        return new RetainedFiringPositions.Key(target, "order", "goal", true, 2, 20f, 0f, 0f, true);
    }

    @Test void validPositionSurvivesHundredsOfTicksWithoutSlidingItsExpiry() {
        var cache = new RetainedFiringPositions(16);
        cache.remember(1L, key(2L), 10, 4, 5);
        AtomicInteger checks = new AtomicInteger();
        for (int tick = 11; tick < 311; tick++) {
            assertEquals(new RetainedFiringPositions.Cell(4, 5), cache.lookup(1L, key(2L), tick,
                    cell -> { checks.incrementAndGet(); return true; }));
        }
        assertEquals(300, checks.get());
        assertNull(cache.lookup(1L, key(2L), 311, cell -> true));
    }

    @Test void invalidCellIsRetiredImmediatelyAndNeverResurrected() {
        var cache = new RetainedFiringPositions(16);
        cache.remember(1L, key(2L), 10, 4, 5);
        assertNull(cache.lookup(1L, key(2L), 11, cell -> false));
        assertNull(cache.lookup(1L, key(2L), 12, cell -> true));
    }

    @Test void changedTargetOrOrderOrConstraintsCannotReuseOldChoice() {
        var original = key(2L);
        var changed = new RetainedFiringPositions.Key[]{key(3L),
                new RetainedFiringPositions.Key(2, "new-order", "goal", true, 2, 20, 0, 0, true),
                new RetainedFiringPositions.Key(2, "order", "new-goal", true, 2, 20, 0, 0, true),
                new RetainedFiringPositions.Key(2, "order", "goal", true, 3, 20, 0, 0, true),
                new RetainedFiringPositions.Key(2, "order", "goal", true, 2, 10, 0, 0, true),
                new RetainedFiringPositions.Key(2, "order", "goal", false, 2, 20, 0, 0, true)};
        for (var replacement : changed) {
            var cache = new RetainedFiringPositions(16);
            cache.remember(1L, original, 10, 4, 5);
            assertNull(cache.lookup(1L, replacement, 11, cell -> fail("key mismatch must not validate")));
        }
    }

    @Test void fixedCapacityCollisionsLoseReuseButNeverReturnAnotherMembersCell() {
        var cache = new RetainedFiringPositions(1);
        cache.remember(1L, key(2L), 10, 4, 5);
        cache.remember(3L, key(2L), 10, 8, 9);
        assertNull(cache.lookup(1L, key(2L), 11, cell -> true));
        assertEquals(new RetainedFiringPositions.Cell(8, 9), cache.lookup(3L, key(2L), 11, cell -> true));
    }

    @Test void separateBattlesAndRewoundTimeDoNotShareChoices() {
        var cache = new RetainedFiringPositions(16);
        cache.remember(1L, key(2L), 10, 4, 5);
        assertNull(new RetainedFiringPositions(16).lookup(1L, key(2L), 11, cell -> true));
        assertNull(cache.lookup(1L, key(2L), 9, cell -> true));
    }

    @Test void rejectedApproachIsForgottenWithoutEvictingAnUnrelatedCollision() {
        var cache = new RetainedFiringPositions(1);
        cache.remember(1L, key(2L), 10, 4, 5);
        cache.forget(1L);
        assertNull(cache.lookup(1L, key(2L), 11, cell -> true));
        cache.remember(3L, key(2L), 10, 8, 9);
        cache.forget(1L);
        assertNotNull(cache.lookup(3L, key(2L), 11, cell -> true));
    }
}
