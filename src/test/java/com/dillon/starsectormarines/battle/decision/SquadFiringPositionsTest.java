package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.decision.SquadFiringPositions.Candidate;
import com.dillon.starsectormarines.battle.decision.SquadFiringPositions.Key;
import com.dillon.starsectormarines.battle.decision.SquadFiringPositions.Outcome;
import com.dillon.starsectormarines.battle.decision.SquadFiringPositions.Result;
import com.dillon.starsectormarines.battle.decision.SquadFiringPositions.RefreshReason;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadFiringPositionsTest {
    private static final Key KEY = key(10L);
    private static final Candidate A = new Candidate(0, 0, 0f);
    private static final Candidate B = new Candidate(5, 0, 0f);
    private static final Candidate C = new Candidate(10, 0, 0f);

    @Test
    void squadSharesPoolAndKeepsUniqueStableMemberAssignments() {
        SquadFiringPositions cache = new SquadFiringPositions(0);
        AtomicInteger builds = new AtomicInteger();
        Supplier<List<Candidate>> generator = () -> {
            builds.incrementAndGet();
            return List.of(A, B, C);
        };
        Result first = request(cache, 1, 1, KEY, generator, c -> true, c -> c.x(), true);
        Result second = request(cache, 2, 1, KEY, generator, c -> true, c -> c.x(), true);
        assertEquals(A, first.position());
        assertTrue(first.built());
        assertEquals(RefreshReason.COLD, first.refreshReason());
        assertTrue(first.assigned());
        assertEquals(B, second.position());
        assertFalse(second.built());
        assertEquals(RefreshReason.NONE, second.refreshReason());
        // A cheaper alternative must not shuffle an incumbent who remains legal.
        Result hit = request(cache, 2, 2, KEY, generator, c -> true, c -> -c.x(), true);
        assertEquals(B, hit.position());
        assertEquals(Outcome.HIT, hit.outcome());
        assertFalse(hit.assigned());
        assertEquals(1, builds.get());
    }

    @Test
    void reservedNeighborsSpreadAssignmentsAcrossContexts() {
        SquadFiringPositions cache = new SquadFiringPositions(0);
        request(cache, 1, 1, KEY, () -> List.of(A), c -> true, c -> 0, true);
        Candidate adjacent = new Candidate(1, 0, 0f);
        Candidate distant = new Candidate(4, 0, 0f);
        Result result = request(cache, 2, 1, key(20), () -> List.of(A, adjacent, distant),
                c -> true, c -> c.x(), true);
        assertEquals(distant, result.position(), "adjacent costs 1+4; distant costs 4");
    }

    @Test
    void memberContextChangeAndExplicitDepartureReleaseReservations() {
        SquadFiringPositions cache = new SquadFiringPositions(0);
        request(cache, 1, 1, KEY, () -> List.of(A), c -> true, c -> 0, true);
        Result changed = request(cache, 1, 2, key(20), () -> List.of(B), c -> true, c -> 0, true);
        assertEquals(RefreshReason.KEY_CHANGE, changed.refreshReason());
        assertEquals(A, request(cache, 2, 2, KEY, () -> List.of(A),
                c -> true, c -> 0, true).position());
        cache.release(1);
        assertEquals(B, request(cache, 3, 2, key(20), () -> List.of(B),
                c -> true, c -> 0, true).position());
    }

    @Test
    void departedMembersExpireEvenWhileOthersKeepTheirPoolAlive() {
        SquadFiringPositions cache = new SquadFiringPositions(0);
        request(cache, 1, 1, KEY, () -> List.of(A, B), c -> true, c -> c.x(), true);
        request(cache, 2, 10, KEY, () -> List.of(A, B), c -> true, c -> c.x(), true);
        assertEquals(A, request(cache, 3, 13, KEY, () -> List.of(A, B),
                c -> true, c -> c.x(), true).position());
        assertEquals(B, request(cache, 2, 13, KEY, () -> List.of(A, B),
                c -> true, c -> c.x(), true).position());
    }

    @Test
    void softRefreshDeniedRetainsOnlyStillValidIncumbent() {
        SquadFiringPositions cache = new SquadFiringPositions(0);
        request(cache, 1, 1, KEY, () -> List.of(A), c -> true, c -> 0, true);
        request(cache, 1, 12, KEY, () -> List.of(A), c -> true, c -> 0, true);
        Result deferred = request(cache, 1, 13, KEY, () -> {
            throw new AssertionError("denied refresh ran generator");
        }, c -> true, c -> 0, false);
        assertEquals(Outcome.DEFERRED, deferred.outcome());
        assertEquals(RefreshReason.TTL, deferred.refreshReason());
        assertEquals(A, deferred.position());
        assertFalse(deferred.built());
        assertNull(request(cache, 2, 13, KEY, List::of,
                c -> true, c -> 0, false).position());
        assertNull(request(cache, 1, 14, KEY, List::of,
                c -> false, c -> 0, false).position());
    }

    @Test
    void successfulSoftRebuildPreservesIncumbentsMissingFromNewPool() {
        SquadFiringPositions cache = new SquadFiringPositions(0);
        request(cache, 1, 1, KEY, () -> List.of(A), c -> true, c -> 0, true);
        request(cache, 1, 12, KEY, () -> List.of(A), c -> true, c -> 0, true);
        Result rebuilt = request(cache, 1, 13, KEY, () -> List.of(B),
                c -> true, c -> 0, true);
        assertTrue(rebuilt.built());
        assertEquals(RefreshReason.TTL, rebuilt.refreshReason());
        assertEquals(Outcome.HIT, rebuilt.outcome());
        assertEquals(A, rebuilt.position());
        assertEquals(B, request(cache, 2, 13, KEY, () -> List.of(B),
                c -> true, c -> 0, true).position());
    }

    @Test
    void targetCentroidEpochAndTopologyChangesCannotRetainOldPosition() {
        for (int cause = 0; cause < 4; cause++) {
            SquadFiringPositions cache = new SquadFiringPositions(0);
            request(cache, 1, 1, KEY, () -> List.of(A), c -> true, c -> 0, true);
            Result result = cache.request(1, 2, cause == 2 ? 2 : 1,
                    cause == 3 ? 2 : 1, cause == 0 ? 1f : 0f, 0f,
                    cause == 1 ? 2f : 0f, 0f, KEY,
                    () -> { throw new AssertionError("denied refresh generated"); },
                    c -> true, c -> 0, () -> false);
            assertEquals(Outcome.DEFERRED, result.outcome());
            RefreshReason expected = switch (cause) {
                case 0 -> RefreshReason.TARGET_MOVED;
                case 1 -> RefreshReason.SQUAD_MOVED;
                case 2 -> RefreshReason.EPOCH;
                default -> RefreshReason.TOPOLOGY;
            };
            assertEquals(expected, result.refreshReason());
            assertNull(result.position(), "hard invalidation cause " + cause);
            Result retried = cache.request(1, 3, cause == 2 ? 2 : 1,
                    cause == 3 ? 2 : 1, cause == 0 ? 1f : 0f, 0f,
                    cause == 1 ? 2f : 0f, 0f, KEY,
                    () -> List.of(B), c -> true, c -> 0, () -> true);
            assertEquals(expected, retried.refreshReason(), "reason survives budget deferral");
            assertTrue(retried.built());
        }
    }

    @Test
    void subthresholdMovementRevalidatesRatherThanRebuilds() {
        SquadFiringPositions cache = new SquadFiringPositions(0);
        request(cache, 1, 1, KEY, () -> List.of(A), c -> true, c -> 0, true);
        AtomicInteger validations = new AtomicInteger();
        Result hit = cache.request(1, 2, 1, 1, 0.5f, 0f, 1.5f, 0f, KEY,
                () -> { throw new AssertionError("subthreshold rebuild"); },
                c -> { validations.incrementAndGet(); return true; }, c -> 0, () -> false);
        assertEquals(A, hit.position());
        assertEquals(Outcome.HIT, hit.outcome());
        assertEquals(1, validations.get());
    }

    @Test
    void negativeMemberAnswerBacksOffEvenWhenPoolHasCandidates() {
        SquadFiringPositions cache = new SquadFiringPositions(0);
        AtomicInteger validations = new AtomicInteger();
        Predicate<Candidate> invalid = c -> { validations.incrementAndGet(); return false; };
        assertEquals(Outcome.NEGATIVE, request(cache, 1, 1, KEY,
                () -> List.of(A, B), invalid, c -> 0, true).outcome());
        assertEquals(2, validations.get());
        request(cache, 1, 4, KEY, () -> List.of(A, B), invalid, c -> 0, true);
        assertEquals(2, validations.get());
        request(cache, 1, 5, KEY, () -> List.of(A, B), invalid, c -> 0, true);
        assertEquals(4, validations.get());
    }

    @Test
    void emptyPoolUsesShortTtlAndSquadOffsetStaggersPositiveRefresh() {
        SquadFiringPositions cache = new SquadFiringPositions(5);
        AtomicInteger builds = new AtomicInteger();
        Supplier<List<Candidate>> empty = () -> { builds.incrementAndGet(); return List.of(); };
        request(cache, 1, 1, KEY, empty, c -> true, c -> 0, true);
        request(cache, 1, 4, KEY, empty, c -> true, c -> 0, true);
        assertEquals(1, builds.get());
        request(cache, 1, 5, KEY, () -> { builds.incrementAndGet(); return List.of(A); },
                c -> true, c -> 0, true);
        request(cache, 1, 21, KEY, empty, c -> true, c -> 0, true);
        assertEquals(2, builds.get());
        request(cache, 1, 22, KEY, empty, c -> true, c -> 0, true);
        assertEquals(3, builds.get());
    }

    @Test
    void contextBoundEvictsLeastRecentlyUsedAndReleasesItsCells() {
        SquadFiringPositions cache = new SquadFiringPositions(0);
        for (int i = 0; i < SquadFiringPositions.MAX_CONTEXTS; i++) {
            Candidate cell = new Candidate(i * 5, 0, 0f);
            request(cache, i + 1, 1, key(i), () -> List.of(cell), c -> true, c -> 0, true);
        }
        // Refresh access to the first context, making target1 the LRU victim.
        request(cache, 1, 2, key(0), () -> List.of(A), c -> true, c -> 0, true);
        assertEquals(B, request(cache, 20, 2, key(20), () -> List.of(B),
                c -> true, c -> 0, true).position());
        Result old = request(cache, 2, 3, key(1), List::of, c -> true, c -> 0, false);
        assertEquals(Outcome.DEFERRED, old.outcome());
    }

    @Test
    void callbackFailuresPropagateWithoutLeakingReservations() {
        for (int stage = 0; stage < 3; stage++) {
            SquadFiringPositions cache = new SquadFiringPositions(0);
            RuntimeException failure = new IllegalStateException("controlled failure");
            int which = stage;
            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> request(cache, 1, 1, KEY,
                            () -> { if (which == 0) throw failure; return List.of(A); },
                            c -> { if (which == 1) throw failure; return true; },
                            c -> { if (which == 2) throw failure; return 0; }, true));
            assertSame(failure, thrown);
            assertEquals(A, request(cache, 2, 2, KEY, () -> List.of(A),
                    c -> true, c -> 0, true).position());
        }
    }

    @Test
    void failedSoftGenerationDoesNotDiscardGoodIncumbent() {
        SquadFiringPositions cache = new SquadFiringPositions(0);
        request(cache, 1, 1, KEY, () -> List.of(A), c -> true, c -> 0, true);
        request(cache, 1, 12, KEY, () -> List.of(A), c -> true, c -> 0, true);
        assertThrows(IllegalStateException.class, () -> request(cache, 1, 13, KEY,
                () -> { throw new IllegalStateException("failure"); }, c -> true, c -> 0, true));
        assertEquals(A, request(cache, 1, 14, KEY, List::of,
                c -> true, c -> 0, false).position());
    }

    @Test
    void poolIsCopiedDeduplicatedAndBoundedAndStaticScoreParticipates() {
        SquadFiringPositions cache = new SquadFiringPositions(0);
        List<Candidate> supplied = new ArrayList<>();
        supplied.add(A);
        supplied.add(A);
        for (int i = 1; i <= 64; i++) supplied.add(new Candidate(i * 5, 0, -i));
        Result first = request(cache, 1, 1, KEY, () -> supplied, c -> true, c -> 0, true);
        assertEquals(new Candidate(315, 0, -63), first.position());
        supplied.clear();
        assertEquals(new Candidate(310, 0, -62), request(cache, 2, 2, KEY,
                () -> supplied, c -> true, c -> 0, true).position());
    }

    @Test
    void invalidBestCandidateTriesNextWithoutRescoringTheWholePool() {
        SquadFiringPositions cache = new SquadFiringPositions(0);
        AtomicInteger scores = new AtomicInteger();
        Result result = request(cache, 1, 1, KEY, () -> List.of(A, B, C),
                c -> c == C, c -> { scores.incrementAndGet(); return c.x(); }, true);
        assertEquals(C, result.position());
        assertEquals(3, scores.get());
    }

    private static Result request(SquadFiringPositions cache, long member, int tick,
            Key key, Supplier<List<Candidate>> generator, Predicate<Candidate> validator,
            ToDoubleFunction<Candidate> scorer, boolean permit) {
        return cache.request(member, tick, 1, 1, 0f, 0f, 0f, 0f,
                key, generator, validator, scorer, () -> permit);
    }

    private static Key key(long target) {
        return new Key(target, 20f, 0f, 0f, 0, 0, 8f, -1);
    }
}
