package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The project's sheets should be found, not remembered. */
class TilesetLibraryTest {

    private static Path sourceDir(Path root) throws Exception {
        Path dir = root.resolve(TilesetLibrary.SOURCE_DIR);
        Files.createDirectories(dir);
        return dir;
    }

    private static void rawSheet(Path root, String fileName) throws Exception {
        Files.write(sourceDir(root).resolve(fileName), new byte[]{1, 2, 3});
    }

    private static void document(Path root, String name, int pieces) throws Exception {
        TilesetDocument doc = new TilesetDocument();
        doc.sheet = TilesetLibrary.SOURCE_DIR + "/" + name + ".raw.png";
        doc.sheetName = name;
        for (int i = 0; i < pieces; i++) {
            doc.entries.add(new TilesetExport.Entry(
                    new SheetSlicer.Piece(i * 10, 0, 10, 10), name + ".piece-" + i));
        }
        doc.write(TilesetDocument.pathFor(root, name));
    }

    private static void exported(Path root, String name) throws Exception {
        Path dir = root.resolve(TilesetLibrary.EXPORT_DIR);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name + ".tileset.json"),
                "{\"sheet\":\"graphics/x.png\",\"cellPx\":32,\"doodads\":[]}",
                StandardCharsets.UTF_8);
    }

    private static TilesetLibrary.Sheet named(List<TilesetLibrary.Sheet> sheets, String name) {
        return sheets.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void anEmptyProjectListsNothingRatherThanFailing(@TempDir Path root) {
        assertEquals(List.of(), TilesetLibrary.scan(root),
                "a project with no art-source directory is not an error");
    }

    @Test
    void aRawSheetWithNoDocumentIsWorkNotYetStarted(@TempDir Path root) throws Exception {
        rawSheet(root, "hangar.raw.png");

        TilesetLibrary.Sheet sheet = named(TilesetLibrary.scan(root), "hangar");

        assertNotNull(sheet.rawSheet());
        assertNull(sheet.document());
        assertFalse(sheet.isAnnotated());
        assertEquals("raw — not annotated", sheet.status());
    }

    @Test
    void theRawMarkerDoesNotBecomePartOfTheName(@TempDir Path root) throws Exception {
        // urban-tileset.raw.png must pair with urban-tileset.tileset.json.
        rawSheet(root, "urban-tileset.raw.png");
        exported(root, "urban-tileset");

        List<TilesetLibrary.Sheet> sheets = TilesetLibrary.scan(root);

        assertEquals(1, sheets.size(), "the raw art and its export are one sheet: " + sheets);
        assertEquals("urban-tileset", sheets.get(0).name());
        assertTrue(sheets.get(0).isExported());
    }

    @Test
    void aDocumentWithNoPiecesIsSeededRatherThanBroken(@TempDir Path root) throws Exception {
        // What a person or a model writes to set a sheet up: the sheet, the
        // prefix, the slice settings, and no pieces.
        rawSheet(root, "reactor.raw.png");
        Files.writeString(TilesetDocument.pathFor(root, "reactor"),
                "{\"sheet\":\"" + TilesetLibrary.SOURCE_DIR + "/reactor.raw.png\","
                        + "\"sheetName\":\"reactor\",\"idPrefix\":\"doodad.reactor\","
                        + "\"cellPx\":64,\"alphaMin\":40,\"gridCell\":104}",
                StandardCharsets.UTF_8);

        TilesetLibrary.Sheet sheet = named(TilesetLibrary.scan(root), "reactor");

        assertTrue(sheet.isAnnotated(), "a seeded document is still a document");
        assertTrue(sheet.isSeeded());
        assertEquals("seeded — slice to begin", sheet.status());
    }

    @Test
    void anAnnotatedSheetReportsItsProgressAndWhetherItShipped(@TempDir Path root) throws Exception {
        rawSheet(root, "ship.raw.png");
        document(root, "ship", 12);

        assertEquals("12 pieces — not exported", named(TilesetLibrary.scan(root), "ship").status());

        exported(root, "ship");
        assertEquals("12 pieces — exported", named(TilesetLibrary.scan(root), "ship").status());
    }

    @Test
    void aShippedTilesetWithNoRawArtIsListedAndSaysSo(@TempDir Path root) throws Exception {
        // The hand-written tilesets predate this tool; they are part of the
        // inventory even though there is nothing here to annotate.
        exported(root, "legacy-urban");

        TilesetLibrary.Sheet sheet = named(TilesetLibrary.scan(root), "legacy-urban");

        assertNull(sheet.rawSheet());
        assertNull(sheet.document());
        assertEquals("shipped, but no authoring document", sheet.status());
    }

    @Test
    void unstartedWorkSortsAboveFinishedWork(@TempDir Path root) throws Exception {
        rawSheet(root, "done.raw.png");
        document(root, "done", 9);
        exported(root, "done");
        rawSheet(root, "seeded.raw.png");
        Files.writeString(TilesetDocument.pathFor(root, "seeded"),
                "{\"sheet\":\"" + TilesetLibrary.SOURCE_DIR + "/seeded.raw.png\"}",
                StandardCharsets.UTF_8);
        rawSheet(root, "untouched.raw.png");
        exported(root, "legacy");

        List<String> order = TilesetLibrary.scan(root).stream().map(TilesetLibrary.Sheet::name).toList();

        assertEquals(List.of("untouched", "seeded", "done", "legacy"), order,
                "the listing is also the queue: unstarted first, unopenable last");
    }

    @Test
    void aSheetThatAlreadyShipsDoesNotJumpTheQueue(@TempDir Path root) throws Exception {
        // Every hand-written tileset is raw art with no document. They are
        // adoptable, not urgent, and must not bury genuinely new art.
        rawSheet(root, "urban-tileset.raw.png");
        exported(root, "urban-tileset");
        rawSheet(root, "new-hangar.raw.png");

        List<String> order = TilesetLibrary.scan(root).stream()
                .map(TilesetLibrary.Sheet::name).toList();

        assertEquals(List.of("new-hangar", "urban-tileset"), order);
    }

    @Test
    void anUnreadableDocumentDoesNotHideTheRestOfTheLibrary(@TempDir Path root) throws Exception {
        rawSheet(root, "good.raw.png");
        document(root, "good", 3);
        Files.writeString(TilesetDocument.pathFor(root, "broken"), "{ not json",
                StandardCharsets.UTF_8);

        List<TilesetLibrary.Sheet> sheets = TilesetLibrary.scan(root);

        assertEquals(2, sheets.size(), "the broken sheet is listed, not skipped: " + sheets);
        assertEquals(3, named(sheets, "good").annotatedPieces());
        assertTrue(named(sheets, "broken").isAnnotated(),
                "it has a document; opening it is where the failure belongs");
    }
}
