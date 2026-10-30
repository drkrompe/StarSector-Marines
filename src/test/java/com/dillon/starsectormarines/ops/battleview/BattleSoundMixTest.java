package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BattleSoundMixTest {

    @Test
    void pitchMovesSpeedTooAndAStartPastTheEndIsDropped() {
        float[] clip = {0f, 1f, 2f, 3f, 4f};
        float[] left = new float[6];
        float[] right = new float[6];
        // Twice the pitch reads every other sample, so the clip lasts half as long; it starts at sample 1.
        BattleSoundMix.add(clip, 2f, 1, 1f, 0.5f, left, right);
        assertArrayEquals(new float[] {0f, 0f, 2f, 4f, 0f, 0f}, left);
        assertArrayEquals(new float[] {0f, 0f, 1f, 2f, 0f, 0f}, right);
        // Half the pitch interpolates between samples.
        float[] slow = new float[4];
        BattleSoundMix.add(new float[] {0f, 2f}, 0.5f, 0, 1f, 1f, slow, new float[4]);
        assertArrayEquals(new float[] {0f, 1f, 2f, 0f}, slow);
        assertFalse(BattleSoundMix.add(clip, 1f, 6, 1f, 1f, left, right));
    }

    @Test
    void aMusicTrackFadesInAndFadesOutFromTheNextCue() {
        float[] clip = new float[10];
        Arrays.fill(clip, 1f);
        float[] left = new float[12];
        // Unit gain at the centre; in over two samples from sample 1, out over two from sample 6.
        BattleSoundMix.music(clip, (float) Math.sqrt(2.0), 1, 2, 6, 2, false, left, new float[12]);
        assertArrayEquals(new float[] {0f, 0f, .5f, 1f, 1f, 1f, 1f, .5f, 0f, 0f, 0f, 0f}, left, 1e-5f);
        // A looped track wraps round under its run instead of ending.
        float[] looped = new float[6];
        BattleSoundMix.music(new float[] {1f, 2f}, (float) Math.sqrt(2.0), 0, 0, 6, 0, true, looped, new float[6]);
        assertArrayEquals(new float[] {1f, 2f, 1f, 2f, 1f, 2f}, looped, 1e-5f);
    }

    @Test
    void aHeldLoopWrapsRoundUnderItsRunAtEachTicksLevelAndStopsWithIt() {
        float[] left = new float[8];
        float[] right = new float[8];
        // Three ticks of two samples from sample 1: the two-sample clip wraps, not restarts, at each tick's gain.
        BattleSoundMix.hold(new float[] {1f, 2f}, 1f, 1, 2, new float[] {1f, 0.5f, 1f},
                new float[] {0f, 0f, 2f}, 0, left, right);
        assertArrayEquals(new float[] {0f, 1f, 2f, 0.5f, 1f, 1f, 2f, 0f}, left);
        assertArrayEquals(new float[] {0f, 0f, 0f, 0f, 0f, 2f, 4f, 0f}, right);
        // Faded, it rises from silence and dies back to it at the run's ends.
        float[] faded = new float[6];
        BattleSoundMix.hold(new float[] {1f}, 1f, 0, 2, new float[] {1f, 1f, 1f}, new float[3], 2, faded, new float[6]);
        assertArrayEquals(new float[] {0f, 0.5f, 1f, 1f, 0.5f, 0f}, faded);
    }

    @Test
    void aHeldLoopReadsOnAtEachTicksPitchAndTakesItsOwnFades() {
        float[] clip = {0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f};
        float[] left = new float[6];
        // Two ticks of three samples: pitch 1 then 2, read on from where the first tick left off.
        BattleSoundMix.hold(clip, new float[] {1f, 2f}, 0, 3, new float[] {1f, 1f}, new float[2], 0, 0,
                left, new float[6]);
        assertArrayEquals(new float[] {0f, 1f, 2f, 3f, 5f, 7f}, left, 1e-5f);
        // In over two samples, out over none: the loop's own envelope already took it down.
        float[] faded = new float[4];
        BattleSoundMix.hold(new float[] {1f}, new float[] {1f, 1f}, 0, 2, new float[] {1f, 1f}, new float[2], 2, 0,
                faded, new float[4]);
        assertArrayEquals(new float[] {0f, 0.5f, 1f, 1f}, faded, 1e-5f);
    }

    @Test
    void anIdGivenAsAnObjectWithItsSoundsResolvesLikeAPlainList(@TempDir Path mod) throws Exception {
        Files.createDirectories(mod.resolve("data/config"));
        Files.writeString(mod.resolve("data/config/sounds.json"), """
                {"plain_fire":[{"file":"sounds/a.ogg"}],
                 "missile_blast":{"sounds":[{"file":"sounds/b.ogg","volume":0.5},{"file":"sounds/c.ogg"}]}}""");
        Map<String, List<BattleSoundMix.Take>> takes = BattleSoundMix.takes(mod);
        assertEquals(1, takes.get("plain_fire").size());
        assertEquals(2, takes.get("missile_blast").size());
        assertEquals(0.5f, takes.get("missile_blast").get(0).volume(), 1e-6f);
    }
}
