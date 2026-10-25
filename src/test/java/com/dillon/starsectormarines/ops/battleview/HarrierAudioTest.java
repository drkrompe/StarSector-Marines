package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarrierAudioTest {

    @Test void aWingbeatSoundsOnTheDownstrokeAndNotWhileGliding() {
        assertTrue(HarrierAudio.downstroke(0.45f, 0.55f));
        assertFalse(HarrierAudio.downstroke(0.55f, 0.65f), "already driving down");
        assertFalse(HarrierAudio.downstroke(-1f, 0.6f), "coming out of a glide is not a stroke");
        assertFalse(HarrierAudio.downstroke(-1f, -1f));
        assertFalse(HarrierAudio.downstroke(Float.NaN, 0.6f), "a flier first heard makes no beat");
    }

    @Test void aFlierScreechesOnceAsItGoesIn() {
        assertTrue(HarrierAudio.wentIn(0f, 0.1f));
        assertFalse(HarrierAudio.wentIn(0.1f, 0.2f), "still folding");
        assertFalse(HarrierAudio.wentIn(0.5f, 0.4f), "opening again on the climb");
        assertFalse(HarrierAudio.wentIn(Float.NaN, 0.1f));
    }

    @Test void aGrazeOrAKillDrawsNoPain() {
        assertTrue(HarrierAudio.hurt(28f, 20f));
        assertFalse(HarrierAudio.hurt(28f, 27.5f));
        assertFalse(HarrierAudio.hurt(10f, 0f), "the death cry covers the kill");
        assertFalse(HarrierAudio.hurt(Float.NaN, 20f));
    }

    @Test void jitterIsSeededAndSmall() {
        for (int n = 0; n < 50; n++) {
            float j = HarrierAudio.jitter(1234, n);
            assertTrue(Math.abs(j) <= HarrierAudio.PITCH_JITTER + 1e-6f);
            assertTrue(j == HarrierAudio.jitter(1234, n));
        }
    }
}
