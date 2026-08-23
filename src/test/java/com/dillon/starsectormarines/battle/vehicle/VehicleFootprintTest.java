package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleFootprintTest {

    @Test
    void withinGridDoesNotMistakeObstacleOverlapForOffMap() {
        NavigationGrid grid = new NavigationGrid(20, 20);

        assertTrue(VehicleFootprint.isPoseWithinGrid(10.5f, 10.5f, 0f,
                3f, 2f, grid));
        assertFalse(VehicleFootprint.isPoseFeasible(10.5f, 10.5f, 0f,
                3f, 2f, grid));
        assertFalse(VehicleFootprint.isPoseWithinGrid(0.25f, 10.5f, 0f,
                3f, 2f, grid));
    }
}
