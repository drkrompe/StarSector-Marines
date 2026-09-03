package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The atlas layout, which is arithmetic and needs no GPU to be wrong.
 *
 * <p>What a driver has to answer — whether a quad moved into a slot samples the
 * same texels — is {@code GroundAtlasGlEvidence}'s question. What is settled
 * here is the property that makes that possible at all: that no two sheets can
 * touch, so a bilinear tap at a slot's edge cannot reach the sheet next door.
 */
class GroundAtlasTest {

    /** The sheets this mod actually ships for the ground layer, in load order. */
    private static final int[] SHIPPED_W = {512, 512, 896, 48, 294, 1856};
    private static final int[] SHIPPED_H = {224, 192, 168, 16, 48, 61};

    @Test
    void everySheetGetsItsOwnRectangleAndTheGuttersHold() {
        int[] packed = GroundAtlas.shelfPack(SHIPPED_W, SHIPPED_H, GroundAtlas.MAX_SIDE_PX);
        assertNotNull(packed, "the sheets this mod ships have to fit the stated cap");
        int count = SHIPPED_W.length;
        int height = packed[count * 2];
        assertTrue(height > 0 && height <= GroundAtlas.MAX_SIDE_PX);

        for (int i = 0; i < count; i++) {
            int x = packed[i * 2];
            int y = packed[i * 2 + 1];
            assertTrue(x >= GroundAtlas.GUTTER_PX && y >= GroundAtlas.GUTTER_PX,
                    "sheet " + i + " has to clear the atlas edge by a gutter");
            assertTrue(x + SHIPPED_W[i] + GroundAtlas.GUTTER_PX <= GroundAtlas.MAX_SIDE_PX,
                    "sheet " + i + " runs off the right edge");
            assertTrue(y + SHIPPED_H[i] + GroundAtlas.GUTTER_PX <= height,
                    "sheet " + i + " runs off the bottom edge");
        }

        for (int a = 0; a < count; a++) {
            for (int b = a + 1; b < count; b++) {
                assertTrue(clearOf(packed, a, b),
                        "sheets " + a + " and " + b + " have to be a gutter apart, or a "
                                + "bilinear tap at the edge of one samples the other");
            }
        }
    }

    /** Whether the two rectangles, each grown by the gutter, still miss each other. */
    private static boolean clearOf(int[] packed, int a, int b) {
        int ax = packed[a * 2];
        int ay = packed[a * 2 + 1];
        int bx = packed[b * 2];
        int by = packed[b * 2 + 1];
        return ax + SHIPPED_W[a] + GroundAtlas.GUTTER_PX <= bx
                || bx + SHIPPED_W[b] + GroundAtlas.GUTTER_PX <= ax
                || ay + SHIPPED_H[a] + GroundAtlas.GUTTER_PX <= by
                || by + SHIPPED_H[b] + GroundAtlas.GUTTER_PX <= ay;
    }

    @Test
    void aSheetWiderThanTheAtlasIsRefusedRatherThanClipped() {
        assertNull(GroundAtlas.shelfPack(new int[]{600, 64}, new int[]{64, 64}, 512),
                "a sheet that cannot fit the width has no slot, and half of one is worse "
                        + "than none");
    }

    @Test
    void aSetTallerThanTheAtlasIsRefusedRatherThanWrapped() {
        int[] wide = new int[8];
        int[] tall = new int[8];
        for (int i = 0; i < 8; i++) {
            wide[i] = 400;
            tall[i] = 200;
        }
        assertNull(GroundAtlas.shelfPack(wide, tall, 512),
                "eight shelves of two hundred do not fit in five hundred and twelve");
    }

    @Test
    void theLayoutIsIndependentOfTheOrderTheSheetsArrive() {
        int[] forwardW = {512, 48, 1856};
        int[] forwardH = {224, 16, 61};
        int[] reverseW = {1856, 48, 512};
        int[] reverseH = {61, 16, 224};
        int[] forward = GroundAtlas.shelfPack(forwardW, forwardH, GroundAtlas.MAX_SIDE_PX);
        int[] reverse = GroundAtlas.shelfPack(reverseW, reverseH, GroundAtlas.MAX_SIDE_PX);
        assertNotNull(forward);
        assertNotNull(reverse);
        // Same three rectangles, arriving in opposite orders: each still lands
        // where its own size puts it, because the pack sorts before it places.
        assertEquals(forward[0], reverse[4]);
        assertEquals(forward[1], reverse[5]);
        assertEquals(forward[4], reverse[0]);
        assertEquals(forward[5], reverse[1]);
        assertEquals(forward[6], reverse[6], "and the atlas is the same height either way");
    }
}
