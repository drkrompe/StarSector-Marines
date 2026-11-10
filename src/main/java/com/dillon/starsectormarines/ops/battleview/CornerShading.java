package com.dillon.starsectormarines.ops.battleview;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Colours bands' pieces corner by corner rather than flat: each corner takes the mean colour of every piece that
 * meets there, so neighbouring pieces agree along their shared sides and the colour runs smoothly across them instead
 * of stepping from piece to piece.
 *
 * <p>Pieces meet where their corners stand at the same place, to {@link #GRID} of a cell; a corner no other piece
 * shares keeps its own piece's colour. Which corners meet is worked out once, on construction, so a colouring that
 * changes every frame is shaded again cheaply.
 */
final class CornerShading {

    /** Corners nearer than this, in cells, are one corner. */
    static final float GRID = 1f / 1024f;

    /** Per band, per piece corner (four a piece), the shared corner it stands on; -1 for a triangle's repeat. */
    private final int[][] corner;
    private final float[] sums;

    /** The corners shared across all of {@code bands}: pass together only bands that meet edge to edge. */
    CornerShading(NaturalVoidField.Band... bands) {
        Map<Long, Integer> ids = new HashMap<>();
        corner = new int[bands.length][];
        for (int k = 0; k < bands.length; k++) {
            NaturalVoidField.Band band = bands[k];
            int[] at = new int[band.count * 4];
            for (int q = 0; q < band.count; q++) {
                for (int v = 0; v < 4; v++) {
                    // A triangle repeats its last corner: it counts once.
                    if (v == 3 && same(band.quads, q, 2, 3)) { at[q * 4 + v] = -1; continue; }
                    at[q * 4 + v] = ids.computeIfAbsent(key(band.quads, q, v), x -> ids.size());
                }
            }
            corner[k] = at;
        }
        sums = new float[ids.size() * 4];
    }

    /**
     * The corner colours of the bands' pieces into {@code out} (per band, twelve floats a piece: {@code (r, g, b)}
     * for each of its four corners, in vertex order), from each piece's flat colour in {@code flat} (per band, three
     * floats a piece).
     */
    void shade(float[][] flat, float[][] out) {
        Arrays.fill(sums, 0f);
        for (int k = 0; k < corner.length; k++) {
            int[] at = corner[k];
            float[] f = flat[k];
            for (int i = 0; i < at.length; i++) {
                int c = at[i];
                if (c < 0) continue;
                int q = i >> 2;
                sums[c * 4] += f[q * 3];
                sums[c * 4 + 1] += f[q * 3 + 1];
                sums[c * 4 + 2] += f[q * 3 + 2];
                sums[c * 4 + 3]++;
            }
        }
        for (int k = 0; k < corner.length; k++) {
            int[] at = corner[k];
            float[] o = out[k];
            for (int i = 0; i < at.length; i++) {
                // A triangle's repeated corner is its third.
                int c = at[i] >= 0 ? at[i] : at[i - 1];
                float n = sums[c * 4 + 3];
                o[i * 3] = sums[c * 4] / n;
                o[i * 3 + 1] = sums[c * 4 + 1] / n;
                o[i * 3 + 2] = sums[c * 4 + 2] / n;
            }
        }
    }

    /** {@link #shade} once, into new arrays. */
    static float[][] corners(NaturalVoidField.Band[] bands, float[][] flat) {
        float[][] out = new float[bands.length][];
        for (int k = 0; k < bands.length; k++) out[k] = new float[bands[k].count * 12];
        new CornerShading(bands).shade(flat, out);
        return out;
    }

    private static boolean same(float[] quads, int q, int a, int b) {
        int o = q * 8;
        return quads[o + a * 2] == quads[o + b * 2] && quads[o + a * 2 + 1] == quads[o + b * 2 + 1];
    }

    private static long key(float[] quads, int q, int v) {
        int o = q * 8 + v * 2;
        long x = Math.round(quads[o] / GRID), y = Math.round(quads[o + 1] / GRID);
        return (x << 32) ^ (y & 0xFFFFFFFFL);
    }
}
