package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test void aPackLayersItsVoicesUpToTheSoundsVoiceCount() {
        HarrierAudio.Mix mix = new HarrierAudio.Mix();
        HarrierAudio.Sound s = HarrierAudio.Sound.SCREECH;
        for (int i = 0; i < s.voices; i++) {
            assertEquals(HarrierAudio.Admit.START, mix.admit(s, 100 + i, i), "flier " + i + " layers on");
            assertEquals(i + 1, mix.sounding(s));
        }
        assertEquals(HarrierAudio.Admit.DROP, mix.admit(s, 200, s.voices), "every voice is sounding");
        int later = s.voices + Math.round(s.voiceSeconds / BattleSimulation.TICK_DT) + 1;
        assertEquals(HarrierAudio.Admit.START, mix.admit(s, 200, later), "a voice has finished");
    }

    @Test void twoVoicesNeverStartOnOneTick() {
        HarrierAudio.Mix mix = new HarrierAudio.Mix();
        HarrierAudio.Sound s = HarrierAudio.Sound.DEATH;
        assertEquals(HarrierAudio.Admit.START, mix.admit(s, 1, 10));
        assertEquals(HarrierAudio.Admit.HOLD, mix.admit(s, 2, 10), "held for the next tick, not dropped");
        assertEquals(HarrierAudio.Admit.START, mix.admit(s, 2, 11));
    }

    @Test void oneFlierDoesNotRepeatInsideItsGap() {
        HarrierAudio.Mix mix = new HarrierAudio.Mix();
        HarrierAudio.Sound s = HarrierAudio.Sound.PAIN;
        int gap = Math.round(s.flierGapSeconds / BattleSimulation.TICK_DT);
        assertEquals(HarrierAudio.Admit.START, mix.admit(s, 7, 0));
        assertEquals(HarrierAudio.Admit.DROP, mix.admit(s, 7, gap - 1));
        assertEquals(HarrierAudio.Admit.START, mix.admit(s, 8, gap - 1), "another flier is heard");
        assertEquals(HarrierAudio.Admit.START, mix.admit(s, 7, gap + 1));
    }

    @Test void layeredVoicesGrowSlowerThanTheirCount() {
        assertEquals(1f, HarrierAudio.layerGain(1));
        for (int n = 2; n <= 8; n++) {
            float g = HarrierAudio.layerGain(n);
            assertTrue(g < HarrierAudio.layerGain(n - 1), "each layer quieter than the last");
            float power = n * g * g;
            assertTrue(power > (n - 1) * HarrierAudio.layerGain(n - 1) * HarrierAudio.layerGain(n - 1),
                    "a bigger pack is still louder");
            assertTrue(power < n, "but far less than n voices at full level");
        }
    }

    @Test void jitterIsSeededAndSmall() {
        for (int n = 0; n < 50; n++) {
            float j = HarrierAudio.jitter(1234, n);
            assertTrue(Math.abs(j) <= HarrierAudio.PITCH_JITTER + 1e-6f);
            assertTrue(j == HarrierAudio.jitter(1234, n));
        }
    }
}
