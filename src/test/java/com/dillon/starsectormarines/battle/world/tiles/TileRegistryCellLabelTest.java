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
        // urban-tileset is generated from its raw sheet and repacked on every
        // export, so its coordinates are the packer's and nothing outside the
        // tileset may hold one. Ask the registry where the chair went.
        DoodadDef chair = reg.doodad("doodad.chair-south-yellow");
        assertNotNull(chair, "urban-tileset no longer defines doodad.chair-south-yellow");
        assertEquals("doodad.chair-south-yellow",
                reg.cellLabel(chair.sheetPath, chair.col, chair.row).name);
        // Same rule for the block sheets: ask the block where its cells are
        // rather than writing a coordinate down. A pinned (col, row) here is the
        // hazard the whole id-addressing law exists for, and it bit exactly once
        // - this line named Water_tiles cell (8, 0) until that sheet started
        // exporting the three cells the game actually asks it for.
        GridBlockDef road = reg.block("road.road");
        assertNotNull(road, "urban-tileset-2 no longer defines road.road");
        assertEquals("road-nw",
                reg.cellLabel(road.sheetPath, road.originCol, road.originRow).name);

        assertEquals("grass-1", firstCellLabel(reg, "floors.grass").name);
        assertEquals("water.water v1", firstCellLabel(reg, "water.water").name);
    }

    /** The label on a variant pool's first cell, wherever the packer put it. */
    private static CellLabel firstCellLabel(TileRegistry reg, String blockId) {
        GridBlockDef block = reg.block(blockId);
        assertNotNull(block, "no block " + blockId);
        assertTrue(block.isVariantPool(), blockId + " is no longer a variant pool");
        return reg.cellLabel(block.sheetPath, block.cells[0][0], block.cells[0][1]);
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
