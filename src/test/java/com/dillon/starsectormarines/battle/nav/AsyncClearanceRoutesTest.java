package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
