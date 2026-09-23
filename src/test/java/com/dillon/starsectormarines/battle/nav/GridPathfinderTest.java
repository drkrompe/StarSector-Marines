package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GridPathfinderTest {

    @Test
    void onDemandPathDoesNotVisitUnrelatedCells() {
        NavigationGrid grid = new NavigationGrid(100, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 100; x++) grid.setWalkableFloor(x, y);
        }
        AtomicInteger clearanceReads = new AtomicInteger();
        AtomicInteger costReads = new AtomicInteger();
        int[] route = GridPathfinder.findPathOnDemand(grid, 2, 10, 80, 10,
                index -> {
                    costReads.incrementAndGet();
                    return 1f;
                }, index -> {
                    clearanceReads.incrementAndGet();
                    return true;
                });

        assertEquals(2, route[0]);
        assertEquals(80, route[route.length - 2]);
        assertTrue(clearanceReads.get() < 1000,
                "clearance should be queried near the route, not across the whole grid");
        assertTrue(costReads.get() < 1000);
    }

    @Test
    void onDemandGateHonorsBlockedEndpoint() {
        NavigationGrid grid = new NavigationGrid(3, 1);
        for (int x = 0; x < 3; x++) grid.setWalkableFloor(x, 0);

        assertSame(GridPathfinder.EMPTY_PATH,
                GridPathfinder.findPathOnDemand(grid, 0, 0, 2, 0,
                        index -> 1f, index -> index != 2));
    }

    @Test
    void incrementalSearchFindsLongDetourWithinEachExpansionAllowance() {
        NavigationGrid grid = detourGrid(false);
        GridPathfinder.OnDemandSearch search = GridPathfinder.beginOnDemand(
                grid, 2, 2, 55, 2, index -> 1f, index -> true);
        int pauses = 0;
        for (int i = 0; i < 2000 && search.status()
                == GridPathfinder.OnDemandSearch.Status.PENDING; i++) {
            int before = search.expandedNodes();
            search.advance(7);
            assertTrue(search.expandedNodes() - before <= 7);
            if (search.status() == GridPathfinder.OnDemandSearch.Status.PENDING) pauses++;
        }

        assertTrue(pauses > 1);
        assertEquals(GridPathfinder.OnDemandSearch.Status.ROUTED, search.status());
        assertArrayEquals(GridPathfinder.findPathOnDemand(grid, 2, 2, 55, 2,
                index -> 1f, index -> true), search.path());
        boolean usedGap = false;
        for (int i = 0; i < search.path().length; i += 2) {
            if (search.path()[i] == 30 && search.path()[i + 1] == 19) usedGap = true;
        }
        assertTrue(usedGap);
    }

    @Test
    void incrementalSearchExhaustsDisconnectedRegionWithoutOneTickFlood() {
        NavigationGrid grid = detourGrid(true);
        GridPathfinder.OnDemandSearch search = GridPathfinder.beginOnDemand(
                grid, 2, 2, 55, 2, index -> 1f, index -> true);
        assertFalse(search.exhaustedReachable(grid.index(2, 2)));
        int pauses = 0;
        for (int i = 0; i < 2000 && search.status()
                == GridPathfinder.OnDemandSearch.Status.PENDING; i++) {
            int before = search.expandedNodes();
            search.advance(5);
            assertTrue(search.expandedNodes() - before <= 5);
            if (search.status() == GridPathfinder.OnDemandSearch.Status.PENDING) pauses++;
        }

        assertTrue(pauses > 1);
        assertEquals(GridPathfinder.OnDemandSearch.Status.NO_ROUTE, search.status());
        assertSame(GridPathfinder.EMPTY_PATH, search.path());
        assertTrue(search.exhaustedReachable(grid.index(2, 2)));
        assertFalse(search.exhaustedReachable(grid.index(55, 2)));
    }

    @Test
    void unrelatedPathfinderCallDoesNotDisturbPausedFrontier() {
        NavigationGrid grid = detourGrid(false);
        GridPathfinder.OnDemandSearch search = GridPathfinder.beginOnDemand(
                grid, 2, 2, 55, 2, index -> 1f, index -> true);
        assertEquals(GridPathfinder.OnDemandSearch.Status.PENDING, search.advance(3));

        NavigationGrid other = new NavigationGrid(5, 1);
        for (int x = 0; x < 5; x++) other.setWalkableFloor(x, 0);
        assertArrayEquals(new int[]{0, 0, 1, 0, 2, 0, 3, 0, 4, 0},
                GridPathfinder.findPath(other, 0, 0, 4, 0));
        for (int i = 0; i < 2000 && search.status()
                == GridPathfinder.OnDemandSearch.Status.PENDING; i++) search.advance(7);

        assertEquals(GridPathfinder.OnDemandSearch.Status.ROUTED, search.status());
        assertArrayEquals(GridPathfinder.findPathOnDemand(grid, 2, 2, 55, 2,
                index -> 1f, index -> true), search.path());
    }

    private static NavigationGrid detourGrid(boolean sealed) {
        NavigationGrid grid = new NavigationGrid(60, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 60; x++) grid.setWalkableFloor(x, y);
        }
        for (int y = 0; y < (sealed ? 20 : 19); y++) {
            grid.setWalkable(30, y, false);
        }
        return grid;
    }

    @Test
    void reportsActualExpandedNodesAndPathLength() {
        NavigationGrid grid = new NavigationGrid(3, 1);
        for (int x = 0; x < 3; x++) grid.setWalkableFloor(x, 0);
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        try {
            assertArrayEquals(new int[]{0, 0, 1, 0, 2, 0},
                    GridPathfinder.findPath(grid, 0, 0, 2, 0));
            TickInnerProfile.PathSearch search = profile.slowPathSearches().get(0);
            assertEquals(3, search.pathCells());
            assertEquals(3, search.expandedNodes());
            assertEquals(3L, profile.pathfindExpandedNodes());
            assertEquals(1, profile.countOf(TickInnerProfile.Bucket.PATHFIND));
        } finally {
            TickInnerProfile.releaseCurrentThread();
        }
    }

    @Test
    void reconstructsValidParentChain() {
        int[] parents = {0, 0, 1, 2};

        int[] path = GridPathfinder.reconstructPathForTest(
                parents, 4, 4, 0, 3);

        assertArrayEquals(new int[]{0, 0, 1, 0, 2, 0, 3, 0}, path);
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void rejectsCyclicParentChainInsteadOfLoopingForever() {
        int[] parents = {0, 2, 1, 2};

        int[] path = GridPathfinder.reconstructPathForTest(
                parents, 4, 4, 0, 2);

        assertSame(GridPathfinder.EMPTY_PATH, path);
    }

    @Test
    void rejectsSelfParentBeforeStart() {
        int[] parents = {0, 1, 1};

        int[] path = GridPathfinder.reconstructPathForTest(
                parents, 3, 3, 0, 2);

        assertSame(GridPathfinder.EMPTY_PATH, path);
    }

    @Test
    void rejectsOutOfBoundsParent() {
        int[] parents = {0, 0, 9};

        int[] path = GridPathfinder.reconstructPathForTest(
                parents, 3, 3, 0, 2);

        assertSame(GridPathfinder.EMPTY_PATH, path);
    }

}
