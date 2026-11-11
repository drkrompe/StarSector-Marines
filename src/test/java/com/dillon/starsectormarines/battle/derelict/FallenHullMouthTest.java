package com.dillon.starsectormarines.battle.derelict;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void everySectionBeyondTheMouthsOpensAHatchClearOfEverySection() {
        // The mouth's section at x 0..9; two more at x 40..49 and x 80..89; open ground everywhere else.
        List<FallenHullSite.Rect> sections = List.of(new FallenHullSite.Rect(0, 0, 9, 9),
                new FallenHullSite.Rect(40, 0, 49, 9), new FallenHullSite.Rect(80, 0, 89, 9));
        List<int[]> hatches = FallenHull.sectionHatches(sections, 5, 10, (x, y) -> true);
        assertEquals(2, hatches.size(), "one for each section but the mouth's");
        for (int[] h : hatches) {
            for (FallenHullSite.Rect r : sections) {
                for (int dy = -FallenHull.BAY_CLEAR_CELLS; dy <= FallenHull.BAY_CLEAR_CELLS; dy++) {
                    for (int dx = -FallenHull.BAY_CLEAR_CELLS; dx <= FallenHull.BAY_CLEAR_CELLS; dx++) {
                        assertFalse(r.contains(h[0] + dx, h[1] + dy), "clear of every section");
                    }
                }
            }
        }
        assertTrue(Math.abs(hatches.get(0)[0] - 45) < 20 && Math.abs(hatches.get(1)[0] - 85) < 20, "by its own section");
        assertTrue(FallenHull.sectionHatches(sections, 5, 10, (x, y) -> false).isEmpty(), "nowhere open: none");
    }
}
