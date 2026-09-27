package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Service-boundary tests: no simulation, world generator, or real route search. */
class PhaseOwnedRallyRequestsTest {
    @Test
    void memberPollAndUnrelatedNoopCancellationNeverAcquireTheServiceMonitor() throws Exception {
        NavigationGrid grid = grid();
        byte[] occupancy = new byte[100];
        ExecutorService members = Executors.newFixedThreadPool(2);
        try (AsyncDefendTrackRoutes routes = service((g, o, r) -> path(r))) {
            routes.beginTick(1);
            assertTrue(routes.beginMemberUpdates(new long[]{1L, 2L}, 2));
            synchronized (routes) {
                Future<?> poll = members.submit(() -> assertFalse(routes.pollOrSubmit(
                        request(1L, new Object(), 8), 1, grid, occupancy).ready()));
                Future<?> cancel = members.submit(() -> routes.cancel(2L));
                poll.get(2, TimeUnit.SECONDS);
                cancel.get(2, TimeUnit.SECONDS);
                assertEquals(0, routes.metrics().submitted());
                assertEquals(0, routes.metrics().topologyCopies());
                assertEquals(0, routes.metrics().pollCalls(), "member counters fold only after join");
            }
            routes.finishMemberUpdates(grid, occupancy);
            assertEquals(1, routes.metrics().pollCalls());
            assertEquals(1, routes.metrics().cancelCalls());
            assertEquals(1, routes.metrics().noOpCancels());
            assertEquals(1, routes.metrics().submitted());
        } finally {
            members.shutdownNow();
            assertTrue(members.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void latestIntentWinsAndCanceledStagedWorkNeverTakesASnapshot() throws Exception {
        NavigationGrid grid = grid();
        byte[] occupancy = new byte[100];
        Object action = new Object();
        AsyncDefendTrackRoutes.Request latest = request(1L, action, 8);
        AtomicInteger goal = new AtomicInteger();
        try (AsyncDefendTrackRoutes routes = service((g, o, r) -> {
            goal.set(r.goalX());
            return path(r);
        })) {
            routes.beginTick(1);
            routes.beginMemberUpdates(new long[]{1L, 2L}, 2);
            routes.pollOrSubmit(request(1L, action, 7), 1, grid, occupancy);
            routes.pollOrSubmit(latest, 1, grid, occupancy);
            routes.pollOrSubmit(request(2L, action, 9), 1, grid, occupancy);
            routes.cancel(2L);
            assertEquals(0, routes.metrics().submitted());
            routes.finishMemberUpdates(grid, occupancy);
            assertArrayEquals(path(latest), awaitReady(routes, latest, grid, occupancy, 2));
            assertEquals(8, goal.get());
            assertEquals(1, routes.metrics().submitted());
            assertEquals(1, routes.metrics().topologyCopies());
            assertEquals(1, routes.metrics().occupancyCopies());
        }
    }

    @Test
    void freezesInputsOnlyAfterJoinAndWorkersNeverReadLaterLiveMutations() throws Exception {
        NavigationGrid grid = grid();
        byte[] occupancy = new byte[100];
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch observed = new CountDownLatch(1);
        AtomicInteger value = new AtomicInteger();
        try (AsyncDefendTrackRoutes routes = service((g, o, r) -> {
            started.countDown();
            await(release);
            value.set((g.isWalkable(2, 2) ? 100 : 0) + o[22]);
            observed.countDown();
            return path(r);
        })) {
            routes.beginTick(1);
            routes.beginMemberUpdates(new long[]{1L}, 1);
            occupancy[22] = 4;
            routes.pollOrSubmit(request(1L, new Object(), 8), 1, grid, occupancy);
            assertEquals(0, routes.metrics().occupancyCopies());
            occupancy[22] = 9;
            routes.finishMemberUpdates(grid, occupancy);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            occupancy[22] = 20;
            grid.setWalkable(2, 2, false);
            release.countDown();
            assertTrue(observed.await(2, TimeUnit.SECONDS));
            assertEquals(109, value.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void cancellationCannotInstallALateResultAndAllRemovalsPrecedeNewSubmissions() throws Exception {
        NavigationGrid grid = grid();
        byte[] occupancy = new byte[100];
        Object action = new Object();
        CountDownLatch oldStarted = new CountDownLatch(1);
        CountDownLatch releaseOld = new CountDownLatch(1);
        AsyncDefendTrackRoutes.Request old = request(2L, action, 7);
        AsyncDefendTrackRoutes.Request replacement = request(1L, action, 8);
        try (AsyncDefendTrackRoutes routes = new AsyncDefendTrackRoutes(1, 1, (g, o, r) -> {
            if (r.goalX() == 7) {
                oldStarted.countDown();
                // Deliberately finish after cancellation, as a worker missing its
                // next interruption checkpoint may do.
                boolean released = false;
                while (!released) {
                    try { released = releaseOld.await(2, TimeUnit.SECONDS); }
                    catch (InterruptedException ignored) { }
                    if (!released && releaseOld.getCount() != 0) Thread.yield();
                }
            }
            return path(r);
        }, true)) {
            routes.beginTick(1);
            routes.beginMemberUpdates(new long[]{1L, 2L, 3L}, 3);
            routes.pollOrSubmit(old, 1, grid, occupancy);
            routes.finishMemberUpdates(grid, occupancy);
            assertTrue(oldStarted.await(2, TimeUnit.SECONDS));
            // Occupy the queue as well, then cancel its member late in the inbox
            // order. The first inbox's replacement must see that freed capacity.
            routes.beginMemberUpdates(new long[]{1L, 2L, 3L}, 3);
            routes.pollOrSubmit(request(3L, action, 9), 1, grid, occupancy);
            routes.finishMemberUpdates(grid, occupancy);
            routes.beginTick(2);
            routes.beginMemberUpdates(new long[]{1L, 2L, 3L}, 3);
            routes.pollOrSubmit(replacement, 2, grid, occupancy);
            routes.cancel(2L);
            routes.cancel(3L);
            routes.finishMemberUpdates(grid, occupancy);
            assertEquals(3, routes.metrics().submitted());
            assertEquals(0, routes.metrics().rejected());
            assertEquals(1, routes.metrics().pendingMembers());
            releaseOld.countDown();
            assertArrayEquals(path(replacement), awaitReady(routes, replacement, grid, occupancy, 3));
            assertEquals(0, routes.metrics().pendingMembers());
        } finally {
            releaseOld.countDown();
        }
    }

    @Test
    void changedStartOrTopologyCannotDeliverTheEarlierReadyRoute() throws Exception {
        NavigationGrid grid = grid();
        byte[] occupancy = new byte[100];
        Object action = new Object();
        AsyncDefendTrackRoutes.Request first = request(1L, action, 8);
        try (AsyncDefendTrackRoutes routes = service((g, o, r) -> path(r))) {
            stage(routes, first, grid, occupancy, 1);
            awaitCompleted(routes, 1);
            AsyncDefendTrackRoutes.Request moved = new AsyncDefendTrackRoutes.Request(
                    1L, 4, 1L, action, 8, 8, 3, 2, 8, 8, 8, 8, false);
            routes.beginTick(2);
            routes.beginMemberUpdates(new long[]{1L}, 1);
            assertFalse(routes.pollOrSubmit(moved, 2, grid, occupancy).ready());
            routes.finishMemberUpdates(grid, occupancy);
            awaitCompleted(routes, 2);
            grid.setWalkable(2, 2, false);
            routes.beginTick(3);
            routes.beginMemberUpdates(new long[]{1L}, 1);
            assertFalse(routes.pollOrSubmit(moved, 3, grid, occupancy).ready());
            routes.finishMemberUpdates(grid, occupancy);
            assertArrayEquals(path(moved), awaitReady(routes, moved, grid, occupancy, 4));
            assertEquals(3, routes.metrics().submitted());
        }
    }

    @Test
    void negativeRetryRemainsBoundedAndTtlRetiresReadyButUnclaimedResults() throws Exception {
        NavigationGrid grid = grid();
        byte[] occupancy = new byte[100];
        AsyncDefendTrackRoutes.Request request = request(1L, new Object(), 8);
        try (AsyncDefendTrackRoutes routes = service((g, o, r) -> GridPathfinder.EMPTY_PATH)) {
            stage(routes, request, grid, occupancy, 1);
            for (int i = 0; i < 1000 && routes.metrics().noPath() == 0; i++) {
                stage(routes, request, grid, occupancy, 2);
                Thread.sleep(1);
            }
            assertEquals(1, routes.metrics().noPath());
            stage(routes, request, grid, occupancy, 31);
            assertEquals(1, routes.metrics().submitted());
            stage(routes, request, grid, occupancy, 32);
            assertEquals(2, routes.metrics().submitted());
        }
        try (AsyncDefendTrackRoutes routes = service((g, o, r) -> path(r))) {
            stage(routes, request, grid, occupancy, 1);
            awaitCompleted(routes, 1);
            routes.beginTick(122);
            assertEquals(0, routes.metrics().pendingMembers());
            routes.beginMemberUpdates(new long[]{1L}, 1);
            assertFalse(routes.pollOrSubmit(request, 122, grid, occupancy).ready());
            routes.finishMemberUpdates(grid, occupancy);
            assertEquals(2, routes.metrics().submitted());
        }
    }

    @Test
    void emptyAndCancellationOnlyPhasesMakeNoCopiesAndObsoleteStagedRevisionIsDropped() {
        NavigationGrid grid = grid();
        byte[] occupancy = new byte[100];
        try (AsyncDefendTrackRoutes routes = service((g, o, r) -> path(r))) {
            routes.beginTick(1);
            routes.beginMemberUpdates(new long[0], 0);
            routes.finishMemberUpdates(grid, occupancy);
            routes.beginMemberUpdates(new long[]{1L}, 1);
            routes.cancel(1L);
            routes.finishMemberUpdates(grid, occupancy);
            routes.beginMemberUpdates(new long[]{1L}, 1);
            routes.pollOrSubmit(request(1L, new Object(), 8), 1, grid, occupancy);
            grid.setWalkable(2, 2, false);
            routes.finishMemberUpdates(grid, occupancy);
            assertEquals(0, routes.metrics().submitted());
            assertEquals(0, routes.metrics().topologyCopies());
            assertEquals(0, routes.metrics().occupancyCopies());
        }
    }

    @Test
    void abandonedPhaseCannotBeReusedAndCloseNeverDrainsItsRequests() throws Exception {
        NavigationGrid grid = grid();
        byte[] occupancy = new byte[100];
        AsyncDefendTrackRoutes.Request request = request(1L, new Object(), 8);
        try (AsyncDefendTrackRoutes routes = service((g, o, r) -> path(r))) {
            assertThrows(IllegalStateException.class, () -> routes.finishMemberUpdates(grid, occupancy));
            routes.beginMemberUpdates(new long[]{1L}, 1);
            routes.pollOrSubmit(request, 1, grid, occupancy);
            assertThrows(IllegalStateException.class, () -> routes.beginMemberUpdates(new long[]{1L}, 1));
            assertThrows(IllegalStateException.class, () -> routes.beginTick(2));
            assertThrows(IllegalStateException.class, () -> routes.cancel(2L));
            ExecutorService otherHost = Executors.newSingleThreadExecutor();
            try {
                otherHost.submit(() -> assertThrows(IllegalStateException.class,
                        () -> routes.finishMemberUpdates(grid, occupancy))).get(2, TimeUnit.SECONDS);
            } finally {
                otherHost.shutdownNow();
                assertTrue(otherHost.awaitTermination(2, TimeUnit.SECONDS));
            }
            routes.close();
            assertEquals(0, routes.metrics().submitted());
            assertFalse(routes.beginMemberUpdates(new long[]{1L}, 1));
            assertFalse(routes.pollOrSubmit(request, 2, grid, occupancy).ready());
            routes.cancel(1L);
        }
    }

    @Test
    void explicitControlKeepsUnbracketedSubmission() {
        NavigationGrid grid = grid();
        try (AsyncDefendTrackRoutes routes = new AsyncDefendTrackRoutes(1, 4,
                (g, o, r) -> path(r), false)) {
            assertFalse(routes.phaseOwnedRequestsEnabled());
            assertFalse(routes.beginMemberUpdates(new long[]{1L}, 1));
            routes.pollOrSubmit(request(1L, new Object(), 8), 1, grid, new byte[100]);
            assertEquals(1, routes.metrics().submitted());
        }
    }

    private static void stage(AsyncDefendTrackRoutes routes, AsyncDefendTrackRoutes.Request request,
                              NavigationGrid grid, byte[] occupancy, int tick) {
        routes.beginTick(tick);
        assertTrue(routes.beginMemberUpdates(new long[]{request.member()}, 1));
        assertFalse(routes.pollOrSubmit(request, tick, grid, occupancy).ready());
        routes.finishMemberUpdates(grid, occupancy);
    }

    private static int[] awaitReady(AsyncDefendTrackRoutes routes, AsyncDefendTrackRoutes.Request request,
                                    NavigationGrid grid, byte[] occupancy, int tick) throws Exception {
        routes.beginTick(tick);
        for (int i = 0; i < 1000; i++) {
            routes.beginMemberUpdates(new long[]{request.member()}, 1);
            AsyncDefendTrackRoutes.Result result = routes.pollOrSubmit(request, tick, grid, occupancy);
            routes.finishMemberUpdates(grid, occupancy);
            if (result.ready()) return result.path();
            Thread.sleep(1);
        }
        throw new AssertionError("route did not publish");
    }

    private static void awaitCompleted(AsyncDefendTrackRoutes routes, int count) throws Exception {
        for (int i = 0; i < 1000; i++) {
            if (routes.metrics().completed() >= count) return;
            Thread.sleep(1);
        }
        throw new AssertionError("search did not finish");
    }

    private static AsyncDefendTrackRoutes service(AsyncDefendTrackRoutes.Search search) {
        return new AsyncDefendTrackRoutes(1, 4, search, true);
    }

    private static AsyncDefendTrackRoutes.Request request(long member, Object token, int goal) {
        return new AsyncDefendTrackRoutes.Request(member, 4, 1L, token,
                goal, goal, 1, 1, goal, goal, goal, goal, false);
    }

    private static int[] path(AsyncDefendTrackRoutes.Request request) {
        return new int[]{request.startX(), request.startY(), request.goalX(), request.goalY()};
    }

    private static NavigationGrid grid() {
        NavigationGrid grid = new NavigationGrid(10, 10);
        for (int y = 0; y < 10; y++) for (int x = 0; x < 10; x++) grid.setWalkableFloor(x, y);
        return grid;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) throw new AssertionError("latch timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
