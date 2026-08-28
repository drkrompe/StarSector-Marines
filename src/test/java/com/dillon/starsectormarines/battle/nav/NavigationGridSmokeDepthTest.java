package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationGridSmokeDepthTest {

    @Test
    void smokelessGridMeasuresZero() {
        NavigationGrid grid = openGrid(10, 5);
        assertEquals(0, grid.smokeDepthOnLine(0.5f, 2.5f, 9.5f, 2.5f));
    }

    @Test
    void countsOnlyTheSmokedCellsTheSegmentCrosses() {
        NavigationGrid grid = openGrid(10, 5);
        smoke(grid, 4, 2);
        smoke(grid, 5, 2);
        smoke(grid, 6, 3); // off the lane

        assertEquals(2, grid.smokeDepthOnLine(0.5f, 2.5f, 9.5f, 2.5f));
    }

    @Test
    void endpointCellsCountBecauseStandingInACloudObscuresToo() {
        NavigationGrid grid = openGrid(10, 5);
        smoke(grid, 0, 2);
        smoke(grid, 9, 2);

        assertEquals(2, grid.smokeDepthOnLine(0.5f, 2.5f, 9.5f, 2.5f),
                "a shooter inside its own cloud and a target hiding in one both pay");
        assertEquals(1, grid.smokeDepthOnLine(0.5f, 2.5f, 0.9f, 2.5f),
                "a segment inside one smoked cell is one cell deep");
    }

    @Test
    void refCountedCloudsStopCountingOnceTheLastFieldLifts() {
        NavigationGrid grid = openGrid(10, 5);
        int idx = grid.index(5, 2);
        grid.addTransientOpacityAt(idx);
        grid.addTransientOpacityAt(idx); // two overlapping clouds, one cell

        assertEquals(1, grid.smokeDepthOnLine(0.5f, 2.5f, 9.5f, 2.5f),
                "depth counts cells, not stacked fields");

        grid.removeTransientOpacityAt(idx);
        assertEquals(1, grid.smokeDepthOnLine(0.5f, 2.5f, 9.5f, 2.5f));
        grid.removeTransientOpacityAt(idx);
        assertEquals(0, grid.smokeDepthOnLine(0.5f, 2.5f, 9.5f, 2.5f));
    }

    @Test
    void smokeBlocksSightWhileLeavingFireAndTheWallTraceAlone() {
        NavigationGrid grid = openGrid(10, 5);
        smoke(grid, 5, 2);

        assertFalse(grid.hasLineOfSight(0, 2, 9, 2), "sight still stops at a cloud");
        assertTrue(grid.hasLineOfFire(0.5f, 2.5f, 9.5f, 2.5f),
                "fire is graded by depth instead of gated");
        assertEquals(-1, (int) grid.firstWallOnLine(0.5f, 2.5f, 9.5f, 2.5f),
                "a round in flight crosses the cloud untouched");
    }

    @Test
    void aSmokedWallStillStopsFire() {
        NavigationGrid grid = openGrid(10, 5);
        grid.setWalkable(5, 2, false);
        smoke(grid, 5, 2);

        assertFalse(grid.hasLineOfFire(0.5f, 2.5f, 9.5f, 2.5f),
                "smoke over a wall must not launder the wall away");
    }

    private static void smoke(NavigationGrid grid, int x, int y) {
        grid.addTransientOpacityAt(grid.index(x, y));
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
