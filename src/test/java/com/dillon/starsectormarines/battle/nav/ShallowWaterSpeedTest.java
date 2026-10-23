package com.dillon.starsectormarines.battle.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShallowWaterSpeedTest {

    @Test
    void shallowWaterIsWadedSlowlyAndStaysWalkable() {
        NavigationGrid grid = new NavigationGrid(3, 1);
        for (int x = 0; x < 3; x++) grid.setWalkable(x, 0, true);
        grid.setTag(1, 0, NavigationGrid.CellTag.SHALLOW_WATER, true);
        grid.setTag(2, 0, NavigationGrid.CellTag.SHALLOW_WATER, true);
        grid.setTag(2, 0, NavigationGrid.CellTag.CRASH_DEBRIS, true);

        assertEquals(1f, grid.groundSpeedMultiplier(0.5f, 0.5f));
        assertEquals(NavigationGrid.SHALLOW_WATER_SPEED, grid.groundSpeedMultiplier(1.5f, 0.5f));
        assertTrue(NavigationGrid.SHALLOW_WATER_SPEED < 1f);
        assertEquals(.5f, grid.groundSpeedMultiplier(2.5f, 0.5f), "the slower of the two");
        assertTrue(grid.isWalkable(1, 0));
    }
}
