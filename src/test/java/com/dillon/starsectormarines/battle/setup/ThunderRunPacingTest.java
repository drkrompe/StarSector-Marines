package com.dillon.starsectormarines.battle.setup;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThunderRunPacingTest {
    @Test void pressureIsFiniteAndCannotBeReplayedByRecapture() {
        var pacing = new ThunderRunPacing();
        assertEquals(68, pacing.pendingMembers());
        int released = 0, lastRelease = -360;
        for (int tick = 0; tick < 15000; tick++) {
            int phase = tick < 300 ? 0 : tick < 3000 ? 1 : tick < 6600 ? 2 : 3;
            for (int batch = 0; batch < ThunderRunPacing.BATCHES; batch++) {
                if (!pacing.takeBatch(batch, phase, tick)) continue;
                assertTrue(tick - lastRelease >= 360, "No transition burst of overdue squads");
                lastRelease = tick;
                released += 4;
            }
        }
        assertEquals(68, released);
        assertEquals(0, pacing.pendingMembers());
        for (int batch = 0; batch < ThunderRunPacing.BATCHES; batch++)
            assertFalse(pacing.takeBatch(batch, 1, 18000));
    }
    @Test void trailingPressureKeepsItsOriginalClockAfterTheNextCapture() {
        var pacing = new ThunderRunPacing();
        assertTrue(pacing.takeBatch(1, 1, 300));
        assertFalse(pacing.takeBatch(5, 2, 3000));
        assertFalse(pacing.takeBatch(5, 2, 3779));
        assertTrue(pacing.takeBatch(5, 2, 3780));
        assertFalse(pacing.takeBatch(5, 1, 9000));
    }
    @Test void dispatchWindowsAndFreightSpacingAreSeparate() {
        assertEquals(2700, ThunderRunPacing.holdTicks(1));
        assertEquals(3600, ThunderRunPacing.holdTicks(2));
        assertEquals(0, ThunderRunPacing.holdTicks(3));
        assertFalse(ThunderRunPacing.cargoDue(0, 419));
        assertTrue(ThunderRunPacing.cargoDue(0, 420));
        assertFalse(ThunderRunPacing.cargoDue(1, 1079));
        assertTrue(ThunderRunPacing.cargoDue(1, 1080));
        assertFalse(ThunderRunPacing.cargoDue(2, 1739));
        assertTrue(ThunderRunPacing.cargoDue(2, 1740));
        // A retry's first remaining load uses ordinal zero, irrespective of cargo identity.
        assertTrue(ThunderRunPacing.cargoDue(0, 420));
    }
}
