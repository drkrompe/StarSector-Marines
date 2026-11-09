package com.dillon.starsectormarines.battle.drone;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlockSystemTest {

    /** A 20x10 field; a roofed block x 10..17, y 2..8 with a 2x2 light well at x 13..14, y 4..5 open to the sky. */
    private static boolean open(int x, int y) {
        if (x < 0 || y < 0 || x >= 20 || y >= 10) return false;
        boolean block = x >= 10 && x <= 17 && y >= 2 && y <= 8;
        boolean well = x >= 13 && x <= 14 && y >= 4 && y <= 5;
        return !block || well;
    }

    @Test
    void aLightWellInsideIsNotTheOutside() {
        boolean[] outside = FlockSystem.outside(20, 10, FlockSystemTest::open);
        assertTrue(outside[5 * 20 + 2], "the open field is the outside");
        assertTrue(outside[9 * 20 + 15], "the street past the block is the outside");
        assertFalse(outside[4 * 20 + 13], "the light well is open sky but not the outside");
    }

    @Test
    void launchCellsComeFromTheOpenGroundBeyondACrampedHome() {
        // Home at the mouth of a one-cell alley (x 0, y 0..4) that opens onto a yard at y 5.
        FlockField.Open alley = (x, y) -> (x == 0 && y >= 0 && y <= 4) || (x >= 0 && x <= 5 && y >= 5 && y <= 8);
        List<int[]> cells = FlockSystem.openAround(0, 0, alley, 14);
        assertEquals(14, cells.size(), "the yard beyond the alley gives the rest of the cells");
        assertEquals(0, cells.get(0)[0]);
        assertEquals(0, cells.get(0)[1]);
    }
}
