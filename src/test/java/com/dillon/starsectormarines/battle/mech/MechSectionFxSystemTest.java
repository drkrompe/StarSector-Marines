package com.dillon.starsectormarines.battle.mech;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechSectionFxSystemTest {

    @Test
    void cadenceFiresOncePerPeriodWithAPhasePerMech() {
        int period = MechSectionFxSystem.LEG_SMOKE_PERIOD_TICKS;
        for (long id = 1; id < 40; id++) {
            int count = 0;
            for (int tick = 0; tick < period * 10; tick++) {
                if (MechSectionFxSystem.onCadence(id, tick, period, 7)) count++;
            }
            assertEquals(10, count, "mech " + id);
        }
        boolean anyPhaseDiffers = false;
        for (int tick = 0; tick < period && !anyPhaseDiffers; tick++) {
            anyPhaseDiffers = MechSectionFxSystem.onCadence(1L, tick, period, 7)
                    != MechSectionFxSystem.onCadence(2L, tick, period, 7);
        }
        assertTrue(anyPhaseDiffers, "two mechs should not puff in lockstep");
    }

    @Test
    void jitterIsRepeatableAndBounded() {
        for (int tick = 0; tick < 500; tick++) {
            float j = MechSectionFxSystem.jitter(42L, tick, 3);
            assertEquals(j, MechSectionFxSystem.jitter(42L, tick, 3));
            assertTrue(j >= -1f && j < 1f, "jitter " + j);
        }
    }

    @Test
    void shoulderAnchorsSitEitherSideAndTurnWithTheTorso() {
        assertTrue(MechSectionFxSystem.anchorX(MechSection.LEFT_SHOULDER, 0f, 2f) < 0f);
        assertTrue(MechSectionFxSystem.anchorX(MechSection.RIGHT_SHOULDER, 0f, 2f) > 0f);
        assertEquals(0f, MechSectionFxSystem.anchorX(MechSection.LEGS, 37f, 2f), 1e-6f);
        assertEquals(0f, MechSectionFxSystem.anchorY(MechSection.LEGS, 37f, 2f), 1e-6f);
        // A quarter turn carries the left shoulder from -x onto -y.
        assertEquals(0f, MechSectionFxSystem.anchorX(MechSection.LEFT_SHOULDER, 90f, 2f), 1e-5f);
        assertTrue(MechSectionFxSystem.anchorY(MechSection.LEFT_SHOULDER, 90f, 2f) < 0f);
    }
}
