package com.dillon.starsectormarines.battle.derelict;

import org.junit.jupiter.api.Test;

import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hull's cradle and bay stand clear of its mouth, with room round them for a heavy walker. */
class FallenHullMouthTest {

    @Test
    void aStructureStandsOffTheMouthWithOpenGroundAllRoundIt() {
        // The hull's wall along y < 0; open ground above it. The mouth at (10, 0), on the wall's edge.
        BiPredicate<Integer, Integer> open = (x, y) -> y >= 0 && x >= 0 && x < 40;
        int[] at = FallenHull.clearCellNear(10, 0, FallenHull.BAY_OFF_MOUTH_CELLS, FallenHull.BAY_CLEAR_CELLS, open);
        assertTrue(Math.max(Math.abs(at[0] - 10), Math.abs(at[1])) >= FallenHull.BAY_OFF_MOUTH_CELLS, "off the mouth");
        for (int dy = -FallenHull.BAY_CLEAR_CELLS; dy <= FallenHull.BAY_CLEAR_CELLS; dy++) {
            for (int dx = -FallenHull.BAY_CLEAR_CELLS; dx <= FallenHull.BAY_CLEAR_CELLS; dx++) {
                assertTrue(open.test(at[0] + dx, at[1] + dy), "open all round it, never against the wall");
            }
        }
        // A second structure keeps clear of the first.
        BiPredicate<Integer, Integer> untaken = (x, y) -> open.test(x, y) && !(x == at[0] && y == at[1]);
        int[] second = FallenHull.clearCellNear(10, 0, FallenHull.BAY_OFF_MOUTH_CELLS, FallenHull.BAY_CLEAR_CELLS,
                untaken);
        assertTrue(Math.max(Math.abs(second[0] - at[0]), Math.abs(second[1] - at[1])) > FallenHull.BAY_CLEAR_CELLS);
        // Nowhere clear: the mouth itself.
        assertArrayEquals(new int[] {10, 0}, FallenHull.clearCellNear(10, 0, 3, 2, (x, y) -> false));
    }
}
