package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreepAudioTest {

    @Test
    void silentWithNoSeenCreepInReach() {
        assertNull(CreepAudio.bed((x, y) -> x > 100, 50f, 50f));
    }

    @Test
    void anchorsOnTheNearestCreepAndSwellsWithItsShare() {
        // A strip of creep from x = 56 eastward: the nearest searched cell is on the camera's row.
        CreepAudio.Bed edge = CreepAudio.bed((x, y) -> x >= 56, 50f, 50f);
        assertEquals(56, edge.x());
        assertEquals(50, edge.y());
        CreepAudio.Bed drowned = CreepAudio.bed((x, y) -> true, 50f, 50f);
        assertEquals(50, drowned.x());
        assertEquals(1f, drowned.loudness(), 1e-6);
        assertTrue(edge.loudness() < drowned.loudness());
    }
}
