package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NavigationGridOpacityRevisionTest {
    @Test void onlyChangesToStructuralOpacityInvalidateObservation() {
        NavigationGrid grid = new NavigationGrid(5, 5);
        long initial = grid.opacityRevision();
        grid.setWalkable(2, 2, true);
        assertEquals(initial + 1, grid.opacityRevision());
        grid.setWalkable(2, 2, true);
        grid.setSeeThrough(2, 2, true);
        grid.setTag(2, 2, NavigationGrid.CellTag.NATURAL_SOLID, true);
        grid.setWalkable(2, 2, false); // see-through fixture stays transparent
        assertEquals(initial + 1, grid.opacityRevision());
        grid.setSeeThrough(2, 2, false);
        assertEquals(initial + 2, grid.opacityRevision());
        grid.setTag(2, 2, NavigationGrid.CellTag.NATURAL_SOLID, false);
        grid.setWallHp(2, 2, 10);
        assertTrue(grid.damageCell(2, 2, 10));
        assertEquals(initial + 3, grid.opacityRevision(), "the raw wall-breach mutation also opens sight");
    }
}
