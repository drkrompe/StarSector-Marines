package com.dillon.starsectormarines.battle.world.tiles;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A sheet's own cell size has to reach the thing that samples it.
 *
 * <p>{@code cellPx} was authored on every doodad sheet and read during ingest,
 * and then went nowhere: the renderer sampled every sheet at the game's grid
 * size regardless. Nothing failed, because every sheet in the mod happened to
 * be drawn at that size — so the only symptom would have been the first finer
 * sheet quietly rendering as a crop of its own corner.
 */
class DoodadSourceCellTest {

    private static DoodadDef ingest(Integer cellPx) throws Exception {
        JSONObject doodad = new JSONObject()
                .put("id", "test.prop")
                .put("col", 2)
                .put("row", 3);
        JSONObject sheet = new JSONObject()
                .put("sheet", "graphics/test/sheet.png")
                .put("doodads", new org.json.JSONArray().put(doodad));
        if (cellPx != null) sheet.put("cellPx", cellPx.intValue());

        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(sheet);
        return registry.doodad("test.prop");
    }

    @Test
    void aSheetIsSampledAtItsOwnCellSize() throws Exception {
        DoodadDef def = ingest(104);
        assertEquals(104, def.sourceCellPx, "sheet cellPx did not reach the doodad def");
        assertEquals(104, new Doodad(0, 0, def).sourceCellPx,
                "sheet cellPx did not reach the placed doodad");
    }

    @Test
    void aSheetThatDoesNotSayFallsBackToTheGameGrid() throws Exception {
        DoodadDef def = ingest(null);
        assertEquals(0, def.sourceCellPx, "an unspecified cell size should stay unspecified");
        assertEquals(TileManifest.TILE_SIZE, new Doodad(0, 0, def).sourceCellPx,
                "an unspecified cell size should render at the game's own grid");
    }
}
