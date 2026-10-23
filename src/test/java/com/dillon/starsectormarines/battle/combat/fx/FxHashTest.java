package com.dillon.starsectormarines.battle.combat.fx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxHashTest {

    @Test
    void cadenceFiresOncePerPeriodWithAPhasePerBody() {
        int period = 16;
        for (long id = 1; id < 40; id++) {
            int count = 0;
            for (int tick = 0; tick < period * 10; tick++) {
                if (FxHash.onCadence(id, tick, period, 7)) count++;
            }
            assertEquals(10, count, "body " + id);
        }
        boolean anyPhaseDiffers = false;
        for (int tick = 0; tick < period && !anyPhaseDiffers; tick++) {
            anyPhaseDiffers = FxHash.onCadence(1L, tick, period, 7)
                    != FxHash.onCadence(2L, tick, period, 7);
        }
        assertTrue(anyPhaseDiffers, "two bodies should not puff in lockstep");
    }

    @Test
    void jitterIsRepeatableAndBounded() {
        for (int tick = 0; tick < 500; tick++) {
            float j = FxHash.jitter(42L, tick, 3);
            assertEquals(j, FxHash.jitter(42L, tick, 3));
            assertTrue(j >= -1f && j < 1f, "jitter " + j);
        }
    }
}
