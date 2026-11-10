package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How an older lava flow's margin is drawn ({@link LavaFlowShading}). */
class LavaFlowShadingTest {

    private static final int W = 40, H = 40;

    /** A field over a flow of every cell west of column 20. */
    private static LavaFlowShading.Field westOf20() {
        float[] heat = new float[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < 20; x++) heat[y * W + x] = .5f;
        byte[] cover = LavaFlowShading.cover(heat, W, H);
        return new LavaFlowShading.Field() {
            @Override public int width() { return W; }
            @Override public int height() { return H; }
            @Override public int texel(int x, int y, int c) {
                return c == 0 ? cover[y * W + x] & 0xFF : c == 1 && x < 20 ? 128 : 0;
            }
        };
    }

    @Test
    void theCrustCoversTheFlowAndNothingPastItsMargin() {
        LavaFlowShading.Field field = westOf20();
        LavaFlowShading.Scratch s = new LavaFlowShading.Scratch();
        float[] out = new float[4];
        assertTrue(LavaFlowShading.shade(field, 10.5f, 20.5f, 0f, s, out));
        assertEquals(1f, out[0], 1e-4f, "whole inside the flow");
        assertFalse(LavaFlowShading.shade(field, 30.5f, 20.5f, 0f, s, out), "nothing well off it");
    }

    @Test
    void theMarginWandersAcrossTheCellsRatherThanRunningAlongTheirSides() {
        LavaFlowShading.Field field = westOf20();
        LavaFlowShading.Scratch s = new LavaFlowShading.Scratch();
        float[] out = new float[4];
        // Where the margin stands down each row, to a tenth of a cell: the flow's straight side, pushed by the noise.
        float least = Float.MAX_VALUE, most = -Float.MAX_VALUE;
        for (int row = 0; row < H * 4; row++) {
            float y = row / 4f + .125f, edge = 14f;
            while (edge < 26f && LavaFlowShading.shade(field, edge, y, 0f, s, out) && out[0] >= .5f) edge += .1f;
            least = Math.min(least, edge);
            most = Math.max(most, edge);
        }
        assertTrue(most - least > 1f, "lobed, not ruled along the cells' sides: " + least + ".." + most);
        assertTrue(least > 17f && most < 23f, "and kept to the flow's side: " + least + ".." + most);
    }
}
