package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShadowcastTransientOpacityTest {

    @Test
    void clearAirCastPassesSmokeButStillStopsAtStructure() {
        NavigationGrid grid = new NavigationGrid(12, 5);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        grid.addTransientOpacityAt(grid.index(4, 2));
        grid.setWalkable(8, 2, false);

        int[] ordinary = new int[Shadowcast.maxCells(10)];
        int ordinaryCount = Shadowcast.castFrom(grid, 1, 2, 10, 0f, ordinary, 0);
        int[] clearAir = new int[Shadowcast.maxCells(10)];
        int clearAirCount = Shadowcast.castFromIgnoringTransientOpacity(
                grid, 1, 2, 10, 0f, clearAir, 0);

        assertFalse(contains(ordinary, ordinaryCount, grid.index(6, 2)),
                "smoke should stop the real observation footprint");
        assertTrue(contains(clearAir, clearAirCount, grid.index(6, 2)),
                "the counterfactual footprint should pass through smoke");
        assertFalse(contains(clearAir, clearAirCount, grid.index(10, 2)),
                "the counterfactual footprint must still stop at structure");
    }

    private static boolean contains(int[] cells, int count, int expected) {
        return Arrays.stream(cells, 0, count).anyMatch(cell -> cell == expected);
    }
}
