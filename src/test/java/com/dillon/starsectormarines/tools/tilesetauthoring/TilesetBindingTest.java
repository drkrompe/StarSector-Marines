package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.json.JSONObject;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A candidate binding is an authoring decision that has to survive being put
 * down, and must never reach the exported tileset: a tileset describes what
 * content is, not what some other content might have been.
 */
class TilesetBindingTest {

    private static TilesetExport.Entry piece(String id, int index) {
        TilesetExport.Entry entry = new TilesetExport.Entry(
                new SheetSlicer.Piece(index * 32, 0, 32, 32), id);
        entry.cover = "med";
        return entry;
    }

    @Test
    void aBindingSurvivesSavingAndReopening(@TempDir Path dir) throws Exception {
        TilesetDocument doc = new TilesetDocument();
        doc.sheet = "art-source/tilesets/ship.raw.png";
        doc.sheetName = "ship";
        TilesetExport.Entry crate = piece("doodad.ship.crate", 0);
        crate.standsInFor = "doodad.crate";
        doc.entries = new ArrayList<>(List.of(crate, piece("doodad.ship.desk", 1)));

        Path path = dir.resolve("ship.tileset-authoring.json");
        doc.write(path);
        TilesetDocument reopened = TilesetDocument.read(path);

        assertEquals("doodad.crate", reopened.entries.get(0).standsInFor);
        assertEquals("", reopened.entries.get(1).standsInFor,
                "an unbound piece stays unbound");
    }

    @Test
    void aBindingIsNeverExportedIntoTheTileset() throws Exception {
        TilesetExport.Entry crate = piece("doodad.ship.crate", 0);
        crate.standsInFor = "doodad.crate";

        JSONObject tileset = TilesetExport.tileset(
                "graphics/doodads/ship.png", 32, List.of(crate), List.of());

        assertFalse(tileset.toString().contains("standsInFor"), tileset.toString());
        assertFalse(tileset.toString().contains("doodad.crate\""),
                "the shipped id must not leak into the authored sheet: " + tileset);
        assertEquals("doodad.ship.crate",
                tileset.getJSONArray("doodads").getJSONObject(0).getString("id"));
    }

    @Test
    void theComparisonImageShowsBothMapsSideBySide() {
        BufferedImage left = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        BufferedImage right = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);

        BufferedImage composed = TilesetMapPanel.compose(
                new TilesetMapPreview.Result(left, right, List.of()));

        assertTrue(composed.getWidth() >= left.getWidth() + right.getWidth(),
                "both maps have to fit: " + composed.getWidth());
        assertTrue(composed.getHeight() > left.getHeight(), "room for the captions");
    }
}
