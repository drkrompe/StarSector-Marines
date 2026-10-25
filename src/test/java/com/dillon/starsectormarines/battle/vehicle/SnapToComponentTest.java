package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** {@link VehicleRoutePlanner#snapToComponent}: the nearest cell the chassis can reach, never a nearer one it cannot. */
class SnapToComponentTest {

    /** A yard x 1..5 and a sealed yard x 7..11, a wall column at x 6 between them. */
    private static NavigationGrid twoYards() {
        NavigationGrid grid = new NavigationGrid(13, 7);
        for (int y = 1; y < 6; y++) {
            for (int x = 1; x < 12; x++) if (x != 6) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    @Test
    void aGoalInASealedYardSnapsToTheNearestCellOnTheHullsSide() {
        NavigationGrid grid = twoYards();
        VehicleClearance mask = VehicleClearance.erode(grid, 0);
        ClearanceComponents components = ClearanceComponents.of(grid, mask);

        assertArrayEquals(new int[]{8, 3}, VehicleRoutePlanner.snapToMask(mask, 8, 3, 6));
        assertArrayEquals(new int[]{5, 3}, VehicleRoutePlanner.snapToComponent(mask, components, 2, 3, 8, 3, 6));
        assertArrayEquals(new int[]{4, 2}, VehicleRoutePlanner.snapToComponent(mask, components, 2, 3, 4, 2, 6));
    }

    @Test
    void nothingReachableInRangeOrAStartOffTheMaskSnapsToNothing() {
        NavigationGrid grid = twoYards();
        VehicleClearance mask = VehicleClearance.erode(grid, 0);
        ClearanceComponents components = ClearanceComponents.of(grid, mask);

        assertNull(VehicleRoutePlanner.snapToComponent(mask, components, 2, 3, 10, 3, 2));
        assertNull(VehicleRoutePlanner.snapToComponent(mask, components, 6, 3, 2, 3, 6));
    }
}
