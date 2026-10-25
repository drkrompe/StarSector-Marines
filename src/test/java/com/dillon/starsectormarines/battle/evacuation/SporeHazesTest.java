package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** When a spore haze pulses: once at each whole period of its age, however the ticks fall. */
class SporeHazesTest {

    @Test
    void itPulsesOnceAtEachWholePeriod() {
        float period = SporeHazes.PULSE_SECONDS;
        assertEquals(0, SporeHazes.pulsesDue(0f, period * 0.99f), "not before the first period");
        assertEquals(1, SporeHazes.pulsesDue(period * 0.99f, period * 1.01f));
        assertEquals(0, SporeHazes.pulsesDue(period * 1.01f, period * 1.5f), "once only");
        assertEquals(2, SporeHazes.pulsesDue(period * 0.5f, period * 2.5f), "a long tick owes each it crossed");
        assertEquals(0, SporeHazes.pulsesDue(period, period));
    }
}
