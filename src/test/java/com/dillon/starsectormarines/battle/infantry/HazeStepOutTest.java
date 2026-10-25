package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.smoke.HazeEdge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a marine goes to leave a spore haze, and when he stops at its edge rather than walk back in. */
class HazeStepOutTest {

    @Test
    void theRefugeIsTheNearestClearFreeCell() {
        NavigationGrid grid = openGrid(12, 9);
        hazeBlock(grid, 3, 2, 7, 6);
        byte[] occupancy = new byte[12 * 9];
        // From the middle of the haze the nearest clear cells are three steps out; west of the block is taken.
        occupancy[grid.index(2, 4)] = 1;
        int refuge = HazeStepOut.refuge(grid, occupancy, 5, 4);
        assertFalse(grid.hasHaze(refuge % 12, refuge / 12));
        assertEquals(0, occupancy[refuge], "an occupied cell is not a refuge");
        assertEquals(3, Math.max(Math.abs(refuge % 12 - 5), Math.abs(refuge / 12 - 4)));
        assertEquals(-1, HazeStepOut.refuge(openHazed(), new byte[25], 2, 2), "nowhere clear in reach");
    }

    @Test
    void aWalkerWaitsAtTheEdgeOnlyToAvoidStandingInIt() {
        NavigationGrid grid = openGrid(12, 9);
        hazeBlock(grid, 5, 0, 7, 8);
        assertTrue(HazeEdge.waits(grid, 4, 4, 5, 4, 6, 4), "it would walk in only to stand in the haze");
        assertFalse(HazeEdge.waits(grid, 4, 4, 5, 4, 10, 4), "crossing to clear ground beyond goes on");
        assertFalse(HazeEdge.waits(grid, 6, 4, 7, 4, 6, 3), "already inside: the step-out decides, not the edge");
    }

    private static void hazeBlock(NavigationGrid grid, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) grid.addHazeAt(grid.index(x, y));
    }

    private static NavigationGrid openHazed() {
        NavigationGrid grid = openGrid(5, 5);
        hazeBlock(grid, 0, 0, 4, 4);
        return grid;
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
