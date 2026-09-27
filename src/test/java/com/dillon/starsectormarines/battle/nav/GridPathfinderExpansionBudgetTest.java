package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridPathfinderExpansionBudgetTest {
    @Test
    void bothGeometricModesStopAtTheActualExpansionAllowance() {
        NavigationGrid grid = openGrid(new NavigationGrid(32, 24));
        for (boolean cardinal : new boolean[]{true, false}) {
            for (boolean envelope : new boolean[]{true, false}) {
                for (int allowance : new int[]{1, 2, 7}) {
                    assertSame(GridPathfinder.EMPTY_PATH,
                            search(grid, cardinal, envelope, allowance));
                    assertEquals(allowance, GridPathfinder.lastSearchExpandedNodes());
                }
            }
        }
    }

    @Test
    void sufficientAllowancePreservesOriginalPathsIncludingHeapTies() {
        Random random = new Random(91721);
        for (boolean cardinal : new boolean[]{true, false}) {
            for (int trial = 0; trial < 25; trial++) {
                NavigationGrid grid = openGrid(new NavigationGrid(13, 11));
                for (int y = 0; y < 11; y++) {
                    for (int x = 0; x < 13; x++) {
                        if (random.nextInt(7) == 0) grid.setWalkable(x, y, false);
                        if (random.nextInt(17) == 0) grid.blockEdge(x, y, Direction.E);
                    }
                }
                grid.setWalkableFloor(1, 1);
                grid.setWalkableFloor(11, 9);
                int[] original = GridPathfinder.findPath(grid, 1, 1, 11, 9, cardinal, null);
                assertArrayEquals(original, GridPathfinder.findPathWithinExpansionBudget(
                        grid, 1, 1, 11, 9, cardinal, 143));
                assertTrue(GridPathfinder.lastSearchExpandedNodes() <= 143);
                int[] originalEnvelope = GridPathfinder.findPathWithinStepEnvelope(
                        grid, 1, 1, 11, 9, cardinal, 30);
                assertArrayEquals(originalEnvelope, GridPathfinder.findPathWithinStepEnvelope(
                        grid, 1, 1, 11, 9, cardinal, 30, 143));
                assertTrue(GridPathfinder.lastSearchExpandedNodes() <= 143);
            }
        }
    }

    @Test
    void cappedFrontierDoesNotPoisonTheNextSearchOrItsCount() {
        NavigationGrid grid = openGrid(new NavigationGrid(32, 24));
        for (boolean cardinal : new boolean[]{true, false}) {
            int[] expected = GridPathfinder.findPath(grid, 1, 1, 30, 22, cardinal, null);
            for (boolean envelope : new boolean[]{true, false}) {
                assertSame(GridPathfinder.EMPTY_PATH, search(grid, cardinal, envelope, 7));
                assertEquals(7, GridPathfinder.lastSearchExpandedNodes());
                assertArrayEquals(expected, search(grid, cardinal, envelope, 768));
                int fullCount = GridPathfinder.lastSearchExpandedNodes();
                assertTrue(fullCount > 7 && fullCount <= 768);
                assertArrayEquals(expected, search(grid, cardinal, envelope, fullCount));
                assertEquals(fullCount, GridPathfinder.lastSearchExpandedNodes());
                assertSame(GridPathfinder.EMPTY_PATH,
                        search(grid, cardinal, envelope, fullCount - 1));
                assertEquals(fullCount - 1, GridPathfinder.lastSearchExpandedNodes());
                assertArrayEquals(expected,
                        GridPathfinder.findPath(grid, 1, 1, 30, 22, cardinal, null));
            }
        }
    }

    @Test
    void zeroInvalidAndTrivialQueriesResetTheLastSearchCount() {
        NavigationGrid grid = openGrid(new NavigationGrid(32, 24));
        for (boolean envelope : new boolean[]{true, false}) {
            search(grid, true, envelope, 7);
            assertSame(GridPathfinder.EMPTY_PATH, search(grid, true, envelope, 0));
            assertEquals(0, GridPathfinder.lastSearchExpandedNodes());
            search(grid, true, envelope, 7);
            assertThrows(IllegalArgumentException.class, () -> search(grid, true, envelope, -1));
            assertEquals(0, GridPathfinder.lastSearchExpandedNodes());
        }
        assertSame(GridPathfinder.EMPTY_PATH, GridPathfinder.findPathWithinExpansionBudget(
                grid, 1, 1, 1, 1, true, 0));
        assertSame(GridPathfinder.EMPTY_PATH, GridPathfinder.findPathWithinStepEnvelope(
                grid, 1, 1, 1, 1, true, 0, 0));
        assertArrayEquals(new int[]{1, 1}, GridPathfinder.findPathWithinExpansionBudget(
                grid, 1, 1, 1, 1, true, 1));
        assertEquals(0, GridPathfinder.lastSearchExpandedNodes());
        search(grid, true, false, 7);
        assertSame(GridPathfinder.EMPTY_PATH, GridPathfinder.findPathWithinExpansionBudget(
                grid, -1, 1, 1, 1, true, 7));
        assertEquals(0, GridPathfinder.lastSearchExpandedNodes());
        grid.setWalkable(1, 1, false);
        search(grid, true, false, 7);
        assertSame(GridPathfinder.EMPTY_PATH, GridPathfinder.findPathWithinStepEnvelope(
                grid, 0, 0, 1, 1, true, 5, 7));
        assertEquals(0, GridPathfinder.lastSearchExpandedNodes());
    }

    @Test
    void cappedQueriesNeverAskForColdWholeMapComponents() {
        NavigationGrid grid = openGrid(new NavigationGrid(32, 24) {
            @Override
            public boolean arePathConnected(int startX, int startY, int goalX, int goalY,
                                            boolean cardinalOnly) {
                throw new AssertionError("budgeted A* must not ask for component labels");
            }
        });
        for (boolean cardinal : new boolean[]{true, false}) {
            for (boolean envelope : new boolean[]{true, false}) {
                assertSame(GridPathfinder.EMPTY_PATH, search(grid, cardinal, envelope, 7));
                assertTrue(search(grid, cardinal, envelope, 768).length > 0);
            }
        }
    }

    @Test
    void envelopeStillRejectsBeforeExpandingAndOrdinaryControlsResetCounters() {
        NavigationGrid grid = openGrid(new NavigationGrid(32, 24));
        search(grid, true, false, 7);
        assertSame(GridPathfinder.EMPTY_PATH, GridPathfinder.findPathWithinStepEnvelope(
                grid, 1, 1, 30, 22, true, 3, 768));
        assertEquals(0, GridPathfinder.lastSearchExpandedNodes());
        search(grid, true, false, 7);
        GridPathfinder.findPath(grid, 1, 1, 1, 1, true, null);
        assertEquals(0, GridPathfinder.lastSearchExpandedNodes());
        search(grid, true, false, 7);
        GridPathfinder.findPathWithinStepEnvelope(grid, 1, 1, 1, 1, true, 0);
        assertEquals(0, GridPathfinder.lastSearchExpandedNodes());
        search(grid, true, false, 7);
        assertThrows(IllegalArgumentException.class, () ->
                GridPathfinder.findPathWithinStepEnvelope(grid, 1, 1, 2, 1, true, -1, 7));
        assertEquals(0, GridPathfinder.lastSearchExpandedNodes());
    }

    private static int[] search(NavigationGrid grid, boolean cardinal,
                                boolean envelope, int allowance) {
        return envelope
                ? GridPathfinder.findPathWithinStepEnvelope(grid, 1, 1, 30, 22,
                        cardinal, 100, allowance)
                : GridPathfinder.findPathWithinExpansionBudget(grid, 1, 1, 30, 22,
                        cardinal, allowance);
    }

    private static NavigationGrid openGrid(NavigationGrid grid) {
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
