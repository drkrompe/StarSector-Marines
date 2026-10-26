package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.evacuation.SporeHazes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BombardAudioTest {

    @Test
    void eachCreakIsHeardOnceAsTheWindUpCrossesIt() {
        assertEquals(0, BombardAudio.creak(-1f, BombardAudio.CREAKS[0]));
        assertEquals(-1, BombardAudio.creak(BombardAudio.CREAKS[0], BombardAudio.CREAKS[0] + 0.01f));
        assertEquals(1, BombardAudio.creak(0.2f, 0.35f));
        // a slow frame that skips past two is heard as the later
        assertEquals(3, BombardAudio.creak(0.5f, 0.9f));
        assertEquals(-1, BombardAudio.creak(0.9f, 1f));
    }

    @Test
    void aHazeSwellsAtEachStingAndSettlesBetween() {
        float pulse = SporeHazes.PULSE_SECONDS;
        assertEquals(1f, BombardAudio.swell(0.5f), 1e-6f, "the burst has its own sound");
        assertEquals(1f + BombardAudio.STING_SWELL, BombardAudio.swell(pulse), 1e-4f);
        assertTrue(BombardAudio.swell(pulse + 0.1f) > BombardAudio.swell(pulse + 1f));
        assertTrue(BombardAudio.swell(2 * pulse - 0.01f) < 1.02f);
        assertEquals(1f + BombardAudio.STING_SWELL, BombardAudio.swell(3 * pulse), 1e-4f);
    }

    @Test
    void theNearestHazesInRangeHoldTheLoopsAndThinOnesAreQuieter() {
        List<BombardAudio.Haze> hazes = List.of(
                new BombardAudio.Haze(1L, 10f, 0f, 1f, 1f),
                new BombardAudio.Haze(2L, 2f, 0f, 1f, 0.5f),
                new BombardAudio.Haze(3L, 5f, 0f, 1f, 1f),
                new BombardAudio.Haze(4L, BombardAudio.HAZE_RANGE_CELLS + 1f, 0f, 1f, 1f));
        List<BombardAudio.Play> loops = BombardAudio.hazeLoops(hazes, 0f, 0f, new long[BombardAudio.HAZE_LOOPS]);
        assertEquals(BombardAudio.HAZE_LOOPS, loops.size());
        assertEquals(2f, loops.get(0).x());
        assertEquals(5f, loops.get(1).x());
        assertEquals(BombardAudio.Sound.HAZE.volume * 0.5f, loops.get(0).volume(), 1e-6f);
        assertEquals(BombardAudio.Sound.HAZE.volume * VoicePool.layerGain(2), loops.get(1).volume(), 1e-6f);
        assertTrue(BombardAudio.hazeLoops(List.of(hazes.get(3)), 0f, 0f, new long[BombardAudio.HAZE_LOOPS]).isEmpty());
    }

    @Test
    void aHazeKeepsItsLoopWhenANearerOneBursts() {
        long[] slots = new long[BombardAudio.HAZE_LOOPS];
        BombardAudio.Haze first = new BombardAudio.Haze(1L, 8f, 0f, 1f, 1f);
        assertEquals(0, BombardAudio.hazeLoops(List.of(first), 0f, 0f, slots).get(0).loop());
        BombardAudio.Haze nearer = new BombardAudio.Haze(2L, 1f, 0f, 0f, 1f);
        List<BombardAudio.Play> loops = BombardAudio.hazeLoops(List.of(first, nearer), 0f, 0f, slots);
        assertEquals(1f, loops.get(0).x());
        assertEquals(1, loops.get(0).loop());
        assertEquals(0, loops.get(1).loop());
        // once the first has gone its slot is free again
        assertEquals(1, BombardAudio.hazeLoops(List.of(nearer), 0f, 0f, slots).get(0).loop());
        assertEquals(0, BombardAudio.hazeLoops(List.of(nearer, new BombardAudio.Haze(3L, 2f, 0f, 0f, 1f)), 0f, 0f,
                slots).get(1).loop());
    }
}
