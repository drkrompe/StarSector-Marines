package com.dillon.starsectormarines.battle.world.tiles;

import com.dillon.starsectormarines.battle.world.model.TileManifest;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Parity oracle for moddable-tilesets Phase 1c (grid blocks): pins each
 * {@link GridBlockDef} resolver loaded from {@code urban-tileset.tileset.json}
 * to the hardcoded {@code TileManifest.pickXxxTile} it replaces, across all 16
 * neighbor masks. Green here means the render migration off the static pickers
 * is behavior-preserving (same source cell + same null/fill case).
 */
public class GridBlockParityTest {

    private static TileRegistry load() throws Exception {
        TileRegistry reg = new TileRegistry();
        reg.ingestSheet(new JSONObject(Files.readString(Paths.get("mod/data/tilesets/urban-tileset.tileset.json"))));
        return reg;
    }

    @Test
    void wallBlockMatchesPickWallTile() throws Exception {
        GridBlockDef wall = load().block("urban.wall");
        assertNotNull(wall, "urban.wall block not loaded");
        assertEquals(32, wall.cellPx);
        assertEquals(Integer.valueOf(0x060A10), wall.fillRgb, "wall fill must match GroundRenderSystem WALL_COLOR");
        for (int m = 0; m < 16; m++) {
            boolean n = (m & 1) != 0, s = (m & 2) != 0, e = (m & 4) != 0, w = (m & 8) != 0;
            int[] got = wall.resolve(n, s, e, w);
            TileManifest.TileFrame want = TileManifest.pickWallTile(n, s, e, w);
            if (want == null) {
                assertNull(got, "wall mask " + m + " should be the enclosed/null case");
            } else {
                assertNotNull(got, "wall mask " + m);
                assertEquals(want.col, got[0], "wall col, mask " + m);
                assertEquals(want.row, got[1], "wall row, mask " + m);
            }
        }
    }

    @Test
    void floorBlockMatchesPickFloorTile() throws Exception {
        GridBlockDef floor = load().block("urban.floor");
        assertNull(floor.fillRgb, "floor never returns the null case");
        for (int m = 0; m < 16; m++) {
            boolean n = (m & 1) != 0, s = (m & 2) != 0, e = (m & 4) != 0, w = (m & 8) != 0;
            int[] got = floor.resolve(n, s, e, w);
            TileManifest.TileFrame want = TileManifest.pickFloorTile(n, s, e, w);
            assertEquals(want.col, got[0], "floor col, mask " + m);
            assertEquals(want.row, got[1], "floor row, mask " + m);
        }
    }

    @Test
    void rubbleBlockMatchesPickRubbleTile() throws Exception {
        GridBlockDef rubble = load().block("urban.rubble");
        for (int m = 0; m < 16; m++) {
            boolean n = (m & 1) != 0, s = (m & 2) != 0, e = (m & 4) != 0, w = (m & 8) != 0;
            int[] got = rubble.resolve(n, s, e, w);
            TileManifest.TileFrame want = TileManifest.pickRubbleTile(n, s, e, w);
            assertEquals(want.col, got[0], "rubble col, mask " + m);
            assertEquals(want.row, got[1], "rubble row, mask " + m);
        }
    }

    @Test
    void doorBlockIsSingleCell() throws Exception {
        GridBlockDef door = load().block("urban.door-open");
        assertEquals(GridLayout.SINGLE, door.layout);
        int[] c = door.resolve(true, false, true, false);
        assertEquals(7, c[0]);
        assertEquals(2, c[1]);
        assertArrayEquals(door.resolve(false, false, false, false), door.resolve(true, true, true, true),
                "single cell is neighbor-independent");
    }
}
