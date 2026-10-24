package com.dillon.starsectormarines.battle.evacuation.flier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A Harrier's throw: rare and the same every replay, and an arc that leaves the ground and comes back to it. */
class HarrierLiftTest {

    @Test
    void theRollIsRareAndTheSameEveryReplay() {
        int lifted = 0, n = 20_000;
        for (int i = 0; i < n; i++) {
            long harrier = 100 + i % 12, body = 900 + i / 12, tick = 3L * i;
            boolean once = HarrierLift.rolls(harrier, body, tick, HarrierRules.LIFT_CHANCE);
            assertEquals(once, HarrierLift.rolls(harrier, body, tick, HarrierRules.LIFT_CHANCE));
            if (once) lifted++;
        }
        float share = (float) lifted / n;
        assertTrue(Math.abs(share - HarrierRules.LIFT_CHANCE) < 0.01f, "lifted " + share + " of strikes");
    }

    @Test
    void theArcLeavesTheGroundAndLandsWhereItWasThrown() {
        HarrierLift lift = new HarrierLift(1L, 2L, 10f, 10f, 12f, 11f);
        assertEquals(0f, lift.height(), 1e-6f);
        float peak = 0f;
        while (!lift.landed()) {
            lift.advance(1f / 30f);
            peak = Math.max(peak, lift.height());
        }
        assertEquals(HarrierRules.LIFT_PEAK_CELLS, peak, 0.02f);
        assertEquals(0f, lift.height(), 1e-6f);
        assertEquals(12f, lift.x(), 1e-5f);
        assertEquals(11f, lift.y(), 1e-5f);
    }
}
