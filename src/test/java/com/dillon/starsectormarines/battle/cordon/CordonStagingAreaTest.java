package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The staging area is the open ground just inside the loading gate, kept inside the compound. */
class CordonStagingAreaTest {

    @Test
    void itRunsFromJustInsideTheGateAndStaysOnTheGroundItReaches() {
        // A compound whose inside runs 10..40 by 10..40, gate below its bottom row at (25, 41), a wall across row 34.
        CordonStagingArea area = CordonStagingArea.of((x, y) -> y != 34, 10, 10, 40, 40, 25, 41);
        assertTrue(area.contains(25, 40), "the cell just inside the gate");
        assertTrue(area.contains(25 + CordonStagingArea.REACH, 35));
        assertFalse(area.contains(25 + CordonStagingArea.REACH + 1, 40));
        assertFalse(area.contains(25, 33), "not past the wall across the yard");
        assertFalse(area.contains(25, 41), "never outside the compound");
        assertEquals((2 * CordonStagingArea.REACH + 1) * 6, area.cells().size());
        assertTrue(area.contains(area.centreX(), area.centreY()));
        assertTrue(area.near(25, 33, 2) && !area.near(25, 33, 1));
    }

    @Test
    void aCompoundWithNothingOpenHasNoStagingArea() {
        assertTrue(CordonStagingArea.of((x, y) -> false, 0, 0, 5, 5, 3, 6).isEmpty());
    }
}
