package com.dillon.starsectormarines.battle.world.gen.forest;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LandscapeAccessTest {
    @Test void openGroundAdmitsAnIslandOfTreesButAThroatDoesNot() {
        NavigationGrid grid = floor(11, 9);
        boolean[] earlier = new boolean[99];
        assertTrue(LandscapeAccess.canClose(grid, earlier, 4, 3, 6, 5,
                (x, y) -> x >= 4 && x <= 6 && y >= 3 && y <= 5));
        for (int y = 0; y < 9; y++) if (y != 4) grid.setWalkable(5, y, false);
        assertFalse(LandscapeAccess.canClose(grid, earlier, 5, 4, 5, 4, (x, y) -> x == 5 && y == 4));
    }

    @Test void earlierCanopiesAndSharedEdgesCannotHideASealedPocket() {
        NavigationGrid grid = floor(9, 9);
        boolean[] earlier = new boolean[81];
        // A U-shaped forest with one opening north of its center.
        for (int y = 3; y <= 5; y++) { earlier[y * 9 + 3] = true; earlier[y * 9 + 5] = true; }
        earlier[3 * 9 + 4] = true;
        assertFalse(LandscapeAccess.canClose(grid, earlier, 4, 5, 4, 5, (x, y) -> x == 4 && y == 5));
        NavigationGrid edges = floor(9, 9);
        for (int y = 0; y < 9; y++) if (y != 4) edges.setSharedEdgePassable(4, y, Direction.E, false);
        assertFalse(LandscapeAccess.canClose(edges, new boolean[81], 4, 4, 4, 4, (x, y) -> x == 4 && y == 4));
    }

    @Test void aCrownMayNotNarrowAGapBelowAHullsWidth() {
        // A wall across x=8 with a five-cell opening (y 6..10): a radius-2 hull drives through on y=8 only.
        NavigationGrid grid = floor(17, 17);
        for (int y = 0; y < 17; y++) if (y < 6 || y > 10) grid.setWalkable(8, y, false);
        boolean[] earlier = new boolean[17 * 17];
        // One cell in the opening still leaves infantry a way, but no tank.
        assertTrue(LandscapeAccess.canClose(grid, earlier, 8, 10, 8, 10, (x, y) -> x == 8 && y == 10));
        assertFalse(LandscapeAccess.keepsClearance(grid, earlier, 8, 10, 8, 10, (x, y) -> x == 8 && y == 10, 2));
        // The same crown out in the open costs the hull nothing.
        assertTrue(LandscapeAccess.keepsClearance(grid, earlier, 3, 3, 3, 3, (x, y) -> x == 3 && y == 3, 2));
    }

    @Test void aPocketAlreadyTooNarrowForAHullMayBeWooded() {
        // The opening is four cells: no tank fits before the crown, so closing a cell of it strands nothing.
        NavigationGrid grid = floor(17, 17);
        for (int y = 0; y < 17; y++) if (y < 6 || y > 9) grid.setWalkable(8, y, false);
        assertTrue(LandscapeAccess.keepsClearance(grid, new boolean[17 * 17], 8, 9, 8, 9,
                (x, y) -> x == 8 && y == 9, 2));
    }

    @Test void routeReservationFollowsTheActualOpeningAndKeepsItsWidth() {
        NavigationGrid grid = floor(15, 11);
        for (int y = 0; y < 11; y++) if (y != 7) grid.setWalkable(7, y, false);
        boolean[] reserved = new boolean[165];
        LandscapeAccess.reserveRoutes(grid, List.of(new int[]{2, 4}, new int[]{12, 4}), reserved);
        for (int y = 5; y <= 9; y++) for (int x = 5; x <= 9; x++) assertTrue(reserved[y * 15 + x]);
        assertTrue(reserved[4 * 15 + 2]); assertTrue(reserved[4 * 15 + 12]);
        assertFalse(reserved[0], "reservations should not consume all available countryside");
    }

    private static NavigationGrid floor(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        return grid;
    }
}
