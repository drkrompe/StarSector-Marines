package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NestTrailsTest {

    private static final float LENGTH = 24f;
    private static float[] grown(float progress) {
        float[] out = new float[2];
        NestTrails.growth(progress, out);
        return out;
    }

    @Test
    void aRootDigsOutAsAVeinThenThickensIntoRopeAndIsWholeWhenItsGrowthRises() {
        float dig = NestTrails.DIG_SHARE;
        assertEquals(0f, grown(0f)[0], 1e-6f, "nothing the moment its growth is begun");
        assertEquals(0.5f, grown(dig / 2)[0], 1e-4f, "half way there at half the dig");
        assertEquals(0f, grown(dig / 2)[1], 1e-6f, "still a vein while it digs");
        assertEquals(1f, grown(dig)[0], 1e-6f, "it reaches the site");
        assertEquals(0.5f, grown(dig + (1f - dig) / 2)[1], 1e-4f, "then thickens");
        assertEquals(1f, grown(1f)[1], 1e-6f, "a rope by the time the growth rises at its end");
    }

    @Test
    void aVeinIsLaidThinnerThanAnyRope() {
        NestTrails.Trail vein = new NestTrails.Trail(0, 0, LENGTH, 0, 0.6f, 0.3f, 1L, 0.5f, 0f);
        assertEquals(NestTrails.VEIN_HALF_WIDTH, NestTrails.laidHalfWidth(vein, 0.2f), 1e-6f);
        NestTrails.Trail rope = new NestTrails.Trail(0, 0, LENGTH, 0, 0.6f, 0.3f, 1L, 1f, 1f);
        assertEquals(0.6f, NestTrails.laidHalfWidth(rope, 0f), 1e-6f);
        assertEquals(0.3f, NestTrails.laidHalfWidth(rope, 1f), 1e-6f);
        NestTrails.Trail starting = new NestTrails.Trail(0, 0, LENGTH, 0, 0.6f, 0.3f, 1L, 1f, 0.01f);
        assertEquals(NestTrails.ROPE_START, NestTrails.laidHalfWidth(starting, 0f), 0.01f, "a rope starts thin");
    }
}
