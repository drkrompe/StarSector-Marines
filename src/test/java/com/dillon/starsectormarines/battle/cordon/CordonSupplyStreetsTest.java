package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** A city's supply way is the truck's own route, cell by cell, and a truck sets out from its first street cell. */
class CordonSupplyStreetsTest {

    @Test
    void aRoutesCornersBecomeAnUnbrokenRunOfCells() {
        List<int[]> way = CordonSupplyStreets.cells(new float[]{0.5f, 4.5f, 4.5f}, new float[]{0.5f, 0.5f, 3.5f});
        assertEquals(8, way.size());
        assertArrayEquals(new int[]{0, 0}, way.get(0));
        assertArrayEquals(new int[]{4, 0}, way.get(4));
        assertArrayEquals(new int[]{4, 3}, way.get(7));
        for (int i = 1; i < way.size(); i++) {
            int step = Math.max(Math.abs(way.get(i)[0] - way.get(i - 1)[0]), Math.abs(way.get(i)[1] - way.get(i - 1)[1]));
            assertEquals(1, step, "each cell a step from the last");
        }
    }

    @Test
    void aTruckTriesTheWaysStreetCellsFromTheBaseEndThenTheDoor() {
        List<int[]> way = List.of(new int[]{0, 0}, new int[]{0, 1}, new int[]{0, 2}, new int[]{0, 3}, new int[]{0, 4},
                new int[]{0, 5}, new int[]{0, 6});
        List<int[]> starts = CordonSupplyStreets.starts(way, (x, y) -> y >= 2, 2, 3);
        assertEquals(3, starts.size());
        assertArrayEquals(new int[]{0, 2}, starts.get(0));
        assertArrayEquals(new int[]{0, 5}, starts.get(1));
        assertArrayEquals(new int[]{0, 0}, starts.get(2), "the door last");
        assertEquals(1, CordonSupplyStreets.starts(way, (x, y) -> false, 2, 3).size(), "no street: the door alone");
    }
}
