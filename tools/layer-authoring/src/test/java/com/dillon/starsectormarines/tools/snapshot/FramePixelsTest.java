package com.dillon.starsectormarines.tools.snapshot;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class FramePixelsTest {

    @Test
    void readsWhatGetRgbReadsForEveryFrameLayout() {
        Random random = new Random(3);
        for (int type : new int[]{BufferedImage.TYPE_3BYTE_BGR, BufferedImage.TYPE_INT_ARGB,
                BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB_PRE, BufferedImage.TYPE_4BYTE_ABGR}) {
            BufferedImage image = new BufferedImage(37, 23, type);
            for (int y = 0; y < 23; y++) for (int x = 0; x < 37; x++) image.setRGB(x, y, random.nextInt());
            assertArrayEquals(image.getRGB(0, 0, 37, 23, null, 0, 37), FramePixels.argb(image), "type " + type);
            BufferedImage cut = image.getSubimage(3, 2, 20, 11);
            assertArrayEquals(cut.getRGB(0, 0, 20, 11, null, 0, 20), FramePixels.argb(cut), "subimage of " + type);
        }
    }
}
