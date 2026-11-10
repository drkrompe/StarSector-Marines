package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pieces coloured corner by corner ({@link CornerShading}). */
class CornerShadingTest {

    private static NaturalVoidField.Band squares() {
        // Two unit squares side by side, sharing the side x = 1.
        NaturalVoidField.Band band = new NaturalVoidField.Band(0, 0f);
        band.add(new float[] {0, 0, 1, 0, 1, 1, 0, 1}, 4, new float[8]);
        band.add(new float[] {1, 0, 2, 0, 2, 1, 1, 1}, 4, new float[8]);
        return band;
    }

    @Test
    void neighboursAgreeAlongTheirSharedSideAndKeepTheirOwnColourElsewhere() {
        NaturalVoidField.Band band = squares();
        float[] flat = {0f, 0f, 0f, 1f, 1f, 1f};
        float[] c = CornerShading.corners(new NaturalVoidField.Band[] {band}, new float[][] {flat})[0];
        // The left square's corners 1 and 2 stand on the shared side; the right square's 0 and 3.
        assertEquals(.5f, c[3], 1e-6f);
        assertEquals(.5f, c[6], 1e-6f);
        assertEquals(.5f, c[12], 1e-6f);
        assertEquals(.5f, c[12 + 9], 1e-6f);
        assertEquals(0f, c[0], 1e-6f, "the left square's far side stays its own");
        assertEquals(1f, c[12 + 3], 1e-6f, "the right square's far side stays its own");
    }

    @Test
    void cornersAreSharedAcrossBandsPassedTogether() {
        NaturalVoidField.Band left = new NaturalVoidField.Band(0, 0f), right = new NaturalVoidField.Band(1, 0f);
        left.add(new float[] {0, 0, 1, 0, 1, 1, 0, 1}, 4, new float[8]);
        right.add(new float[] {1, 0, 2, 0, 2, 1, 1, 1}, 4, new float[8]);
        float[][] c = CornerShading.corners(new NaturalVoidField.Band[] {left, right},
                new float[][] {{0f, 0f, 0f}, {1f, 1f, 1f}});
        assertEquals(.5f, c[0][3], 1e-6f);
        assertEquals(.5f, c[1][0], 1e-6f);
    }
}
