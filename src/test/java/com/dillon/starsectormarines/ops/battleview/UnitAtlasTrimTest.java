package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which part of an image the unit atlas holds: its art, a texel of margin, and no side whose trim moves art's edge. */
class UnitAtlasTrimTest {

    @Test
    void artWithMarginAllRoundIsHeldWithOneTexelOfIt() {
        // A station arm: 150x240 canvas, art 48x97 in the middle of it.
        assertArrayEquals(new int[]{50, 70, 50, 99},
                UnitAtlas.trimRect(150, 240, 150, 240, new int[]{51, 71, 99, 168}));
    }

    @Test
    void aSideIsKeptWhenArtReachesTheEdgesItWouldMove() {
        // A rifle whose barrel runs the canvas's full height: trimming its side columns would move the ends of the
        // top and bottom edges the art is drawn on, so the columns stay.
        assertArrayEquals(new int[]{0, 0, 25, 98}, UnitAtlas.trimRect(25, 98, 25, 98, new int[]{2, 0, 24, 98}));
        // Art at the left edge only: the columns past it on the right may go, but no row may, since trimming a row
        // moves the ends of the left edge.
        assertArrayEquals(new int[]{0, 0, 31, 40}, UnitAtlas.trimRect(40, 40, 40, 40, new int[]{0, 10, 30, 30}));
    }

    @Test
    void aFilteredCopyIsTrimmedInItsOwnTexelsRoundedOutward() {
        // A 400x400 image held as a 100x100 copy: art at 101..299 covers copy texels 25..75.
        assertArrayEquals(new int[]{24, 24, 52, 52},
                UnitAtlas.trimRect(100, 100, 400, 400, new int[]{101, 101, 299, 299}));
    }

    @Test
    void opaqueBoundsCoverEveryTexelThatIsNotFullyTransparent() {
        BufferedImage image = new BufferedImage(10, 8, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(3, 2, 0x01FFFFFF);
        image.setRGB(6, 5, 0xFF000000);
        assertArrayEquals(new int[]{3, 2, 7, 6}, BattleSprites.opaqueBounds(image));
        assertEquals(0, BattleSprites.opaqueBounds(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)).length);
    }
}
