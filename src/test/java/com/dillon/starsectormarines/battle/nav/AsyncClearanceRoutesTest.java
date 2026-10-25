package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncClearanceRoutesTest {
    @Test
    void synchronousControlReusesProofAndRebuildsOnLiveTerrainRevision() {
        NavigationGrid grid = floor();
        var request = new AsyncClearanceRoutes.Request(2f, 4f, 9, 4, .6f);
        try (var routes = new AsyncClearanceRoutes(false)) {
            var first = routes.pollOrSubmit(1, request, grid);
            assertEquals(PathRequestStatus.READY, first.status());
            assertEquals(first, routes.pollOrSubmit(1, request, grid));
            for (int y = 0; y < 8; y++) grid.blockSharedEdge(5, y, Direction.E);
            var blocked = routes.pollOrSubmit(1, request, grid);
            assertEquals(PathRequestStatus.FAILED, blocked.status());
            assertNotEquals(first.topologyRevision(), blocked.topologyRevision());
            assertEquals(ClearanceRoutePlanner.Status.UNREACHABLE, blocked.proof().status());
        }
    }

    @Test
    void workerResultsCannotOutliveAnIntentOrTerrainChange() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            NavigationGrid grid = floor();
            var request = new AsyncClearanceRoutes.Request(2f, 4f, 9, 4, .6f);
            try (var routes = new AsyncClearanceRoutes(true)) {
                routes.pollOrSubmit(7, request, grid);
                for (int y = 0; y < 8; y++) grid.blockSharedEdge(5, y, Direction.E);
                AsyncClearanceRoutes.Reply result;
                do {
                    result = routes.pollOrSubmit(7, request, grid);
                    if (result.status() == PathRequestStatus.PENDING) Thread.sleep(1);
                } while (result.status() == PathRequestStatus.PENDING);
                assertEquals(PathRequestStatus.FAILED, result.status());
                assertEquals(grid.topologyRevision(), result.topologyRevision());
                // The same owner can replace the refused goal with a local one.
                var local = new AsyncClearanceRoutes.Request(2f, 4f, 3, 4, .6f);
                do {
                    result = routes.pollOrSubmit(7, local, grid);
                    if (result.status() == PathRequestStatus.PENDING) Thread.sleep(1);
                } while (result.status() == PathRequestStatus.PENDING);
                assertEquals(PathRequestStatus.READY, result.status());
                assertTrue(result.proof().resolved().x() < 6f);
                routes.forget(7);
            }
        });
    }

    @Test
    void completedProofRetainsItsStartForLiveAttachmentAfterCrowdDrift() {
        NavigationGrid grid = floor();
        try (var routes = new AsyncClearanceRoutes(false)) {
            var first = routes.pollOrSubmit(1,
                    new AsyncClearanceRoutes.Request(2f, 4f, 9, 4, .6f), grid);
            var drifted = routes.pollOrSubmit(1,
                    new AsyncClearanceRoutes.Request(2f, 5f, 9, 4, .6f), grid);
            assertEquals(PathRequestStatus.READY, drifted.status());
            assertSame(first.proof(), drifted.proof(), "owner must validate a live attachment before installing");
        }
    }

    @Test
    void refusedIntentIsNotResearchedUntilItsBackoffElapses() {
        NavigationGrid grid = floor();
        for (int y = 0; y < 8; y++) grid.blockSharedEdge(5, y, Direction.E);
        try (var routes = new AsyncClearanceRoutes(false)) {
            routes.beginTick(10);
            var first = routes.pollOrSubmit(1, new AsyncClearanceRoutes.Request(2f, 4f, 9, 4, .6f), grid);
            assertEquals(PathRequestStatus.FAILED, first.status());
            routes.beginTick(11);
            var drifted = routes.pollOrSubmit(1, new AsyncClearanceRoutes.Request(2f, 5f, 9, 4, .6f), grid);
            assertSame(first.proof(), drifted.proof(), "a drifting body must not reopen a fresh refusal");
            routes.beginTick(10 + AsyncClearanceRoutes.FAILURE_RETRY_TICKS);
            var later = routes.pollOrSubmit(1, new AsyncClearanceRoutes.Request(2f, 5f, 9, 4, .6f), grid);
            assertNotSame(first.proof(), later.proof(), "after the backoff the refusal is reconsidered");
        }
    }

    @Test
    void aRefusalStandsAgainstATerrainChangeUntilItsBackoffElapses() {
        NavigationGrid grid = floor();
        for (int y = 0; y < 8; y++) grid.blockSharedEdge(5, y, Direction.E);
        var request = new AsyncClearanceRoutes.Request(2f, 4f, 9, 4, .6f);
        try (var routes = new AsyncClearanceRoutes(false)) {
            routes.beginTick(10);
            var first = routes.pollOrSubmit(1, request, grid);
            assertEquals(PathRequestStatus.FAILED, first.status());
            for (int y = 2; y <= 6; y++) grid.openSharedEdge(5, y, Direction.E);
            routes.beginTick(11);
            assertSame(first.proof(), routes.pollOrSubmit(1, request, grid).proof(),
                    "a moved revision must not re-flood a fresh refusal");
            routes.beginTick(10 + AsyncClearanceRoutes.FAILURE_RETRY_TICKS);
            assertEquals(PathRequestStatus.READY, routes.pollOrSubmit(1, request, grid).status(),
                    "the opened wall is found once the refusal lapses");
        }
    }

    @Test
    void forgettingAnOwnerKeepsItsFreshRefusal() {
        NavigationGrid grid = floor();
        for (int y = 0; y < 8; y++) grid.blockSharedEdge(5, y, Direction.E);
        var request = new AsyncClearanceRoutes.Request(2f, 4f, 9, 4, .6f);
        try (var routes = new AsyncClearanceRoutes(false)) {
            routes.beginTick(10);
            var first = routes.pollOrSubmit(1, request, grid);
            routes.forget(1);
            routes.beginTick(11);
            assertSame(first.proof(), routes.pollOrSubmit(1, request, grid).proof(),
                    "a cleared path must not cost the refusal it was refused with");
        }
    }

    @Test
    void closedCoordinatorRefusesNewWork() {
        var routes = new AsyncClearanceRoutes(true);
        routes.close();
        assertEquals(PathRequestStatus.FAILED, routes.pollOrSubmit(1,
                new AsyncClearanceRoutes.Request(2f, 4f, 9, 4, .6f), floor()).status());
    }

    private static NavigationGrid floor() {
        NavigationGrid grid = new NavigationGrid(12, 8);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        return grid;
    }
}
