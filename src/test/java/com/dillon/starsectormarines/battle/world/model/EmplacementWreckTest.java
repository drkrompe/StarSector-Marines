package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.GridLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmplacementWreckTest {

    private static final GridBlockDef INTACT = new GridBlockDef("intact", "a.png", 128, 0, 0, GridLayout.WALL_3X3, 0x969690);
    private static final GridBlockDef WRECK = new GridBlockDef("wreck", "b.png", 128, 3, 0, GridLayout.WALL_3X3, 0x969690);

    private static Doodad piece(String sheet, int col, int row, int cover) {
        return new Doodad(7, 9, new TileManifest.TileFrame(col, row), sheet, cover, 0.6f, 1, 1, null, 128);
    }

    @Test
    void aPieceBecomesTheSameSlotOfTheOtherPatchKeepingItsCover() {
        Doodad wrecked = EmplacementWreck.inState(piece("a.png", 2, 1, Doodad.COVER_HEAVY), INTACT, WRECK);
        assertEquals("b.png", wrecked.sheetPath);
        assertEquals(5, wrecked.tile.col);
        assertEquals(1, wrecked.tile.row);
        assertEquals(Doodad.COVER_HEAVY, wrecked.cover);
        assertEquals(0.6f, wrecked.ballisticHalfHeight);
        assertEquals(7, wrecked.cellX);
        assertEquals(9, wrecked.cellY);
    }

    @Test
    void anythingElseIsLeftAlone() {
        assertNull(EmplacementWreck.inState(piece("c.png", 0, 0, 0), INTACT, WRECK), "another sheet");
        assertNull(EmplacementWreck.inState(piece("a.png", 3, 0, 0), INTACT, WRECK), "outside the patch");
        assertNull(EmplacementWreck.inState(piece("b.png", 3, 0, 0), INTACT, WRECK), "already wrecked");
    }
}
