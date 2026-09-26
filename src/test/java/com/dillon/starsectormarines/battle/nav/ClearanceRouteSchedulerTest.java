package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClearanceRouteSchedulerTest {
    @Test
    void queueSharesBudgetAndRotatesOwnersIndependentOfSubmissionOrder() {
        var grid = splitFloor();
        try (var queue = new ClearanceRouteScheduler(grid)) {
            var request = request();
            queue.pollOrSubmit(30, request);
            queue.pollOrSubmit(10, request);
            queue.pollOrSubmit(20, request);
            assertEquals(new ClearanceRouteScheduler.Work(2, 6, 3), queue.advance(6, 2, 3));
            assertEquals(3, queue.pollOrSubmit(10, request).expandedNodes());
            assertEquals(3, queue.pollOrSubmit(20, request).expandedNodes());
            assertEquals(0, queue.pollOrSubmit(30, request).expandedNodes());
            queue.advance(3, 1, 3);
            assertEquals(3, queue.pollOrSubmit(30, request).expandedNodes());
            queue.advance(3, 1, 3);
            assertEquals(6, queue.pollOrSubmit(10, request).expandedNodes());
        }
    }

    @Test
    void completedProofIsReusedUntilTopologyOrRequestChanges() {
        var grid = splitFloor();
        var nearby = new ClearanceRouteScheduler.Request(2f, 3f, 4f, 3f, .6f, 0f, 500);
        try (var queue = new ClearanceRouteScheduler(grid)) {
            assertEquals(ClearanceRoutePlanner.Status.PENDING, queue.pollOrSubmit(1, nearby).status());
            queue.advance(4, 1, 4);
            var proof = queue.pollOrSubmit(1, nearby);
            assertEquals(ClearanceRoutePlanner.Status.FOUND, proof.status());
            assertEquals(new ClearanceRouteScheduler.Work(0, 0, 0), queue.advance(4, 1, 4));
            assertEquals(proof, queue.pollOrSubmit(1, nearby));
            grid.setWalkable(3, 3, false);
            assertEquals(ClearanceRoutePlanner.Status.PENDING, queue.pollOrSubmit(1, nearby).status());
            assertEquals(0, queue.pollOrSubmit(1, nearby).expandedNodes());
            assertEquals(ClearanceRoutePlanner.Status.PENDING, queue.pollOrSubmit(1, request()).status());
            assertEquals(1, queue.retainedRequests());
        }
    }

    @Test
    void requestCapAlsoBoundsCheapStraightProofsAndForgetReleasesOwnership() {
        var grid = splitFloor();
        var queue = new ClearanceRouteScheduler(grid);
        var nearby = new ClearanceRouteScheduler.Request(2f, 3f, 4f, 3f, .6f, 0f, 500);
        queue.pollOrSubmit(1, nearby);
        queue.pollOrSubmit(2, nearby);
        assertEquals(new ClearanceRouteScheduler.Work(1, 0, 1), queue.advance(4, 1, 4));
        queue.forget(2);
        assertEquals(1, queue.retainedRequests());
        queue.close();
        assertEquals(0, queue.retainedRequests());
        assertThrows(IllegalStateException.class, () -> queue.pollOrSubmit(3, nearby));
        assertTrue(queue.advance(4, 1, 4).pendingRequests() == 0);
    }

    private static ClearanceRouteScheduler.Request request() {
        return new ClearanceRouteScheduler.Request(2f, 3f, 9f, 3f, .6f, 0f, 2000);
    }

    private static NavigationGrid splitFloor() {
        NavigationGrid grid = new NavigationGrid(12, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
            grid.blockSharedEdge(5, y, Direction.E);
        }
        return grid;
    }
}
