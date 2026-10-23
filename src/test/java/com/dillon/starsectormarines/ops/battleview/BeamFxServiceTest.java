package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The knots a textured beam carries: bounded, fixed per knot, and capped however long the beam. */
class BeamFxServiceTest {

    @Test
    void aKnotsBrightnessIsBoundedAndItsOwn() {
        for (int k = -50; k < 50; k++) {
            float w = BeamFxService.knotWeight(k, 0.6f);
            assertTrue(w >= 0f && w <= 0.6f + 1e-6f, "knot " + k + " weight " + w);
            assertEquals(w, BeamFxService.knotWeight(k, 0.6f));
        }
        assertEquals(0f, BeamFxService.knotWeight(7, 0f));
    }

    @Test
    void knotsAreOnePerTexelAndCapped() {
        assertEquals(1, BeamFxService.knotCount(0f, 0.4f));
        assertEquals(6, BeamFxService.knotCount(2f, 0.4f));
        assertEquals(BeamFxService.MAX_KNOTS, BeamFxService.knotCount(500f, 0.4f));
    }

    @Test
    void anUnthrobbedBeamHoldsSteady() {
        assertEquals(1f, BeamFxService.throb(0f, 1.234f));
        float t = BeamFxService.throb(6f, 0.1f);
        assertTrue(t >= 0f && t <= 1f);
    }
}
