package com.dillon.starsectormarines.tools.tilesetauthoring;

import com.dillon.starsectormarines.battle.world.tiles.CellLabel;
import com.dillon.starsectormarines.battle.world.tiles.GridLayout;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a piece is <em>for</em> has to survive export and be readable without
 * opening the atlas — mostly by an LLM assembling a map, which cannot open it
 * at all.
 */
class TilesetHintsTest {

    private static final String SHEET = "graphics/doodads/ship.png";
    private static final int CELL = 32;

    private static TilesetExport.Entry doodad(String id, int index, String note, String... tags) {
        TilesetExport.Entry entry = new TilesetExport.Entry(
                new SheetSlicer.Piece(index * CELL, 0, CELL, CELL), id);
        entry.cover = "med";
        entry.note = note;
        entry.tags = new ArrayList<>(List.of(tags));
        return entry;
    }

    @Test
    void theHintReachesTheRegistryAsADescriptiveCellLabel() throws Exception {
        List<TilesetExport.Entry> entries = List.of(
                doodad("doodad.ship.console", 0, "Bridge console; reads as crew station.",
                        "interior", "military"));

        JSONObject tileset = TilesetExport.tileset(SHEET, CELL, entries, List.of());
        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(tileset);
        registry.validateReferences();

        CellLabel label = registry.cellLabel(SHEET, 0, 0);
        assertEquals("doodad.ship.console", label.name);
        assertEquals("Bridge console; reads as crew station.", label.description);
        assertEquals(List.of("interior", "military"), label.tags);
    }

    @Test
    void anUnannotatedBlockCellStillSaysWhichFacingItIs() throws Exception {
        List<TilesetExport.Entry> entries = new ArrayList<>();
        for (String slot : BlockSlots.of(GridLayout.WALL_3X3)) {
            TilesetExport.Entry entry = doodad("unused", entries.size(), "");
            entry.blockId = "ship.wall";
            entry.slot = slot;
            entries.add(entry);
        }
        List<TilesetExport.BlockSpec> blocks =
                List.of(new TilesetExport.BlockSpec("ship.wall", GridLayout.WALL_3X3, 0x060A10));

        JSONObject tileset = TilesetExport.tileset(SHEET, CELL, entries, blocks);
        TileRegistry registry = new TileRegistry();
        registry.ingestSheet(tileset);
        registry.validateReferences();

        CellLabel northEast = registry.cellLabel(SHEET, 2, 0);
        assertEquals("ship.wall ne", northEast.name);
        assertEquals("exterior on the north and east", northEast.description,
                "the label states the mask the way the layout reads it");
    }

    @Test
    void anExcludedPieceIsNotDescribedInTheSheetItIsNotIn() throws Exception {
        TilesetExport.Entry kept = doodad("doodad.ship.console", 0, "kept");
        TilesetExport.Entry dropped = doodad("doodad.ship.scrap", 1, "dropped");
        dropped.included = false;

        JSONObject tileset = TilesetExport.tileset(SHEET, CELL, List.of(kept, dropped), List.of());

        assertEquals(1, tileset.getJSONArray("cells").length());
        assertEquals("doodad.ship.console",
                tileset.getJSONArray("cells").getJSONObject(0).getString("name"));
    }

    @Test
    void theAnnotationSurvivesSavingAndReopeningTheDocument(@TempDir Path dir) throws Exception {
        TilesetDocument doc = new TilesetDocument();
        doc.sheet = "art-source/tilesets/ship.raw.png";
        doc.sheetName = "ship";
        doc.entries = new ArrayList<>(List.of(
                doodad("doodad.ship.console", 0, "Bridge console.", "interior", "military")));

        Path path = dir.resolve("ship.tileset-authoring.json");
        doc.write(path);
        TilesetDocument reopened = TilesetDocument.read(path);

        assertEquals("Bridge console.", reopened.entries.get(0).note);
        assertEquals(List.of("interior", "military"), reopened.entries.get(0).tags);
    }

    @Test
    void theCatalogCardNamesEveryIncludedIdAndNoExcludedOne() {
        TilesetExport.Entry console = doodad("doodad.ship.console", 0,
                "Bridge console; reads as a crew station.", "interior", "military");
        console.footprintX = 2;
        TilesetExport.Entry scrap = doodad("doodad.ship.scrap", 1, "cut — duplicate of the crate");
        scrap.included = false;
        TilesetExport.Entry wall = doodad("unused", 2, "Exterior hull plating.", "exterior");
        wall.blockId = "ship.wall";
        wall.slot = "nw";

        List<TilesetExport.Entry> entries = List.of(console, scrap, wall);
        List<TilesetExport.BlockSpec> blocks =
                List.of(new TilesetExport.BlockSpec("ship.wall", GridLayout.WALL_3X3, 0x060A10));

        String card = TilesetCatalogCard.render("ship", SHEET, CELL, entries, blocks);

        assertTrue(card.contains("`doodad.ship.console`"), card);
        assertTrue(card.contains("Bridge console; reads as a crew station."), card);
        assertTrue(card.contains("interior, military"), card);
        assertTrue(card.contains("2x1"), "the card states how much deck a piece covers");
        assertTrue(card.contains("`ship.wall`"), "a block is listed as one usable thing");
        assertTrue(card.contains("wall-3x3"), card);
        assertTrue(card.contains("Exterior hull plating."),
                "a block described on one of its cells is described on the card");

        assertFalse(card.contains("doodad.ship.scrap"),
                "an excluded piece is not in the sheet, so it is not in its catalog");
        assertFalse(card.contains("unused"),
                "a block member has no id of its own to advertise");
    }

    @Test
    void aNoteContainingAPipeDoesNotBreakItsRow() {
        TilesetExport.Entry entry = doodad("doodad.ship.pipe", 0, "runs N|S along the bulkhead");
        String card = TilesetCatalogCard.render("ship", SHEET, CELL, List.of(entry), List.of());

        // Measured against the header rather than against a number, because the
        // property is "an escaped pipe adds no cell" and the doodad table gains
        // a column whenever a doodad gains a field worth publishing.
        int columns = -1;
        for (String line : card.split("\n")) {
            if (line.startsWith("| id |")) columns = line.split("(?<!\\\\)\\|", -1).length;
            if (!line.contains("doodad.ship.pipe")) continue;
            assertEquals(columns, line.split("(?<!\\\\)\\|", -1).length,
                    "the row keeps exactly the header's cells: " + line);
            return;
        }
        throw new AssertionError("the piece is missing from the card:\n" + card);
    }
}
