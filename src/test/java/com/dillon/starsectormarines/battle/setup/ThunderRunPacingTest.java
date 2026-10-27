package com.dillon.starsectormarines.battle.setup;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThunderRunPacingTest {
    @Test void pressureIsFiniteAndCannotBeReplayedByRecapture() {
        var pacing = new ThunderRunPacing();
        assertEquals(ThunderRunPacing.RESERVE_SIZE, pacing.pendingMembers());
        int released = 0, lastRelease = -360;
        for (int tick = 0; tick < 15000; tick++) {
            int phase = tick < 300 ? 0 : tick < 3000 ? 1 : tick < 6600 ? 2 : 3;
            for (int batch = 0; batch < ThunderRunPacing.BATCHES; batch++) {
                if (!pacing.takeBatch(batch, phase, tick)) continue;
                assertTrue(tick - lastRelease >= 360, "No transition burst of overdue squads");
                lastRelease = tick;
                released += ThunderRunPacing.RAIDERS_PER_BATCH;
            }
        }
        assertEquals(ThunderRunPacing.RESERVE_SIZE, released);
        assertEquals(0, pacing.pendingMembers());
        for (int batch = 0; batch < ThunderRunPacing.BATCHES; batch++)
            assertFalse(pacing.takeBatch(batch, 1, 18000));
    }
    @Test void trailingPressureKeepsItsOriginalClockAfterTheNextCapture() {
        var pacing = new ThunderRunPacing();
        assertTrue(pacing.takeBatch(1, 1, 300));
        // Dispatch's last batch is due 64s into its phase even after the Crossing falls.
        assertFalse(pacing.takeBatch(4, 2, 2000));
        assertFalse(pacing.takeBatch(4, 2, 300 + 64 * 30 - 1));
        assertTrue(pacing.takeBatch(4, 2, 300 + 64 * 30));
        assertFalse(pacing.takeBatch(4, 1, 9000));
    }
    @Test void rearAttacksStartAfterCrossingHoldAndAlternatePosts() {
        var pacing = new ThunderRunPacing();
        for (int batch = 0; batch < ThunderRunPacing.BATCHES; batch++) pacing.takeBatch(batch, 2, 0);
        int first = ThunderRunPacing.FIRST_REAR_BATCH;
        for (int batch = first; batch < ThunderRunPacing.BATCHES; batch++) {
            assertFalse(pacing.takeBatch(batch, 2, ThunderRunPacing.holdTicks(2)));
            assertEquals((batch - first) % 2, ThunderRunPacing.rearPost(batch));
            assertFalse(ThunderRunPacing.usesOutboundRoad(batch));
        }
        assertEquals(-1, ThunderRunPacing.rearPost(first - 1));
        assertTrue(pacing.takeBatch(first, 2, 84 * 30));
        assertTrue(pacing.takeBatch(first + 1, 3, 108 * 30));
        assertFalse(pacing.takeBatch(first, 2, 300 * 30));
    }
    @Test void finalRunInterdictionComesByTheOutboundRoad() {
        for (int batch = 0; batch < ThunderRunPacing.BATCHES; batch++)
            if (ThunderRunPacing.batchPhase(batch) == 3) assertTrue(ThunderRunPacing.usesOutboundRoad(batch));
    }
    @Test void dispatchWindowsEndAtTheFinalRun() {
        assertEquals(1800, ThunderRunPacing.holdTicks(1));
        assertEquals(2400, ThunderRunPacing.holdTicks(2));
        assertEquals(0, ThunderRunPacing.holdTicks(3));
    }
}
