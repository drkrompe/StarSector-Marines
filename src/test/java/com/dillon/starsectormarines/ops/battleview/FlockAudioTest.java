package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlockAudioTest {

    @Test
    void hearsTheNearestFlocksInRangeAndKeepsEachOnItsSlot() {
        long[] slots = new long[FlockAudio.LOOPS];
        List<FlockAudio.Swarm> swarms = List.of(
                new FlockAudio.Swarm(0, 30f, 0f, 0f, 14),
                new FlockAudio.Swarm(1, 5f, 0f, 0f, 14),
                new FlockAudio.Swarm(2, 10f, 0f, 0f, 14),
                new FlockAudio.Swarm(3, 20f, 0f, 0f, 14),
                new FlockAudio.Swarm(4, FlockAudio.RANGE_CELLS + 1f, 0f, 0f, 14));
        List<FlockAudio.Play> heard = FlockAudio.loops(swarms, 0f, 0f, slots);
        assertEquals(FlockAudio.LOOPS, heard.size());
        assertEquals(5f, heard.get(0).x(), "nearest first");
        assertTrue(heard.get(0).volume() > heard.get(1).volume(), "each further flock quieter");
        int slotOfNear = heard.get(0).loop();

        // The flock that was nearest falls back to third: it keeps the slot it was heard on.
        List<FlockAudio.Swarm> moved = List.of(
                new FlockAudio.Swarm(1, 19f, 0f, 0f, 14),
                new FlockAudio.Swarm(2, 10f, 0f, 0f, 14),
                new FlockAudio.Swarm(3, 15f, 0f, 0f, 14));
        heard = FlockAudio.loops(moved, 0f, 0f, slots);
        assertEquals(19f, heard.get(2).x());
        assertEquals(slotOfNear, heard.get(2).loop());
    }

    @Test
    void whinesHigherFasterAndThinsAsItIsShotDown() {
        assertTrue(FlockAudio.pitch(FlockAudio.FULL_SPEED) > FlockAudio.pitch(1f));
        assertEquals(FlockAudio.PITCH_REST, FlockAudio.pitch(0f), 1e-6f);
        assertEquals(FlockAudio.PITCH_FULL, FlockAudio.pitch(FlockAudio.FULL_SPEED * 2f), 1e-6f);
        assertTrue(FlockAudio.volume(3) < FlockAudio.volume(14));
        assertEquals(FlockAudio.volume(14), FlockAudio.volume(20), 1e-6f);
    }
}
