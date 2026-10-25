package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
}
