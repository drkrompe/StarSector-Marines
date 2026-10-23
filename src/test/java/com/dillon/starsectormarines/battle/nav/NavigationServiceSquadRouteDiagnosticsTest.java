package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationServiceSquadRouteDiagnosticsTest {
    @Test
    void earlySeedRejectionDoesNotInheritPreviousExpansions() {
        NavigationGrid grid = openGrid(8, 3);
        GridPathfinder.findSquadRouteSeed(grid, 1, 1, 6, 1, true, null);
        assertTrue(GridPathfinder.squadSeedExpandedNodes() > 0);
        GridPathfinder.findSquadRouteSeed(grid, -1, 1, 6, 1, true, null);
        assertEquals(0, GridPathfinder.squadSeedExpandedNodes());
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
