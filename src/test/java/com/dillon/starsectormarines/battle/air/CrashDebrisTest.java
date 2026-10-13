package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrashDebrisTest {
    @Test void fragmentsSlowOpenGroundWithoutOpeningMountainsOrWalls() {
        var grid = new NavigationGrid(9, 9);
        grid.setWalkableFloor(4, 4);
        grid.setTag(5, 4, NavigationGrid.CellTag.NATURAL_SOLID, true);
        AirframeFootprint.settleCrashDebris(grid, 4.5f, 4.5f, 2f);
        assertTrue(grid.isWalkable(4, 4));
        assertFalse(grid.isWalkable(5, 4));
        assertFalse(grid.isWalkable(3, 4));
        assertEquals(.5f, grid.groundSpeedMultiplier(4.5f, 4.5f));
        assertEquals(1f, grid.groundSpeedMultiplier(0.5f, 0.5f));
        assertEquals(.5f, grid.copyRoutingTopology().groundSpeedMultiplier(4.5f, 4.5f));
    }
}
