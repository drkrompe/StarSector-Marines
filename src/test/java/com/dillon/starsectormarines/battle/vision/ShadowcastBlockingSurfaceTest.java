package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class ShadowcastBlockingSurfaceTest {
    @Test void firstSolidSurfaceIsVisibleButItsFarSideRemainsHiddenInEveryOctant() {
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            if (dx == 0 && dy == 0) continue;
            NavigationGrid grid = openGrid();
            int bx = 8 + dx * 3, by = 8 + dy * 3;
            grid.setWalkable(bx, by, false);
            int[] out = new int[Shadowcast.maxCells(8)];
            int n = Shadowcast.castFrom(grid, 8, 8, 8, 0, out, 0);
            assertTrue(contains(out, n, grid.index(bx, by)), "the blocker itself must be visible");
            assertFalse(contains(out, n, grid.index(bx + dx, by + dy)), "no reveal through the blocker");
        }
    }

    @Test void smokeStillConcealsItsOwnCellAndASolidSurfaceInsideIt() {
        NavigationGrid grid = openGrid();
        grid.addTransientOpacityAt(grid.index(11, 8));
        for (boolean solid : new boolean[]{false, true}) {
            grid.setWalkable(11, 8, !solid);
            int[] out = new int[Shadowcast.maxCells(8)];
            int n = Shadowcast.castFrom(grid, 8, 8, 8, 0, out, 0);
            assertFalse(contains(out, n, grid.index(11, 8)));
            assertFalse(contains(out, n, grid.index(12, 8)));
            n = Shadowcast.castFromIgnoringTransientOpacity(grid, 8, 8, 8, 0, out, 0);
            assertTrue(contains(out, n, grid.index(11, 8)));
            assertEquals(!solid, contains(out, n, grid.index(12, 8)));
        }
    }

    private static boolean contains(int[] out, int n, int index) {
        return Arrays.stream(out, 0, n).anyMatch(cell -> cell == index);
    }
    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(18, 18);
        for (int y = 0; y < 18; y++) for (int x = 0; x < 18; x++) grid.setWalkableFloor(x, y);
        return grid;
    }
}
