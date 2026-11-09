package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.appearance.DerelictLegRig;
import com.dillon.starsectormarines.battle.sim.SeparationSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalkerFootprintTest {

    @Test
    void aPersonFitsUnderTheTorsoAMachineNever() {
        assertTrue(WalkerFootprint.fitsUnder(0.3f, false));
        assertFalse(WalkerFootprint.fitsUnder(0.3f, true));
        assertFalse(WalkerFootprint.fitsUnder(0.8f, false), "a big body does not fit");
    }

    @Test
    void itsFeetStandOnTheHipBearingsLeavingTheMiddleOpen() {
        float[] feet = new float[8];
        assertEquals(4, WalkerFootprint.feet(10f, 10f, 0f, feet));
        for (int i = 0; i < 4; i++) {
            float d = (float) Math.hypot(feet[2 * i] - 10f, feet[2 * i + 1] - 10f);
            assertEquals(DerelictLegRig.REST_CELLS, d, 1e-4f);
            assertTrue(d - WalkerFootprint.FOOT_RADIUS > 1f, "the space under the torso is clear of every foot");
        }
        assertTrue(feet[1] > 10f && feet[0] < 10f, "leg 0 stands front left: ahead (+y) and to the left (-x)");
        assertTrue(feet[7] > 10f && feet[6] > 10f, "leg 3 stands front right");
        assertTrue(WalkerFootprint.under(0.5f, -0.5f));
        assertFalse(WalkerFootprint.under(2f, 0f));
    }

    @Test
    void aBodyUnderItIsKeptOffTheNearestFoot() {
        float[] feet = new float[8];
        WalkerFootprint.feet(0f, 0f, 0f, feet);
        int foot = SeparationSystem.nearestFoot(feet, 4, -1.5f, 1.5f);
        assertEquals(0, foot, "front left");
    }
}
