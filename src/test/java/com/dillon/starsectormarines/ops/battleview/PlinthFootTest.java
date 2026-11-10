package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Where a painted plinth's foot stands in its footprint, so its sun shadow meets the walls the player sees. */
class PlinthFootTest {

    /** The guide's foot, in from the west and north edges; cell y runs north, so the north inset is off the top. */
    @Test
    void theFootIsTheGuidesInsetFromTheWestAndNorthEdges() {
        float[] out = new float[4];
        PlinthFoot.rect(10, 20, 3, 3, out, 0);
        assertEquals(10.12f, out[0], 1e-4f);
        assertEquals(20.28f, out[1], 1e-4f);
        assertEquals(12.72f, out[2], 1e-4f);
        assertEquals(22.88f, out[3], 1e-4f);
    }

    /** A footprint the guide did not paint for scales the foot with it. */
    @Test
    void aWiderFootprintScalesTheFoot() {
        float[] out = new float[6];
        PlinthFoot.rect(0, 0, 6, 3, out, 2);
        assertEquals(0.24f, out[2], 1e-4f);
        assertEquals(5.44f, out[4], 1e-4f);
        assertEquals(0.28f, out[3], 1e-4f);
    }
}
