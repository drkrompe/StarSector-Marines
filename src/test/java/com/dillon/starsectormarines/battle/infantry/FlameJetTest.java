package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a player's flamer jet lands: range, the map edge, and the first wall. */
class FlameJetTest {

    private static NavigationGrid open(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) grid.setWalkable(x, y, true);
        return grid;
    }

    @Test
    void aCursorInReachIsWhereTheJetLands() {
        float[] point = FlameJet.point(open(20, 9), 2.5f, 4.5f, 6.5f, 4.5f, 7f);
        assertNotNull(point);
        assertEquals(6.5f, point[0], 1e-4f);
        assertEquals(4.5f, point[1], 1e-4f);
    }

    @Test
    void aCursorPastReachIsPulledInToTheFlamersRange() {
        float[] point = FlameJet.point(open(20, 9), 2.5f, 4.5f, 18.5f, 4.5f, 7f);
        assertNotNull(point);
        assertEquals(9.5f, point[0], 1e-4f);
    }

    @Test
    void aWallStopsTheJetShortAndAWallAtTheNozzleHoldsIt() {
        NavigationGrid grid = open(20, 9);
        grid.setWalkable(5, 4, false);
        float[] point = FlameJet.point(grid, 2.5f, 4.5f, 8.5f, 4.5f, 7f);
        assertNotNull(point);
        assertTrue(point[0] < 5f, "the jet went through the wall to x=" + point[0]);
        assertTrue(point[0] > 4f, "the jet stopped well short of the wall, at x=" + point[0]);
        grid.setWalkable(3, 4, false);
        assertNull(FlameJet.point(grid, 2.5f, 4.5f, 8.5f, 4.5f, 7f));
    }

    @Test
    void aCursorOnTheMarineSendsNothing() {
        assertNull(FlameJet.point(open(20, 9), 2.5f, 4.5f, 2.5f, 4.5f, 7f));
    }
}
