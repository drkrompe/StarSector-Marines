package com.dillon.starsectormarines.render2d;

import com.dillon.starsectormarines.battle.combat.fx.Decal;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecalTilesTest {

    private static final SpriteSheetFrames.Frame SQUARE = new SpriteSheetFrames.Frame(0, 0, 100, 100);

    @Test
    void tilesTakeOverOnlyWhenTheViewIsSharperThanTheMapWideSurface() {
        assertFalse(DecalTiles.engaged(32f, 32), "drawn at the surface's own density");
        assertTrue(DecalTiles.engaged(64f, 32));
        assertTrue(DecalTiles.engaged(24f, 17), "a large map's softer surface hands over sooner");
        assertFalse(DecalTiles.engaged(200f, DecalTiles.PX_PER_CELL), "a surface as sharp as a tile needs none");
    }

    @Test
    void cellsFallIntoTheirTileAndASpanCountsItsTiles() {
        int side = DecalTiles.TILE_CELLS;
        assertEquals(0, DecalTiles.tileOf(side - 1));
        assertEquals(1, DecalTiles.tileOf(side));
        assertEquals(2, DecalTiles.span(side - 1, side));
        assertEquals(1, DecalTiles.span(0, side - 1));
        assertNotEquals(DecalTiles.key(1, 2), DecalTiles.key(2, 1));
    }

    @Test
    void aMarkReachesEveryTileItsTurnedQuadCanTouch() {
        int side = DecalTiles.TILE_CELLS;
        // A two-cell mark centred half a cell inside tile (0, 0): its corner reaches across the border into (1, 0).
        Decal nearEdge = new Decal(side - 0.5f, 4f, 0, 45f, 2f);
        assertTrue(DecalTiles.reaches(nearEdge, SQUARE, 0, 0));
        assertTrue(DecalTiles.reaches(nearEdge, SQUARE, 1, 0));
        assertFalse(DecalTiles.reaches(nearEdge, SQUARE, 2, 0));
        assertFalse(DecalTiles.reaches(nearEdge, SQUARE, 0, 1));
    }
}
