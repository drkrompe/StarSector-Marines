package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridPathfinderTest {

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

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void concurrentSearchesKeepThreadLocalParentChainsIndependent()
            throws Exception {
        int width = 64;
        int height = 48;
        NavigationGrid grid = openGrid(width, height);
        byte[] occupancy = new byte[width * height];
        for (int i = 0; i < occupancy.length; i += 11) occupancy[i] = 3;

        ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> searches = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) {
                final int workerIndex = worker;
                searches.add(() -> {
                    for (int iteration = 0; iteration < 250; iteration++) {
                        int startY = Math.floorMod(workerIndex * 7 + iteration,
                                height);
                        int goalY = height - 1 - startY;
                        int[] path = GridPathfinder.findPath(grid,
                                0, startY, width - 1, goalY, occupancy);
                        if (path.length < 4
                                || path[0] != 0 || path[1] != startY
                                || path[path.length - 2] != width - 1
                                || path[path.length - 1] != goalY) {
                            return false;
                        }
                    }
                    return true;
                });
            }

            List<Future<Boolean>> results = workers.invokeAll(searches);
            for (Future<Boolean> result : results) {
                assertTrue(result.get(),
                        "concurrent search returned an invalid endpoint chain");
            }
        } finally {
            workers.shutdownNow();
        }
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return grid;
    }
}
