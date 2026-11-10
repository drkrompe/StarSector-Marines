package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.WallCrush;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechWallCrushTest {

    @Test
    void theStripLiesAheadOfTheWayTheWalkerGoes() {
        NavigationGrid grid = new NavigationGrid(10, 10);
        Set<Integer> north = new HashSet<>();
        MechWallCrush.forEachBearingCell(grid, 5.5f, 5.5f, 0f, 1f, .6f, (x, y) -> north.add(y * 10 + x));
        assertTrue(north.contains(6 * 10 + 5), "the cell just north of a walker going north is borne on");
        assertFalse(north.contains(4 * 10 + 5), "nothing behind it is");
        for (int cell : north) assertTrue(cell / 10 >= 5, "no cell lies behind the walker's own row");

        Set<Integer> east = new HashSet<>();
        MechWallCrush.forEachBearingCell(grid, 5.5f, 5.5f, 1f, 0f, .6f, (x, y) -> east.add(y * 10 + x));
        assertTrue(east.contains(5 * 10 + 6), "turned east, it bears on the cell to its east");
        assertFalse(east.contains(5 * 10 + 4));
    }

    @Test
    void itSpendsAHeavyHullsRateCarryingTheFraction() {
        float carried = 0f;
        int spent = 0;
        for (int tick = 0; tick < 30; tick++) {
            float[] step = MechWallCrush.spend(carried, 1f / 30f);
            spent += (int) step[0];
            carried = step[1];
        }
        assertEquals(WallCrush.HEAVY_PER_SECOND, spent + carried, 1e-3f, "a second's push is a heavy hull's rate");
        assertTrue(carried < 1f);
    }
}
