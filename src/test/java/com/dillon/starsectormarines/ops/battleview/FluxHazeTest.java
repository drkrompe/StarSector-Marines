package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FluxHazeTest {

    @Test
    void hazeThickensWithTheSquareOfFluxAndVentingDrawsFull() {
        assertEquals(0f, FluxHaze.strength(0f, false));
        assertEquals(0f, FluxHaze.strength(FluxHaze.THRESHOLD, false));
        assertEquals(0.25f, FluxHaze.strength(0.5f, false), 1e-6f);
        assertEquals(1f, FluxHaze.strength(1f, false), 1e-6f);
        // A vent plume is strong from the moment venting begins, and still showing as the flux runs down.
        assertTrue(FluxHaze.strength(0.95f, true) > FluxHaze.strength(0.95f, false));
        assertTrue(FluxHaze.strength(0.55f, true) > 0.55f);
    }
}
