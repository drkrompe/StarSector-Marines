package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A scout's mast sees by ordinary line of sight, out to its own range and no farther. */
class HullSightingsTest {

    private static NavigationGrid floor(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        return grid;
    }

    @Test
    void seesOpenGroundOutToItsRange() {
        NavigationGrid grid = floor(64, 8);
        float range = VehicleType.SCOUT_CAR.sightCells();
        assertTrue(HullSightings.sees(grid, 2.5f, 4.5f, 2.5f + range - .5f, 4.5f, range));
        assertFalse(HullSightings.sees(grid, 2.5f, 4.5f, 2.5f + range + .5f, 4.5f, range), "past its range");
    }

    @Test
    void aWallHidesWhatIsBehindIt() {
        NavigationGrid grid = floor(32, 8);
        for (int y = 0; y < 8; y++) grid.setWalkable(16, y, false);
        assertFalse(HullSightings.sees(grid, 4.5f, 4.5f, 24.5f, 4.5f, 48f));
        assertTrue(HullSightings.sees(grid, 4.5f, 4.5f, 12.5f, 4.5f, 48f));
    }
}
