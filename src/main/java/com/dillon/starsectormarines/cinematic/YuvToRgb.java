package com.dillon.starsectormarines.cinematic;

import java.nio.ByteBuffer;

/**
 * BT.709 limited-range YUV 4:2:0 to packed RGB, on a decode worker rather than the render thread.
 *
 * <p>The planes are JCodec's: it stores every sample as {@code value - 128}, so a stored byte is
 * added back before the matrix is applied. The packager tags its video BT.709 limited, so that
 * is the only matrix needed.
 */
public final class YuvToRgb {
    private YuvToRgb() { }

    /**
     * Writes {@code width * height * 3} bytes, top row first, into {@code out} at its position
     * (advancing it). {@code yStride} and {@code cStride} are the planes' row lengths, which may
     * exceed the visible width.
     */
    public static void convert(byte[] y, int yStride, byte[] u, byte[] v, int cStride,
                               int width, int height, ByteBuffer out) {
        byte[] row = new byte[width * 3];
        for (int j = 0; j < height; j++) {
            int yRow = j * yStride;
            int cRow = (j >> 1) * cStride;
            int o = 0;
            for (int i = 0; i < width; i++) {
                int c = 298 * (((y[yRow + i] + 128) & 0xFF) - 16);
                int d = u[cRow + (i >> 1)];      // already centred: stored value is sample - 128
                int e = v[cRow + (i >> 1)];
                row[o++] = clamp((c + 459 * e + 128) >> 8);
                row[o++] = clamp((c - 55 * d - 136 * e + 128) >> 8);
                row[o++] = clamp((c + 541 * d + 128) >> 8);
            }
            out.put(row);
        }
    }

    private static byte clamp(int value) {
        return (byte) (value < 0 ? 0 : value > 255 ? 255 : value);
    }
}
