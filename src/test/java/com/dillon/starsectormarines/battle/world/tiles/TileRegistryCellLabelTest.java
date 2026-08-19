package com.dillon.starsectormarines.battle.world.tiles;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link TileRegistry#cellLabel} — the dev-viewer annotation lookup that
 * replaced the per-sheet {@code .catalog.json} sidecars once they were folded
 * into each {@code .tileset.json} {@code "cells"} array. Covers both resolution
 * paths: grid sheets read the folded-in cells; sliced sheets fall back to the
 * tile whose {@link TileDef#frame} equals the column.
 */
public class TileRegistryCellLabelTest {

    private static TileRegistry loadAll() throws Exception {
        TileRegistry reg = new TileRegistry();
        for (String path : TileRegistry.BUILTIN_TILESETS) {
            reg.ingestSheet(new JSONObject(Files.readString(Paths.get("mod", path.split("/")))));
        }
        reg.validateReferences();
        return reg;
    }

    @Test
    void gridSheetsResolveFoldedInCells() throws Exception {
        TileRegistry reg = loadAll();
        assertEquals("chair-south-yellow", reg.cellLabel("graphics/tilesets/urban-tileset.png", 6, 1).name);
        assertEquals("road-nw", reg.cellLabel("graphics/tilesets/urban-tileset-2.png", 12, 0).name);
        assertEquals("grass-1", reg.cellLabel("graphics/tilesets/Floors_Tiles.png", 1, 10).name);
        assertEquals("water-edge-s", reg.cellLabel("graphics/tilesets/Water_tiles.png", 8, 0).name);
    }

    @Test
    void slicedSheetsFallBackToTileNameByFrame() throws Exception {
        TileRegistry reg = loadAll();
        // nature: frame 0 is nature.grass-1 (name "grass"); row is always 0 for a strip.
        assertEquals("grass", reg.cellLabel("graphics/tilesets/nature-tiles.png", 0, 0).name);

        // urban-3: frame 5 is the south-facing bench, carrying a description too.
        CellLabel bench = reg.cellLabel("graphics/tilesets/urban-tileset-3.png", 5, 0);
        assertNotNull(bench);
        assertEquals("bench-s", bench.name);
        assertTrue(bench.description.startsWith("bench facing south"), bench.description);
    }

    @Test
    void unlabelledCellsReturnNull() throws Exception {
        TileRegistry reg = loadAll();
        assertNull(reg.cellLabel("graphics/tilesets/urban-tileset.png", 99, 99), "out-of-range grid cell");
        assertNull(reg.cellLabel("graphics/tilesets/nature-tiles.png", 99, 0), "out-of-range sliced frame");
        // A sliced fallback only fires on row 0 (a strip has no other rows).
        assertNull(reg.cellLabel("graphics/tilesets/nature-tiles.png", 0, 3), "sliced lookup off row 0");
    }
}
