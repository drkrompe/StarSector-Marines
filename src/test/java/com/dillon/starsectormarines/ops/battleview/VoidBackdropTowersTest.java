package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How the void backdrop splits a surface into the towers it stands on and the walkways between them. */
class VoidBackdropTowersTest {

    static final int MW = 40, MH = 20;

    @Test
    void groundNarrowerThanASpanIsWalkwayAndWiderStandsOnATower() {
        boolean[] surface = new boolean[MW * MH];
        fill(surface, 2, 2, 12, 12);    // a tower roof
        fill(surface, 12, 6, 22, 8);    // a two-cell catwalk off it
        fill(surface, 22, 2, 29, 9);    // a roof just a span wide
        boolean[] towers = VoidBackdropRenderSystem.towers(surface, MW, MH);
        assertTrue(towers[at(2, 2)], "a roof's corner stands on its tower");
        assertTrue(towers[at(11, 11)]);
        assertFalse(towers[at(16, 7)], "a catwalk is a walkway");
        assertTrue(towers[at(25, 5)], "ground a span wide stands on a tower");
        assertFalse(towers[at(30, 5)], "nothing off the surface is a tower");
    }

    private static void fill(boolean[] mask, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) mask[at(x, y)] = true;
    }

    private static int at(int x, int y) {
        return y * MW + x;
    }
}
