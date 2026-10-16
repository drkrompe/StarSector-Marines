package com.dillon.starsectormarines.cinematic;

import com.dillon.starsectormarines.cinematic.ShotDecoder.Frame;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Two closed 24-frame groups of 96x54 (not a multiple of 16 high), decoded on two workers. */
class ShotDecoderTest {
    private static Callable<byte[]> clip() {
        return () -> {
            try (var in = ShotDecoderTest.class.getResourceAsStream("/cinematic/tiny-gop.mp4")) {
                return in.readAllBytes();
            }
        };
    }

    private static void awaitFrame(ShotDecoder decoder, int index) throws InterruptedException {
        for (int i = 0; i < 3000 && decoder.frame(index) == null && decoder.failure() == null; i++) Thread.sleep(5);
    }

    @Test void bothGroupsDecodeAndTheBackgroundIsTheEncodedColour() throws Exception {
        try (ShotDecoder decoder = new ShotDecoder(clip(), 48, 96, 54, 48).start(2)) {
            awaitFrame(decoder, 47);
            assertNull(decoder.failure());
            for (int index : new int[] {0, 30, 47}) {
                Frame frame = decoder.frame(index);
                assertNotNull(frame, "frame " + index);
                ByteBuffer rgb = ByteBuffer.allocate(96 * 54 * 3);
                YuvToRgb.convert(frame.planes[0], frame.lumaStride, frame.planes[1], frame.planes[2], frame.chromaStride, 96, 54, rgb);
                int at = (50 * 96 + 5) * 3;     // bottom-left, clear of the moving box
                assertEquals(0x20, rgb.get(at) & 0xFF, 8);
                assertEquals(0x60, rgb.get(at + 1) & 0xFF, 8);
                assertEquals(0xC0, rgb.get(at + 2) & 0xFF, 8);
            }
        }
    }

    @Test void theWindowHoldsBackFramesTheClockHasNotReached() throws Exception {
        try (ShotDecoder decoder = new ShotDecoder(clip(), 48, 96, 54, ShotDecoder.GOP).start(2)) {
            awaitFrame(decoder, 23);
            Thread.sleep(150);
            assertNotNull(decoder.frame(23));
            assertNull(decoder.frame(24));
            decoder.consumed(24);
            awaitFrame(decoder, 40);
            assertNotNull(decoder.frame(40));
        }
    }

    @Test void aMissingFileFailsTheDecoderRatherThanThrowingToTheCaller() throws Exception {
        try (ShotDecoder decoder = new ShotDecoder(() -> { throw new IOException("no such video"); }, 48, 96, 54, 48).start(1)) {
            for (int i = 0; i < 3000 && decoder.failure() == null; i++) Thread.sleep(5);
            assertNotNull(decoder.failure());
        }
    }
}
