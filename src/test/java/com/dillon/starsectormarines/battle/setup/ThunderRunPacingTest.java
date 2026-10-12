package com.dillon.starsectormarines.battle.setup;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThunderRunPacingTest {
    @Test void pressureIsFiniteAndCannotBeReplayedByRecapture() {
        var pacing = new ThunderRunPacing();
        assertEquals(44, pacing.pendingMembers());
        assertFalse(pacing.takeBatch(2, 0, 9000));
        assertFalse(pacing.takeBatch(2, 1, 749));
        assertTrue(pacing.takeBatch(2, 1, 750));
        assertFalse(pacing.takeBatch(2, 1, 9000));
        assertEquals(40, pacing.pendingMembers());
        int released = 4;
        for (int phase = 0; phase <= 3; phase++) for (int batch = 0; batch < ThunderRunPacing.BATCHES; batch++)
            if (pacing.takeBatch(batch, phase, 9000)) released += 4;
        assertEquals(44, released);
        assertEquals(0, pacing.pendingMembers());
        for (int batch = 0; batch < ThunderRunPacing.BATCHES; batch++)
            assertFalse(pacing.takeBatch(batch, 1, 9000));
    }
    @Test void dispatchWindowsAndFreightSpacingAreSeparate() {
        assertEquals(2700, ThunderRunPacing.holdTicks(1));
        assertEquals(3600, ThunderRunPacing.holdTicks(2));
        assertEquals(0, ThunderRunPacing.holdTicks(3));
        assertFalse(ThunderRunPacing.cargoDue(0, 419));
        assertTrue(ThunderRunPacing.cargoDue(0, 420));
        assertFalse(ThunderRunPacing.cargoDue(1, 1769));
        assertTrue(ThunderRunPacing.cargoDue(1, 1770));
        assertFalse(ThunderRunPacing.cargoDue(2, 3119));
        assertTrue(ThunderRunPacing.cargoDue(2, 3120));
        // A retry's first remaining load uses ordinal zero, irrespective of cargo identity.
        assertTrue(ThunderRunPacing.cargoDue(0, 420));
    }
}
