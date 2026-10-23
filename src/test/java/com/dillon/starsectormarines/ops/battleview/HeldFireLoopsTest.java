package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeldFireLoopsTest {

    @Test
    void aLanceStartsOnceAndHoldsWhilePulsesArrive() {
        HeldFireLoops loops = new HeldFireLoops();
        assertTrue(loops.arm(7L, "pd_laser_loop", 1f, 0f, 0f), "first pulse starts the lance");
        for (int pulse = 0; pulse < 9; pulse++) {
            loops.advance(0.1f);
            assertFalse(loops.arm(7L, "pd_laser_loop", 1f, 0f, 0f), "later pulses keep the loop");
        }
        assertEquals(1, loops.held().size());
    }

    @Test
    void theLoopLapsesWithoutAPulseAndTheNextPulseStartsAgain() {
        HeldFireLoops loops = new HeldFireLoops();
        loops.arm(7L, "pd_laser_loop", 1f, 0f, 0f);
        loops.advance(HeldFireLoops.HOLD_SECONDS * 0.9f);
        assertTrue(loops.isHeld(7L));
        loops.advance(HeldFireLoops.HOLD_SECONDS * 0.2f);
        assertFalse(loops.isHeld(7L));
        assertTrue(loops.arm(7L, "pd_laser_loop", 1f, 0f, 0f), "a pulse after the cooldown is a new lance");
    }

    @Test
    void eachShooterHoldsItsOwnVoice() {
        HeldFireLoops loops = new HeldFireLoops();
        assertTrue(loops.arm(1L, "pd_laser_loop", 1f, 0f, 0f));
        assertTrue(loops.arm(2L, "pd_laser_loop", 1f, 5f, 5f));
        assertEquals(2, loops.held().size());
    }
}
