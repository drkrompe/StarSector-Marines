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

    // ----- urban-tileset-2 (road sheet) ---------------------------------------

    private static TileRegistry loadUrban2() throws Exception {
        TileRegistry reg = new TileRegistry();
        reg.ingestSheet(new JSONObject(Files.readString(Paths.get("mod/data/tilesets/urban-tileset-2.tileset.json"))));
        return reg;
    }

    /** A 4-neighbor-mask picker (the {@code TileManifest.pickXxxTile} shape) for cross-checking. */
    private interface MaskPicker {
        TileManifest.TileFrame pick(boolean n, boolean s, boolean e, boolean w);
    }

    private static void assertMatchesPicker(GridBlockDef block, MaskPicker picker, String label) {
        for (int m = 0; m < 16; m++) {
            boolean n = (m & 1) != 0, s = (m & 2) != 0, e = (m & 4) != 0, w = (m & 8) != 0;
            int[] got = block.resolve(n, s, e, w);
            TileManifest.TileFrame want = picker.pick(n, s, e, w);
            if (want == null) {
                assertNull(got, label + " mask " + m + " should be null/fill");
            } else {
                assertNotNull(got, label + " mask " + m);
                assertEquals(want.col, got[0], label + " col, mask " + m);
                assertEquals(want.row, got[1], label + " row, mask " + m);
            }
        }
    }

    @Test
    void urban2AutotileBlocksMatchPickers() throws Exception {
        TileRegistry reg = loadUrban2();
        assertMatchesPicker(reg.block("road.road"),      TileManifest::pickRoadTile,      "road.road");
        assertMatchesPicker(reg.block("road.courtyard"), TileManifest::pickCourtyardTile, "road.courtyard");
        assertMatchesPicker(reg.block("road.striped"),   TileManifest::pickStripedTile,   "road.striped");
        assertEquals(Integer.valueOf(TileManifest.ROAD_FILL_RGB), reg.block("road.road").fillRgb);
        assertEquals(Integer.valueOf(TileManifest.COURTYARD_FILL_RGB), reg.block("road.courtyard").fillRgb);
        assertNull(reg.block("road.striped").fillRgb, "striped never returns the null case");
    }

    @Test
    void urban2SingleBlocksMatch() throws Exception {
        TileRegistry reg = loadUrban2();
        int[] tile = reg.block("road.tile").resolve(false, false, false, false);
        assertEquals(TileManifest.pickTileGroundTile(0, 0).col, tile[0]);
        assertEquals(TileManifest.pickTileGroundTile(0, 0).row, tile[1]);

        int[] sidewalk = reg.block("road.sidewalk").resolve(false, false, false, false);
        assertEquals(TileManifest.SIDEWALK.col, sidewalk[0]);
        assertEquals(TileManifest.SIDEWALK.row, sidewalk[1]);

        int[] lz = reg.block("road.lz-marker").resolve(false, false, false, false);
        assertEquals(TileManifest.pickLzMarkerTile().col, lz[0]);
        assertEquals(TileManifest.pickLzMarkerTile().row, lz[1]);
    }
}
