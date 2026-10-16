package com.dillon.starsectormarines.tools.snapshot;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CompressedFrameListTest {

    @Test
    void framesComeBackPixelForPixelFromAnyIndex() {
        CompressedFrameList frames = new CompressedFrameList();
        BufferedImage first = image(BufferedImage.TYPE_INT_ARGB, 0xFF102030);
        BufferedImage second = image(BufferedImage.TYPE_3BYTE_BGR, 0x00A0B0C0);
        frames.add(first);
        frames.add(second);

        assertEquals(2, frames.size());
        assertEquals(0x00A0B0C0, frames.get(1).getRGB(3, 2) & 0xFFFFFF);
        assertEquals(0x102030, frames.get(0).getRGB(3, 2) & 0xFFFFFF);
        assertEquals(5, frames.get(0).getWidth());
    }

    @Test
    void framesSpilledToDiskComeBackInOrder() {
        CompressedFrameList frames = new CompressedFrameList(true);
        for (int i = 0; i < 6; i++) frames.add(image(BufferedImage.TYPE_INT_ARGB, 0xFF000000 | i * 0x102030));
        assertEquals(6, frames.size());
        for (int i = 5; i >= 0; i--) assertEquals(i * 0x102030, frames.get(i).getRGB(4, 3) & 0xFFFFFF);
    }

    @Test
    void anArtifactKeepsTheListRatherThanCopyingItOut() {
        CompressedFrameList frames = new CompressedFrameList();
        frames.add(image(BufferedImage.TYPE_3BYTE_BGR, 0x123456));
        assertSame(frames, SnapshotArtifact.animation("loop.gif", frames, 125).frames());
    }

    private static BufferedImage image(int type, int rgb) {
        BufferedImage image = new BufferedImage(5, 4, type);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 5; x++) image.setRGB(x, y, rgb);
        return image;
    }
}
