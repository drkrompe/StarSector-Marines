package com.dillon.starsectormarines.battle.world.tiles;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins {@link TileRegistry#cellLabel} — the dev-viewer annotation lookup that
 * replaced the per-sheet {@code .catalog.json} sidecars once they were folded
 * into each {@code .tileset.json} {@code "cells"} array.
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
    void unlabelledCellsReturnNull() throws Exception {
        TileRegistry reg = loadAll();
        assertNull(reg.cellLabel("graphics/tilesets/urban-tileset.png", 99, 99), "out-of-range grid cell");
        assertNull(reg.cellLabel("graphics/tilesets/nature-tiles.png", 99, 0), "out-of-range sliced frame");
        // A sliced fallback only fires on row 0 (a strip has no other rows).
        assertNull(reg.cellLabel("graphics/tilesets/nature-tiles.png", 0, 3), "sliced lookup off row 0");
    }
}
