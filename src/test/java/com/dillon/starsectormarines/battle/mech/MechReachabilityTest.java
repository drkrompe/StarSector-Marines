package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Direct picker-component tests: no battle construction or clearance workers. */
class MechReachabilityTest {
    @Test
    void membershipMatchesComponentLabelsAcrossWallsEdgesAndOpenedDoorway() {
        boolean previous = GridPathfinder.USE_CARDINAL_NAVIGATION;
        try {
            for (boolean cardinal : new boolean[]{false, true}) {
                GridPathfinder.USE_CARDINAL_NAVIGATION = cardinal;
                NavigationGrid grid = splitGrid();
                assertMembershipMatchesLabels(grid);
                grid.setWalkableFloor(3, 2);
                assertMembershipMatchesLabels(grid);
                grid.setWalkable(3, 2, false);
                assertMembershipMatchesLabels(grid);
            }
        } finally {
            GridPathfinder.USE_CARDINAL_NAVIGATION = previous;
        }
    }

    @Test
    void warmedPickersReuseLabelsUntilTopologyChanges() {
        CountingGrid grid = new CountingGrid(7, 5);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 7; x++) grid.setWalkableFloor(x, y);
        }
        MechReachability first = new MechReachability(grid, 0, 0);
        assertTrue(first.contains(6, 4));
        assertEquals(1, grid.labelReads);

        for (int i = 0; i < 20; i++) {
            MechReachability next = new MechReachability(grid, i % 7, 0);
            assertTrue(next.contains(6, 4));
        }
        assertEquals(1, grid.labelReads, "new picker instances share the warmed grid cache");

        for (int y = 0; y < 5; y++) grid.setWalkable(3, y, false);
        assertFalse(first.contains(6, 4), "a retained query must observe the new topology");
        assertEquals(2, grid.labelReads);
        assertFalse(new MechReachability(grid, 0, 0).contains(6, 4));
        assertEquals(2, grid.labelReads);
    }

    @Test
    void rawConnectivityDoesNotClaimChassisClearance() {
        NavigationGrid grid = splitGrid();
        grid.setWalkableFloor(3, 2);
        assertTrue(new MechReachability(grid, 1, 2).contains(5, 2),
                "a one-cell doorway is raw reachable; body fit remains the route planner's job");
        assertFalse(ManualTerrainMotion.canStand(grid, 3.5f, 2.5f, 0.60f),
                "raw connectivity must not be mistaken for Bulwark clearance");
        assertFalse(new MechReachability(grid, 3, 0).contains(3, 0));
        assertFalse(new MechReachability(grid, -1, 0).contains(0, 0));
        assertFalse(new MechReachability(grid, 0, 0).contains(7, 0));
    }

    private static void assertMembershipMatchesLabels(NavigationGrid grid) {
        int[] labels = GridPathfinder.labelConnectedComponents(grid);
        for (int ay = -1; ay <= grid.getHeight(); ay++) {
            for (int ax = -1; ax <= grid.getWidth(); ax++) {
                MechReachability reachable = new MechReachability(grid, ax, ay);
                for (int by = -1; by <= grid.getHeight(); by++) {
                    for (int bx = -1; bx <= grid.getWidth(); bx++) {
                        boolean expected = grid.inBounds(ax, ay) && grid.inBounds(bx, by)
                                && labels[grid.index(ax, ay)] >= 0
                                && labels[grid.index(ax, ay)] == labels[grid.index(bx, by)];
                        assertEquals(expected, reachable.contains(bx, by));
                    }
                }
            }
        }
    }

    private static NavigationGrid splitGrid() {
        NavigationGrid grid = new NavigationGrid(7, 5);
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 7; x++) grid.setWalkableFloor(x, y);
            grid.setWalkable(3, y, false);
        }
        // Exercise dual-side barriers in addition to blocked cells.
        grid.setEdgePassable(1, 1, Direction.W, false);
        grid.setEdgePassable(0, 2, Direction.N, false);
        return grid;
    }

    private static final class CountingGrid extends NavigationGrid {
        private int labelReads;

        CountingGrid(int width, int height) { super(width, height); }

        @Override public long[] getCellFlagsArray() {
            labelReads++;
            return super.getCellFlagsArray();
        }
    }
}
