package com.dillon.starsectormarines.battle.world.gen.fortress;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeltStandCellsTest {

    private static Set<Long> row(int x0, int x1, int y) {
        Set<Long> cells = new HashSet<>();
        for (int x = x0; x <= x1; x++) cells.add(BeltStandCells.key(x, y));
        return cells;
    }

    @Test
    void takesTheRunNearestTheNestFirst() {
        Set<Long> trench = row(0, 30, 10);
        List<int[]> cells = BeltStandCells.near(trench, 15, 8, 5, 20);
        assertEquals(5, cells.size());
        assertEquals(15, cells.get(0)[0]);
        assertEquals(10, cells.get(0)[1]);
        for (int[] c : cells) assertTrue(Math.abs(c[0] - 15) <= 2, "run grows outward from the nearest cell");
    }

    @Test
    void doesNotJumpAGapInTheBelt() {
        Set<Long> trench = row(0, 5, 10);
        trench.addAll(row(8, 13, 10));
        List<int[]> cells = BeltStandCells.near(trench, 3, 9, 12, 20);
        assertEquals(6, cells.size(), "the second run is not connected to the first");
        for (int[] c : cells) assertTrue(c[0] <= 5);
    }

    @Test
    void reachBoundsTheRun() {
        Set<Long> trench = row(0, 40, 10);
        List<int[]> cells = BeltStandCells.near(trench, 20, 10, 12, 3);
        assertEquals(7, cells.size());
        assertTrue(BeltStandCells.near(trench, 20, 30, 12, 3).isEmpty(), "no belt within reach of the nest");
    }

    @Test
    void sameCellsGiveSameOrder() {
        Set<Long> trench = row(0, 12, 4);
        trench.addAll(row(0, 12, 5));
        List<int[]> a = BeltStandCells.near(trench, 6, 4, 9, 10);
        List<int[]> b = BeltStandCells.near(new HashSet<>(trench), 6, 4, 9, 10);
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i)[0], b.get(i)[0]);
            assertEquals(a.get(i)[1], b.get(i)[1]);
        }
    }

    @Test
    void emptyGroundOrNoCountGivesNothing() {
        assertTrue(BeltStandCells.near(new HashSet<>(), 0, 0, 5, 9).isEmpty());
        assertTrue(BeltStandCells.near(row(0, 4, 0), 0, 0, 0, 9).isEmpty());
    }
}
