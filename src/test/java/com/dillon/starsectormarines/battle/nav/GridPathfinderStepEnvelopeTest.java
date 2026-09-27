package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GridPathfinderStepEnvelopeTest {
    @Test
    void boundedSearchPreservesHeapTiesAndEveryAdmissibleOriginalRoute() {
        Random random = new Random(19171);
        for (boolean cardinal : new boolean[]{true, false}) {
            for (int trial = 0; trial < 30; trial++) {
                NavigationGrid grid = openGrid(13, 11);
                for (int y = 0; y < 11; y++) {
                    for (int x = 0; x < 13; x++) {
                        if (random.nextInt(7) == 0) grid.setWalkable(x, y, false);
                        if (random.nextInt(17) == 0) grid.blockEdge(x, y, Direction.E);
                    }
                }
                grid.setWalkableFloor(1, 1);
                grid.setWalkableFloor(11, 9);
                int[] original = GridPathfinder.findPath(grid, 1, 1, 11, 9, cardinal, null);
                for (int limit : new int[]{0, 10, 15, 20, 30}) {
                    int[] bounded = GridPathfinder.findPathWithinStepEnvelope(
                            grid, 1, 1, 11, 9, cardinal, limit);
                    if (original.length > 0 && original.length / 2 - 1 <= limit) {
                        assertArrayEquals(original, bounded);
                    } else if (bounded.length > 0) {
                        // Envelope is a conservative rejection proof, not a
                        // replacement for the caller's final step-count test.
                        assertArrayEquals(original, bounded);
                    }
                }
            }
        }
    }

    @Test
    void diagonalBoundaryIncludesAccumulatedFloatRounding() {
        NavigationGrid grid = openGrid(100, 100);
        assertArrayEquals(GridPathfinder.findPath(grid, 0, 0, 99, 99, false, null),
                GridPathfinder.findPathWithinStepEnvelope(grid, 0, 0, 99, 99, false, 99));
    }

    @Test
    void zeroLengthBlockedAndInvalidInputsRetainTheirMeaning() {
        NavigationGrid grid = openGrid(3, 1);
        assertArrayEquals(new int[]{0, 0}, GridPathfinder.findPathWithinStepEnvelope(
                grid, 0, 0, 0, 0, true, 0));
        assertSame(GridPathfinder.EMPTY_PATH, GridPathfinder.findPathWithinStepEnvelope(
                grid, 0, 0, 2, 0, true, 1));
        grid.setWalkable(2, 0, false);
        assertSame(GridPathfinder.EMPTY_PATH, GridPathfinder.findPathWithinStepEnvelope(
                grid, 0, 0, 2, 0, true, 5));
        assertThrows(IllegalArgumentException.class, () -> GridPathfinder.findPathWithinStepEnvelope(
                grid, 0, 0, 1, 0, true, -1));
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
