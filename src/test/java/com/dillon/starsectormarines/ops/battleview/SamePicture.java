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
