package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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

}
