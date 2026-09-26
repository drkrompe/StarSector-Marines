package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadBeliefTestAccess;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommanderInfluenceAsyncTest {

    @Test
    void readsOnlyRequestWorkAndCompletedPairsPublishAtANewBoundary() {
        try (Fixture f = new Fixture()) {
            CommanderInfluenceSnapshot emptyMarine = f.service.snapshot(Faction.MARINE);
            CommanderInfluenceSnapshot emptyDefender = f.service.snapshot(Faction.DEFENDER);
            f.service.advanceAsync(1);
            assertEquals(0, f.executor.queued());

            f.service.tick(1);
            f.service.tick(1);
            assertEquals(0, f.executor.queued(), "readers never submit or build fields");
            assertSame(emptyMarine, f.service.snapshot(Faction.MARINE));

            f.service.advanceAsync(2);
            assertEquals(1, f.executor.queued());
            assertEquals(0, f.service.metrics().completed());
            f.executor.runNext();
            assertEquals(1, f.service.metrics().completed());
            f.service.tick(2);
            f.service.advanceAsync(2);
            assertSame(emptyMarine, f.service.snapshot(Faction.MARINE));
            assertSame(emptyDefender, f.service.snapshot(Faction.DEFENDER));
            assertEquals(0, f.service.metrics().published());

            f.service.advanceAsync(3);
            assertEquals(2, f.service.snapshot(Faction.MARINE).updatedTick());
            assertEquals(2, f.service.snapshot(Faction.DEFENDER).updatedTick());
            assertEquals(1, f.service.metrics().published());
            assertFalse(f.service.metrics().inFlight());
            assertEquals(0, f.executor.queued());
        }
    }

    @Test
    void oneInFlightBuildCoalescesDemandAcrossManyRefreshIntervals() {
        try (Fixture f = new Fixture()) {
            f.request(1);
            for (int tick = 2; tick <= 100; tick++) f.request(tick);
            assertEquals(1, f.executor.queued());
            assertEquals(1, f.service.metrics().submitted());
            assertEquals(-1, f.service.snapshot(Faction.MARINE).updatedTick());

            f.executor.runNext();
            f.service.advanceAsync(101);
            assertEquals(1, f.service.snapshot(Faction.MARINE).updatedTick());
            assertEquals(1, f.executor.queued(), "coalesced demand captures only the latest tick");
            assertEquals(2, f.service.metrics().submitted());
            f.executor.runNext();
            f.service.advanceAsync(102);
            assertEquals(101, f.service.snapshot(Faction.MARINE).updatedTick());
            assertEquals(101, f.service.snapshot(Faction.DEFENDER).updatedTick());
        }
    }

    @Test
    void refreshCadenceRetainsThePreviousSnapshotWhileNextBuildRuns() {
        try (Fixture f = new Fixture()) {
            f.request(1);
            f.executor.runNext();
            f.service.advanceAsync(2);
            CommanderInfluenceSnapshot old = f.service.snapshot(Faction.MARINE);
            for (int tick = 3; tick < 16; tick++) f.service.advanceAsync(tick);
            assertEquals(0, f.executor.queued());
            f.service.advanceAsync(16);
            assertEquals(1, f.executor.queued());
            assertSame(old, f.service.snapshot(Faction.MARINE));
            f.executor.runNext();
            assertSame(old, f.service.snapshot(Faction.MARINE));
            f.service.advanceAsync(17);
            assertEquals(16, f.service.snapshot(Faction.MARINE).updatedTick());
            assertEquals(1, old.updatedTick(), "published snapshots are never updated in place");
        }
    }

    @Test
    void workerUsesCapturedPositionsBeliefsLossesAndTopologyNotLaterLiveState() {
        try (Fixture f = new Fixture();
             CommanderInfluenceService sync = new CommanderInfluenceService(
                     f.grid, f.roster, f.revision::get)) {
            DeathEvent marineLoss = new DeathEvent(f.marine, 2.5f, 3.5f, -1);
            f.service.casualties().onDeath(marineLoss);
            sync.casualties().onDeath(marineLoss);
            sync.refresh(10);
            f.request(10);

            f.roster.world().setCellPos(f.marine, 20, 3);
            f.roster.world().setCellPos(f.defender, 2, 3);
            SquadBeliefTestAccess.observeDirect(f.marineSquad, f.defender, 2, 3, 11);
            SquadBeliefTestAccess.observeDirect(f.defenderSquad, f.marine, 20, 3, 11);
            f.service.casualties().onDeath(marineLoss);
            for (int y = 0; y < f.grid.getHeight(); y++) f.grid.setWalkable(7, y, false);

            f.executor.runNext();
            f.service.advanceAsync(11);
            assertSnapshotsEqual(sync.snapshot(Faction.MARINE), f.service.snapshot(Faction.MARINE));
            assertSnapshotsEqual(sync.snapshot(Faction.DEFENDER), f.service.snapshot(Faction.DEFENDER));
            assertEquals(1f, f.service.snapshot(Faction.MARINE).lossesAtWorld(2, 3));
            assertEquals(20, f.service.snapshot(Faction.MARINE).contacts().get(0).cellX());
            assertTrue(f.service.snapshot(Faction.MARINE).friendlyAtWorld(20, 3) > 0f,
                    "a later wall cannot enter the captured propagation graph");
        }
    }

    @Test
    void changedTopologyDiscardsTheWholePairAndRecapturesPendingDemand() {
        try (Fixture f = new Fixture()) {
            f.request(1);
            for (int y = 0; y < f.grid.getHeight(); y++) f.grid.setWalkable(7, y, false);
            f.revision.incrementAndGet();
            f.executor.runNext();
            f.service.advanceAsync(2);
            assertEquals(-1, f.service.snapshot(Faction.MARINE).updatedTick());
            assertEquals(-1, f.service.snapshot(Faction.DEFENDER).updatedTick());
            assertEquals(1, f.service.metrics().discarded());
            assertEquals(0, f.executor.queued(), "topology churn must not bypass the capture cadence");
            f.service.advanceAsync(16);
            assertEquals(1, f.executor.queued(), "discarding stale work must not lose its request");

            f.executor.runNext();
            f.service.advanceAsync(17);
            assertEquals(16, f.service.snapshot(Faction.MARINE).updatedTick());
            assertEquals(16, f.service.snapshot(Faction.DEFENDER).updatedTick());
            assertEquals(0f, f.service.snapshot(Faction.MARINE).friendlyAtWorld(20, 3));
        }
    }

    @Test
    void explicitRefreshSupersedesEvenACompletedUnpublishedBuild() {
        try (Fixture f = new Fixture()) {
            f.request(1);
            f.executor.runNext();
            f.roster.spawn(new EntitySpec("second", Faction.MARINE, UnitType.MARINE, 2, 3));
            f.service.refresh(2);
            CommanderInfluenceSnapshot fresh = f.service.snapshot(Faction.MARINE);
            assertEquals(2f, fresh.friendlyAtWorld(2, 3));
            assertEquals(1, f.service.metrics().discarded());
            assertFalse(f.service.metrics().inFlight());
            f.service.advanceAsync(3);
            assertSame(fresh, f.service.snapshot(Faction.MARINE));
            assertEquals(2, f.service.snapshot(Faction.DEFENDER).updatedTick());
        }
    }

    @Test
    void explicitRefreshCancelsQueuedWorkBeforeItCanRun() {
        try (Fixture f = new Fixture()) {
            f.request(1);
            Future<?> obsolete = f.executor.nextFuture();
            f.service.refresh(2);
            assertTrue(obsolete.isCancelled());
            f.executor.runNext();
            f.service.advanceAsync(3);
            assertEquals(0, f.service.metrics().completed());
            assertEquals(2, f.service.snapshot(Faction.MARINE).updatedTick());
        }
    }

    @Test
    void rejectedSubmissionRetainsDemandAndRecoversWithoutCallerExecution() {
        try (Fixture f = new Fixture()) {
            f.executor.rejectNext = true;
            f.request(1);
            assertEquals(1, f.service.metrics().failed());
            assertEquals(0, f.service.metrics().submitted());
            assertEquals(0, f.service.metrics().completed());
            assertEquals(-1, f.service.snapshot(Faction.MARINE).updatedTick());
            assertFalse(f.service.metrics().inFlight());

            f.service.advanceAsync(16);
            assertEquals(1, f.executor.queued());
            f.executor.runNext();
            f.service.advanceAsync(17);
            assertEquals(16, f.service.snapshot(Faction.MARINE).updatedTick());
        }
    }

    @Test
    void failedWorkerRetainsOldPairAndRetriesUnfulfilledDemand() {
        try (Fixture f = new Fixture()) {
            f.service.refresh(0);
            CommanderInfluenceSnapshot old = f.service.snapshot(Faction.MARINE);
            f.request(15);
            assertFalse(Thread.currentThread().isInterrupted());
            try {
                Thread.currentThread().interrupt();
                f.executor.runNext();
            } finally {
                Thread.interrupted();
            }
            f.service.advanceAsync(16);
            assertEquals(1, f.service.metrics().failed());
            assertSame(old, f.service.snapshot(Faction.MARINE));
            assertEquals(0, f.service.snapshot(Faction.DEFENDER).updatedTick());

            f.service.advanceAsync(30);
            assertEquals(1, f.executor.queued(), "a failed build has not served its demand");
            f.executor.runNext();
            f.service.advanceAsync(31);
            assertEquals(30, f.service.snapshot(Faction.MARINE).updatedTick());
            assertEquals(1, f.service.metrics().published());
        }
    }

    @Test
    void closingCancelsPendingWorkAndPreventsFurtherPublicationOrSubmission() {
        try (Fixture f = new Fixture()) {
            f.request(1);
            Future<?> queued = f.executor.nextFuture();
            f.service.close();
            assertTrue(queued.isCancelled());
            assertTrue(f.executor.isShutdown());
            assertFalse(f.service.metrics().inFlight());
            assertEquals(1, f.service.metrics().discarded());
            f.service.tick(100);
            f.service.advanceAsync(101);
            f.service.refresh(102);
            f.service.close();
            assertEquals(1, f.service.metrics().submitted());
            assertEquals(0, f.service.metrics().published());
            assertEquals(-1, f.service.snapshot(Faction.MARINE).updatedTick());
            assertEquals(-1, f.service.snapshot(Faction.DEFENDER).updatedTick());
        }
    }

    private static void assertSnapshotsEqual(CommanderInfluenceSnapshot expected,
                                             CommanderInfluenceSnapshot actual) {
        assertEquals(expected.faction(), actual.faction());
        assertEquals(expected.updatedTick(), actual.updatedTick());
        assertEquals(expected.contacts(), actual.contacts());
        assertEquals(expected.width(), actual.width());
        assertEquals(expected.height(), actual.height());
        assertEquals(expected.maxFriendly(), actual.maxFriendly());
        assertEquals(expected.maxHostile(), actual.maxHostile());
        for (int y = 0; y < expected.height(); y++) {
            for (int x = 0; x < expected.width(); x++) {
                assertEquals(expected.friendlyAt(x, y), actual.friendlyAt(x, y));
                assertEquals(expected.hostileAt(x, y), actual.hostileAt(x, y));
                assertEquals(expected.lossesAt(x, y), actual.lossesAt(x, y));
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final NavigationGrid grid = new NavigationGrid(24, 8);
        final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(24, 8), null);
        final AtomicLong revision = new AtomicLong(1);
        final ManualExecutor executor = new ManualExecutor();
        final CommanderInfluenceService service;
        final long marine;
        final long defender;
        final Squad marineSquad;
        final Squad defenderSquad;

        Fixture() {
            for (int y = 0; y < grid.getHeight(); y++) {
                for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
            }
            int marineSquadId = roster.mintSquad(Faction.MARINE, UnitType.MARINE);
            int defenderSquadId = roster.mintSquad(Faction.DEFENDER, UnitType.MARINE_RED);
            marine = roster.spawn(new EntitySpec("marine", Faction.MARINE,
                    UnitType.MARINE, 2, 3).squad(marineSquadId));
            defender = roster.spawn(new EntitySpec("defender", Faction.DEFENDER,
                    UnitType.MARINE_RED, 20, 3).squad(defenderSquadId));
            marineSquad = roster.getSquad(marineSquadId);
            defenderSquad = roster.getSquad(defenderSquadId);
            marineSquad.aliveMembers = 1;
            defenderSquad.aliveMembers = 1;
            SquadBeliefTestAccess.observeDirect(marineSquad, defender, 20, 3, 1);
            SquadBeliefTestAccess.observeDirect(defenderSquad, marine, 2, 3, 1);
            service = new CommanderInfluenceService(grid, roster, revision::get, executor);
        }

        void request(int tick) {
            service.tick(tick);
            service.advanceAsync(tick);
        }

        @Override
        public void close() { service.close(); }
    }

    /** Held tasks make scheduling/publication ordering explicit without clocks or sleeps. */
    private static final class ManualExecutor extends AbstractExecutorService {
        private final ArrayDeque<Runnable> queued = new ArrayDeque<>();
        private boolean shutdown;
        boolean rejectNext;

        int queued() { return queued.size(); }

        Future<?> nextFuture() { return (Future<?>) queued.getFirst(); }

        void runNext() { queued.removeFirst().run(); }

        @Override
        public void execute(Runnable task) {
            if (shutdown || rejectNext) {
                rejectNext = false;
                throw new RejectedExecutionException("deliberate test rejection");
            }
            queued.addLast(task);
        }

        @Override
        public void shutdown() { shutdown = true; }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> abandoned = new ArrayList<>(queued);
            queued.clear();
            return abandoned;
        }

        @Override
        public boolean isShutdown() { return shutdown; }

        @Override
        public boolean isTerminated() { return shutdown && queued.isEmpty(); }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
    }
}
