package com.dillon.starsectormarines.battle.world.tiles;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpriteSheetGridSliceTest {

    @Test
    void eachCellIsCutToItsContentSymmetricAboutTheCellsCentre() {
        BufferedImage sheet = new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB);
        // Cell 0 (x 0..19): content from x 12..14, y 8..9 -- reaching 5 px right of the centre and 2 px up.
        for (int x = 12; x <= 14; x++) for (int y = 8; y <= 9; y++) sheet.setRGB(x, y, 0xFF000000);
        // Cell 1 (x 20..39) is empty.

        SpriteSheetFrames frames = SpriteSheetSlicer.sliceGrid(sheet, 20);

        assertEquals(2, frames.frames.length);
        SpriteSheetFrames.Frame cut = frames.frames[0];
        assertEquals(5, cut.x);
        assertEquals(8, cut.y);
        assertEquals(10, cut.w);
        assertEquals(4, cut.h);
        assertEquals(0, frames.frames[1].w);
        assertEquals(0, frames.frames[1].h);
    }
}
