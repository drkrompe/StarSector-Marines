package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvanceThreatPublicationTest {
    @Test
    void parallelMembersPublishOneCompleteDecisionPerTick() throws Exception {
        for (boolean isolated : new boolean[]{false, true}) {
            Squad squad = new Squad(1, Faction.MARINE);
            ExecutorService workers = Executors.newFixedThreadPool(2);
            CountDownLatch computing = new CountDownLatch(1);
            CountDownLatch finish = new CountDownLatch(1);
            CountDownLatch contenderEntered = new CountDownLatch(1);
            AtomicInteger computations = new AtomicInteger();
            Runnable publish = () -> {
                computations.incrementAndGet();
                squad.advanceThreatId = 123L;
                computing.countDown();
                await(finish);
                squad.advanceEngageCommitted = true;
                squad.advanceEngageWeight = 0.75f;
            };
            try {
                Future<?> owner = workers.submit(() -> AbstractZoneAction.refreshAdvanceThreat(
                        squad, 10, isolated, publish));
                assertTrue(computing.await(2, TimeUnit.SECONDS));
                Future<?> contender = workers.submit(() -> {
                    contenderEntered.countDown();
                    AbstractZoneAction.refreshAdvanceThreat(squad, 10, isolated, publish);
                    assertTrue(squad.advanceEngageCommitted);
                    assertEquals(0.75f, squad.advanceEngageWeight);
                });
                assertTrue(contenderEntered.await(2, TimeUnit.SECONDS));
                assertEquals(-1, squad.advanceThreatTick, "in-progress computation is not published");
                assertFalse(contender.isDone(), "a sibling cannot consume a partial decision");
                finish.countDown();
                owner.get(2, TimeUnit.SECONDS);
                contender.get(2, TimeUnit.SECONDS);
                assertEquals(1, computations.get());
                assertEquals(10, squad.advanceThreatTick);
                AbstractZoneAction.refreshAdvanceThreat(squad, 10, isolated, publish);
                assertEquals(1, computations.get(), "same-tick fast path reuses the publication");
                AbstractZoneAction.refreshAdvanceThreat(squad, 11, isolated, publish);
                assertEquals(2, computations.get(), "next tick still refreshes immediately");
            } finally {
                finish.countDown();
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void isolatedDecisionCompletesWhileAnotherThreadOwnsTheBroadSquadLock() throws Exception {
        Squad squad = new Squad(1, Faction.MARINE);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            synchronized (squad.lock) {
                Future<?> result = worker.submit(() -> AbstractZoneAction.refreshAdvanceThreat(
                        squad, 10, true, () -> {
                            assertFalse(Thread.holdsLock(squad.lock));
                            assertTrue(Thread.holdsLock(squad.advanceThreatLock));
                            squad.advanceThreatId = 321L;
                        }));
                result.get(2, TimeUnit.SECONDS);
                assertEquals(10, squad.advanceThreatTick);
                assertEquals(321L, squad.advanceThreatId);
            }
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void controlKeepsOriginalMonitorAndHostResetCanRefreshTheSameTick() {
        Squad squad = new Squad(1, Faction.MARINE);
        AtomicInteger computations = new AtomicInteger();
        Runnable publish = () -> {
            assertTrue(Thread.holdsLock(squad.lock));
            assertFalse(Thread.holdsLock(squad.advanceThreatLock));
            computations.incrementAndGet();
        };
        AbstractZoneAction.refreshAdvanceThreat(squad, 10, false, publish);
        squad.advanceThreatTick = -1; // Host control-entry boundary, after workers joined.
        AbstractZoneAction.refreshAdvanceThreat(squad, 10, false, publish);
        assertEquals(2, computations.get());
    }

    @Test
    void failedComputationDoesNotMarkThisTickComplete() {
        Squad squad = new Squad(1, Faction.MARINE);
        assertThrows(IllegalStateException.class, () -> AbstractZoneAction.refreshAdvanceThreat(
                squad, 10, true, () -> { throw new IllegalStateException("test failure"); }));
        assertEquals(-1, squad.advanceThreatTick);
        AbstractZoneAction.refreshAdvanceThreat(squad, 10, true, () -> squad.advanceThreatId = 7L);
        assertEquals(10, squad.advanceThreatTick);
        assertEquals(7L, squad.advanceThreatId);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) throw new AssertionError("latch timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
