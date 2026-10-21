package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MotionPhaseTest {

    @Test
    void aStoppedMachineKeepsItsPhaseAndRunsDownRatherThanSnapping() {
        MotionPhase phase = new MotionPhase(0f);
        phase.advance(0f, 1f, 1f);
        phase.advance(0.2f, 1f, 1f);
        assertEquals(0.2f, phase.phase(), 1e-5f, "started at speed on first sight");
        phase.advance(0.4f, 0f, 1f);
        assertEquals(0.8f, phase.rate(), 1e-5f, "runs down over its ease time");
        for (float t = 0.6f; t < 2f; t += 0.2f) phase.advance(t, 0f, 1f);
        float held = phase.phase();
        phase.advance(5f, 0f, 1f);
        assertEquals(held, phase.phase(), 1e-6f, "stopped where it stood");
    }

    @Test
    void aLongGapAdvancesOneClampedStepAndASecondPassAdvancesNothing() {
        MotionPhase phase = new MotionPhase(0f);
        phase.advance(0f, 1f, 0f);
        phase.advance(60f, 1f, 0f);
        assertEquals(0.25f, phase.phase(), 1e-6f);
        phase.advance(60f, 1f, 0f);
        assertEquals(0.25f, phase.phase(), 1e-6f);
    }
}
