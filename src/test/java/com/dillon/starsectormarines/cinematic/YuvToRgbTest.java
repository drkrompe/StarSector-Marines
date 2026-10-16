package com.dillon.starsectormarines.cinematic;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YuvToRgbTest {
    /** JCodec stores each sample minus 128. */
    private static byte stored(int sample) { return (byte) (sample - 128); }

    private static int[] pixel(int y, int u, int v) {
        ByteBuffer out = ByteBuffer.allocate(2 * 2 * 3);
        YuvToRgb.convert(new byte[] {stored(y), stored(y), stored(y), stored(y)}, 2,
                new byte[] {stored(u)}, new byte[] {stored(v)}, 1, 2, 2, out);
        return new int[] {out.get(0) & 0xFF, out.get(1) & 0xFF, out.get(2) & 0xFF};
    }

    @Test void limitedRangeBlackAndWhiteMapToTheFullRange() {
        assertEquals(0, pixel(16, 128, 128)[0]);
        assertEquals(255, pixel(235, 128, 128)[1]);
        assertEquals(255, pixel(235, 128, 128)[2]);
    }

    @Test void bt709PrimariesLandOnTheirChannel() {
        int[] red = pixel(63, 102, 240);     // BT.709 limited-range red
        assertEquals(255, red[0], 3);
        assertEquals(0, red[1], 3);
        assertEquals(0, red[2], 3);
    }

    @Test void rowsAreWrittenTopFirst() {
        ByteBuffer out = ByteBuffer.allocate(2 * 2 * 3);
        YuvToRgb.convert(new byte[] {stored(16), stored(16), stored(235), stored(235)}, 2,
                new byte[] {stored(128)}, new byte[] {stored(128)}, 1, 2, 2, out);
        assertEquals(0, out.get(0) & 0xFF);
        assertEquals(255, out.get(6) & 0xFF);
    }
}
