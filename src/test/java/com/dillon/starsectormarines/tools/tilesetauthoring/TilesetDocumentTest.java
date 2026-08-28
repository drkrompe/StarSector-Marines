package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An annotation pass has to survive being put down, and survive a re-slice. */
class TilesetDocumentTest {

    private static TilesetExport.Entry entry(String id, int x, int y, int w, int h) {
        return new TilesetExport.Entry(new SheetSlicer.Piece(x, y, w, h), id);
    }

    private static TilesetDocument document() {
        TilesetDocument doc = new TilesetDocument();
        doc.sheet = "art-source/tilesets/ship.raw.png";
        doc.sheetName = "ship";
        doc.idPrefix = "doodad.ship";
        doc.cellPx = 64;
        doc.alphaMin = 55;
        doc.gridCols = 12;
        doc.gridRows = 4;

        TilesetExport.Entry console = entry("doodad.ship.console", 10, 10, 200, 100);
        console.footprintX = 2;
        console.cover = "med";
        TilesetExport.Entry scrap = entry("doodad.ship.scrap", 300, 10, 90, 90);
        scrap.included = false;

        doc.entries = new ArrayList<>(List.of(console, scrap));
        return doc;
    }

    @Test
    void savedDocumentReopensWithEveryDecisionIntact(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("ship.tileset-authoring.json");
        document().write(path);

        TilesetDocument reopened = TilesetDocument.read(path);

        assertEquals("art-source/tilesets/ship.raw.png", reopened.sheet);
        assertEquals("doodad.ship", reopened.idPrefix);
        assertEquals(64, reopened.cellPx);
        assertEquals(55, reopened.alphaMin, "the threshold that found these pieces is part of the pass");
        assertEquals(12, reopened.gridCols);
        assertEquals(4, reopened.gridRows);
        assertEquals(2, reopened.entries.size());

        TilesetExport.Entry console = reopened.entries.get(0);
        assertEquals("doodad.ship.console", console.id);
        assertEquals(new SheetSlicer.Piece(10, 10, 200, 100), console.piece);
        assertEquals(2, console.footprintX);
        assertEquals(1, console.footprintY);
        assertEquals("med", console.cover);
        assertTrue(console.included);

        assertFalse(reopened.entries.get(1).included, "an excluded piece stays excluded");
    }

    @Test
    void aMeasuredPlacementIsSavedSoTheCutCanBeReproduced(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("ship.tileset-authoring.json");
        TilesetDocument doc = document();
        doc.setCut(doc.cut(1254, 1254).withColumnAxis(8.5, 123.23));
        doc.write(path);

        TilesetDocument reopened = TilesetDocument.read(path);

        assertEquals(12, reopened.gridCols, "a measurement never revises a stated count");
        assertEquals(4, reopened.gridRows);
        GridCut cut = reopened.cut(1254, 1254);
        assertEquals(8.5, cut.originX());
        assertEquals(123.23, cut.pitchX());
        assertEquals(reopened.cut(1254, 1254).cell(3, 1), cut.cell(3, 1),
                "the same document should describe the same cells every time it is read");
    }

    @Test
    void aDocumentThatHasNotBeenMeasuredStillDescribesACut(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("ship.tileset-authoring.json");
        document().write(path);

        GridCut cut = TilesetDocument.read(path).cut(1200, 400);

        assertTrue(cut.isDivisionOf(1200, 400),
                "a stated layout alone can only say the canvas is divided, and it should say "
                        + "exactly that rather than inventing a placement");
        assertEquals(100.0, cut.pitchX());
    }

    @Test
    void writeReplacesAtomicallyAndLeavesNoTemporary(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("nested/ship.tileset-authoring.json");
        document().write(path);
        TilesetDocument second = document();
        second.idPrefix = "doodad.ship2";
        second.write(path);

        assertEquals("doodad.ship2", TilesetDocument.read(path).idPrefix);
        try (var listing = Files.list(path.getParent())) {
            assertEquals(List.of(path.getFileName().toString()),
                    listing.map(p -> p.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    void reSlicingCarriesAnnotationsOntoTheShiftedPieces() {
        TilesetExport.Entry console = entry("doodad.ship.console", 10, 10, 200, 100);
        console.footprintX = 2;
        console.cover = "heavy";
        TilesetExport.Entry crate = entry("doodad.ship.crate", 300, 10, 90, 90);
        crate.included = false;

        // A higher alpha threshold trims a few pixels off every box.
        List<SheetSlicer.Piece> resliced = List.of(
                new SheetSlicer.Piece(12, 12, 196, 96),
                new SheetSlicer.Piece(302, 12, 86, 86));

        TilesetDocument.Reconciliation result = TilesetDocument.reconcile(
                resliced, new ArrayList<>(List.of(console, crate)), "doodad.ship", 104, 104);

        assertEquals(2, result.carried());
        assertEquals(0, result.added());
        assertTrue(result.lost().isEmpty());
        assertEquals(List.of("doodad.ship.console", "doodad.ship.crate"),
                result.entries().stream().map(e -> e.id).toList());
        assertEquals(2, result.entries().get(0).footprintX);
        assertEquals("heavy", result.entries().get(0).cover);
        assertFalse(result.entries().get(1).included);
        assertEquals(new SheetSlicer.Piece(12, 12, 196, 96), result.entries().get(0).piece,
                "the annotation moves onto the newly found bounds");
    }

    @Test
    void anAppearedPieceIsNewAndAVanishedOneIsReported() {
        TilesetExport.Entry kept = entry("doodad.ship.console", 10, 10, 200, 100);
        kept.cover = "med";
        TilesetExport.Entry gone = entry("doodad.ship.speck", 500, 500, 30, 30);

        List<SheetSlicer.Piece> resliced = List.of(
                new SheetSlicer.Piece(10, 10, 200, 100),
                new SheetSlicer.Piece(300, 10, 104, 104));

        TilesetDocument.Reconciliation result = TilesetDocument.reconcile(
                resliced, new ArrayList<>(List.of(kept, gone)), "doodad.ship", 104, 104);

        assertEquals(1, result.carried());
        assertEquals(1, result.added());
        assertEquals(List.of("doodad.ship.speck"), result.lostIds(),
                "a piece that no longer exists is named, not silently dropped");
        assertTrue(result.summary().contains("doodad.ship.speck"));

        TilesetExport.Entry appeared = result.entries().get(1);
        assertEquals(1, appeared.footprintX, "a 104px piece on a 104px grid is one cell");
        assertEquals("none", appeared.cover);
        assertNotEquals(kept.id, appeared.id);
    }

    @Test
    void aGeneratedIdNeverCollidesWithACarriedOne() {
        // The carried annotation already holds the ordinal id the new piece would take.
        TilesetExport.Entry carried = entry("doodad.ship.piece-000", 300, 10, 90, 90);

        List<SheetSlicer.Piece> resliced = List.of(
                new SheetSlicer.Piece(10, 10, 90, 90),
                new SheetSlicer.Piece(300, 10, 90, 90));

        TilesetDocument.Reconciliation result = TilesetDocument.reconcile(
                resliced, new ArrayList<>(List.of(carried)), "doodad.ship", 104, 104);

        List<String> ids = result.entries().stream().map(e -> e.id).toList();
        assertEquals(2, ids.stream().distinct().count(), "ids stay unique: " + ids);
        assertEquals(List.of("doodad.ship.piece-001", "doodad.ship.piece-000"), ids,
                "the free ordinal steps past the one the carried annotation holds");
    }

    @Test
    void aFusedPieceLosesBothAnnotationsRatherThanInheritingOne() {
        // Lowering the threshold can merge two neighbours into one box. Guessing
        // which of the two it inherits would be worse than saying it is new.
        TilesetExport.Entry left = entry("doodad.ship.left", 10, 10, 90, 90);
        TilesetExport.Entry right = entry("doodad.ship.right", 110, 10, 90, 90);

        List<SheetSlicer.Piece> resliced = List.of(new SheetSlicer.Piece(10, 10, 190, 90));

        TilesetDocument.Reconciliation result = TilesetDocument.reconcile(
                resliced, new ArrayList<>(List.of(left, right)), "doodad.ship", 104, 104);

        assertEquals(0, result.carried());
        assertEquals(1, result.added());
        assertEquals(List.of("doodad.ship.left", "doodad.ship.right"), result.lostIds());
    }

    @Test
    void aSheetNoteSurvivesTheRoundTrip(@TempDir Path dir) throws Exception {
        TilesetDocument doc = document();
        doc.note = "RGB with no alpha; split the single piece on the 125 px grid.";

        Path path = dir.resolve("ship.tileset-authoring.json");
        doc.write(path);

        assertEquals(doc.note, TilesetDocument.read(path).note);
    }

    @Test
    void documentPathIsBesideTheRawSheetNotInsideTheMod() {
        Path path = TilesetDocument.pathFor(Path.of("/project"), "ship");
        String text = path.toString().replace('\\', '/');
        assertTrue(text.endsWith("art-source/tilesets/ship.tileset-authoring.json"), text);
        assertFalse(text.contains("/mod/"), "raw-side authoring never lands in the shipped folder");
    }
}
