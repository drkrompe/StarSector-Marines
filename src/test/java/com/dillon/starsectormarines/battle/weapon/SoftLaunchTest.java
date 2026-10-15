package com.dillon.starsectormarines.battle.weapon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A two-stage missile coasts out of its tube, then flies the rest under power, with no jump between the two. */
class SoftLaunchTest {

    private static final SoftLaunch LAUNCH = new SoftLaunch(0.25f, 0.6f, 0.9f);
    private static final float FLIGHT = 0.5f;
    private static final float DISTANCE = 12f;

    @Test
    void theMotorLightsWhenTheCoastIsOver() {
        assertEquals(0.5f, LAUNCH.coastFraction(FLIGHT), 1e-6f);
        assertFalse(LAUNCH.ignited(0.49f, FLIGHT));
        assertTrue(LAUNCH.ignited(0.5f, FLIGHT));
    }

    @Test
    void theCoastCoversOnlyThePopAndTheRunCoversTheRest() {
        assertEquals(0f, LAUNCH.progress(0f, FLIGHT, DISTANCE, true));
        assertEquals(0.6f / DISTANCE, LAUNCH.progress(0.5f, FLIGHT, DISTANCE, true), 1e-5f);
        assertEquals(1f, LAUNCH.progress(1f, FLIGHT, DISTANCE, true));
        float previous = 0f;
        for (int i = 1; i <= 100; i++) {
            float progress = LAUNCH.progress(i / 100f, FLIGHT, DISTANCE, true);
            assertTrue(progress >= previous, "the missile never flies backwards");
            assertTrue(progress - previous < 0.05f, "no jump at ignition");
            previous = progress;
        }
    }

    @Test
    void itClimbsThroughTheCoastAndLevelsOffOnArrival() {
        assertEquals(0f, LAUNCH.lift(0f, FLIGHT));
        assertEquals(0.9f, LAUNCH.lift(0.5f, FLIGHT), 1e-5f);
        assertTrue(LAUNCH.lift(0.75f, FLIGHT) < 0.9f);
        assertEquals(0f, LAUNCH.lift(1f, FLIGHT));
    }
}
