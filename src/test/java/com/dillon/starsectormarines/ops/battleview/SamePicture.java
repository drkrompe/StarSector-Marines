package com.dillon.starsectormarines.ops.battleview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two GL read-backs of what should be one picture, drawn by two routes that address the same texels with different
 * arithmetic: one sheet's texture against an atlas slot, a quad turned and lifted on the CPU against the same quad on
 * the modelview. A float there can land a bilinear tap a hair either side of where the other route lands it, and
 * where the texels either side differ that rounds a channel {@link #ROUNDING} level apart, on a scattering of pixels.
 * Anything the comparison is for (a quad in the wrong slot or place, a wrong frame or tint) moves pixels by tens of
 * levels across whole cells.
 *
 * <p>The bound is measured, not chosen (2026-11-10, RTX 3080): {@code RoofMeshGlEvidence} 50 and
 * {@code GroundAtlasGlEvidence} 33 of 98304 pixels, one level each. For the roofs, giving both routes the same
 * source pixels ({@link RoofTiles#framePixel}) took the worst from 106 levels to 1, and drawing every roof flat (lift
 * 1) left 7 of the 50, all in a turned broken cell: what remains is the transform's float rounding. For the ground,
 * every differing pixel is in a cell drawn from the nature sheet, whose own texture is padded to a power of two so
 * its coordinates are inexact fractions where the atlas slot's are not. Comparisons that need not cross two routes
 * stay exact (a patched roof against a fresh bake). Widen this only on the same kind of evidence: a trace showing
 * the new difference is rounding, not a quad, frame or tint gone wrong.
 */
final class SamePicture {

    /** Levels a channel may differ by. */
    static final int ROUNDING = 1;

    private SamePicture() {}

    /**
     * {@code expected} and {@code actual}, RGBA rows {@code width} pixels across, are the same picture but for
     * {@link #ROUNDING} on at most a hundredth of its pixels.
     */
    static void assertSame(byte[] expected, byte[] actual, int width, String what, String message) {
        assertEquals(expected.length, actual.length, message);
        int pixels = expected.length / 4;
        int differing = 0, worst = 0, worstAt = -1;
        for (int p = 0; p < pixels; p++) {
            int d = 0;
            for (int ch = 0; ch < 4; ch++) {
                d = Math.max(d, Math.abs((expected[p * 4 + ch] & 0xFF) - (actual[p * 4 + ch] & 0xFF)));
            }
            if (d == 0) continue;
            differing++;
            if (d > worst) {
                worst = d;
                worstAt = p;
            }
        }
        System.out.println("[shader-evidence] " + what + ": " + differing + " of " + pixels
                + " pixels differ, by at most " + worst + " levels");
        assertTrue(worst <= ROUNDING, message + ": " + worst + " levels apart at pixel "
                + (worstAt % width) + "," + (worstAt / width));
        assertTrue(differing <= pixels / 100, message + ": " + differing + " pixels differ");
    }
}
