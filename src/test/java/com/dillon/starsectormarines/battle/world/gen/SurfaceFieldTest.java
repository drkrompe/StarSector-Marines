package com.dillon.starsectormarines.battle.world.gen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceFieldTest {

    private static final int W = 200, H = 120;

    @Test
    void withNoBasinEveryCellIsTheWorldsGroundAndKeepsItsWoodsAndWater() {
        SurfaceField field = new SurfaceField(SurfacePalette.ARID, Basin.NONE, W, H, 7);
        for (int x = 0; x < W; x += 7) for (int y = 0; y < H; y += 7) {
            assertEquals(SurfacePalette.ARID, field.at(x, y));
            assertEquals(1f, field.woods(x, y));
            assertFalse(field.dryRim(x, y), "a brook with no basin runs as authored");
        }
        assertTrue(field.uniform());
    }

    @Test
    void aBasinIsItsFloorInTheMiddleAndTheWorldAtTheCorners() {
        SurfaceField field = new SurfaceField(SurfacePalette.ARID, new Basin(SurfacePalette.VERDANT, .7f, .7f), W, H, 7);
        assertEquals(SurfacePalette.VERDANT, field.at(W / 2, H / 2));
        assertEquals(1f, field.woods(W / 2, H / 2), "deep in the floor the woods are whole");
        assertEquals(SurfacePalette.ARID, field.at(0, 0));
        assertEquals(0f, field.woods(0, 0));
        assertTrue(field.dryRim(0, 0), "a desert rim's brooks run dry");
        assertFalse(field.dryRim(W / 2, H / 2));
    }

    @Test
    void theWoodsThinFromTheMiddleOutward() {
        SurfaceField field = new SurfaceField(SurfacePalette.ARID, new Basin(SurfacePalette.VERDANT, .9f, .9f), W, H, 3);
        float last = 2f;
        int y = H / 2;
        for (int x = W / 2; x >= 0; x -= 10) {
            float woods = field.woods(x, y);
            assertTrue(woods <= last + .2f, "woods do not thicken heading out, at " + x + ": " + woods + " after " + last);
            last = Math.min(last, woods);
        }
        assertEquals(0f, field.woods(0, y));
    }

    @Test
    void aBasinOfTheWorldsOwnPaletteIsNoBasin() {
        assertTrue(new SurfaceField(SurfacePalette.VERDANT, new Basin(SurfacePalette.VERDANT, .5f, .5f), W, H, 1).uniform());
        assertFalse(Basin.ravBloom(SurfacePalette.VERDANT, 1f, 1f).present());
        assertTrue(Basin.ravBloom(SurfacePalette.ARID, 1f, 1f).present());
    }
}
