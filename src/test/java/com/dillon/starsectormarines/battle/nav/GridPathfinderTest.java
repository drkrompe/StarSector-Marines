package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GridPathfinderTest {

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
