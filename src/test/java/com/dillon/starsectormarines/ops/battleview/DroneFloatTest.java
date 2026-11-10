package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A drone floats: a gentle bob of its own, and a shadow that lies apart from it until a wreck comes down. */
class DroneFloatTest {

    @Test
    void aDroneRisesAndSinksWithinItsBob() {
        for (float t = 0f; t < 4f; t += .05f) {
            float bob = DroneFloat.bob(7L, t);
            assertTrue(bob >= -1f && bob <= 1f);
            float scale = DroneFloat.scale(bob);
            assertTrue(scale >= 1f - DroneFloat.BOB_SCALE - 1e-6f && scale <= 1f + DroneFloat.BOB_SCALE + 1e-6f);
        }
        assertTrue(DroneFloat.shadowCells(1f, 1f, 1f) > DroneFloat.shadowCells(1f, -1f, 1f), "higher, the shadow lies farther");
    }

    @Test
    void aSmallDronesShadowStaysCloser() {
        assertTrue(DroneFloat.shadowCells(.55f, 0f, 1f) < DroneFloat.shadowCells(1f, 0f, 1f));
    }

    @Test
    void dronesDoNotBobInStep() {
        int apart = 0;
        for (long id = 1; id <= 16; id++) {
            if (Math.abs(DroneFloat.phase(id) - DroneFloat.phase(id + 1)) > .1f) apart++;
            assertTrue(DroneFloat.phase(id) >= 0f && DroneFloat.phase(id) < 1f);
        }
        assertTrue(apart >= 12, "neighbouring ids should mostly sit well apart in their bob: " + apart);
    }

    @Test
    void aWrecksShadowClosesOnItAsItComesDown() {
        assertEquals(0f, DroneFloat.shadowCells(1f, .5f, 0f));
        assertTrue(DroneFloat.shadowCells(1f, 0f, .5f) < DroneFloat.shadowCells(1f, 0f, 1f));
    }

    @Test
    void aPinnedClockWinsOverBattleTime() {
        assertEquals(2.5f, DroneFloat.clock(2.5f, 900, 1f / 30f));
        assertEquals(30f, DroneFloat.clock(Float.NaN, 900, 1f / 30f), 1e-4f);
    }
}
