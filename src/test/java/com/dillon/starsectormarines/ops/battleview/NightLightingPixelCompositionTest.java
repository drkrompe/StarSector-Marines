package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/** Small pixel arithmetic tests; no world, asset catalog or graphics context. */
class NightLightingPixelCompositionTest {
    @Test
    void transparentCornersAndInternalGapsRetainExactFogPixels() {
        BufferedImage fog = pixels(0xc0123456, 0xc0123456, 0xc0123456, 0xc0123456);
        BufferedImage lit = pixels(0x40abcdef, 0x40abcdef, 0x40abcdef, 0x40abcdef);
        // RGB in the mask carries no meaning, even under completely transparent pixels.
        BufferedImage coverage = pixels(0x00123456, 0xff000000, 0x00ffffff, 0xffabcdef);
        BufferedImage result = HeadlessBattleSceneRenderer.compositeNightLighting(fog, lit, coverage);
        assertEquals(fog.getRGB(0, 0), result.getRGB(0, 0), "transparent corner");
        assertEquals(fog.getRGB(2, 0), result.getRGB(2, 0), "internal gap");
        assertEquals(lit.getRGB(1, 0), result.getRGB(1, 0), "opaque hull");
        assertEquals(lit.getRGB(3, 0), result.getRGB(3, 0), "mask RGB ignored");
        assertEquals(0xc0123456, fog.getRGB(0, 0));
        assertEquals(0x40abcdef, lit.getRGB(0, 0));
        assertEquals(0x00123456, coverage.getRGB(0, 0));
    }

    @Test
    void partialEdgesMixPremultipliedColorAndOpacity() {
        BufferedImage fog = pixels(0xc0000000);
        BufferedImage lit = pixels(0x40ffffff);
        BufferedImage result = HeadlessBattleSceneRenderer.compositeNightLighting(fog, lit, pixels(0x80000000));
        int mixed = result.getRGB(0, 0);
        assertEquals(128, mixed >>> 24, 1);
        // The dark contribution has three times the alpha: straight RGB averaging would incorrectly give 128.
        for (int shift = 0; shift <= 16; shift += 8)
            assertEquals(64, (mixed >>> shift) & 255, 1);
    }

    @Test
    void emptyTreatmentsRemainTransparentAndMismatchedTargetsAreRejected() {
        BufferedImage result = HeadlessBattleSceneRenderer.compositeNightLighting(pixels(0), pixels(0), pixels(0x80000000));
        assertEquals(0, result.getRGB(0, 0));
        assertThrows(IllegalArgumentException.class, () -> HeadlessBattleSceneRenderer.compositeNightLighting(
                pixels(0), pixels(0, 0), pixels(0)));
    }

    private static BufferedImage pixels(int... argb) {
        BufferedImage image = new BufferedImage(argb.length, 1, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < argb.length; x++) image.setRGB(x, 0, argb[x]);
        return image;
    }
}
