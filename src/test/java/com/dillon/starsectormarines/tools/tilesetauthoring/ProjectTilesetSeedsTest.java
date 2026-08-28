package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seeds checked into {@code art-source/tilesets/} are read by a tool the
 * moment someone opens the Tilesets page, so a broken one is discovered by a
 * person mid-task rather than by the build.
 *
 * <p>These assertions are about the project's own content, not about the
 * library's behaviour on synthetic input — {@code TilesetLibraryTest} covers
 * that.
 */
class ProjectTilesetSeedsTest {

    private static final Path PROJECT = Path.of(".");
    private static final Path TILESETS = Path.of(TilesetLibrary.SOURCE_DIR);

    private static List<Path> seeds() throws IOException {
        if (!Files.isDirectory(TILESETS)) return List.of();
        try (Stream<Path> walk = Files.list(TILESETS)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".tileset-authoring.json"))
                    .sorted()
                    .toList();
        }
    }

    @Test
    void everyRawSheetHasAnAuthoringDocument() throws IOException {
        List<String> unseeded = new ArrayList<>();
        for (TilesetLibrary.Sheet sheet : TilesetLibrary.scan(PROJECT)) {
            if (sheet.rawSheet() != null && !sheet.isAnnotated()) unseeded.add(sheet.name());
        }
        assertEquals(List.of(), unseeded,
                "raw art in the project should arrive with a document that says what it is");
    }

    @Test
    void everySeedResolvesToTheSheetItAnnotates() throws Exception {
        for (Path seed : seeds()) {
            TilesetDocument document = TilesetDocument.read(seed);
            Path sheet = PROJECT.resolve(document.sheet).normalize();
            assertTrue(Files.isRegularFile(sheet),
                    seed.getFileName() + " names a sheet that is not there: " + document.sheet);
            assertTrue(document.cellPx > 0, seed.getFileName() + " has no export cell size");
            assertTrue(document.gridCols > 0 && document.gridRows > 0,
                    seed.getFileName() + " has no plate layout");
            assertTrue(document.alphaMin > 0 && document.alphaMin < 255,
                    seed.getFileName() + " has an unusable alpha threshold");
        }
    }

    @Test
    void aSeedWithNoPiecesExplainsItself() throws Exception {
        // Slice settings say how to cut a sheet up but not what is true of it.
        // A document that has not been annotated yet is the only thing standing
        // between the next reader and re-deriving that by failing.
        for (Path seed : seeds()) {
            TilesetDocument document = TilesetDocument.read(seed);
            if (!document.entries.isEmpty()) continue;
            assertFalse(document.note.isBlank(),
                    seed.getFileName() + " is a seed with nothing to say about its sheet");
            // SheetMeasurement drafts a placeholder note on purpose, so that
            // replacing it is a visible step rather than an optional one.
            assertFalse(document.note.contains("TODO"),
                    seed.getFileName() + " still carries the drafted placeholder note; "
                            + "replacing it is the ingest step this stands in for");
        }
    }

    @Test
    void theProjectListsItsSheetsWithoutFailingOnAnyOfThem() throws IOException {
        List<TilesetLibrary.Sheet> sheets = TilesetLibrary.scan(PROJECT);
        assertFalse(sheets.isEmpty(), "the project has tilesets; the library should find them");
        for (TilesetLibrary.Sheet sheet : sheets) {
            assertFalse(sheet.status().isBlank(), sheet.name() + " has no readable state");
        }
        // Nothing in the project should be listed as untouched raw art any more.
        assertEquals(List.of(), sheets.stream()
                        .filter(s -> s.rawSheet() != null && !s.isAnnotated())
                        .map(TilesetLibrary.Sheet::name).toList(),
                "the queue should be seeded");
    }

    /**
     * Nothing a checked-in document says goes missing when it is written back.
     *
     * <p>This is the round-trip law asked of the project's own content rather
     * than of an example. A field the model cannot hold does not fail anything
     * when it is read — it simply is not there when the document is next
     * written, and every tool that touches a sheet rewrites the whole document.
     * What goes missing that way is exactly the part nothing at runtime reads
     * and therefore nothing at runtime misses: a description, a layer, the fact
     * that a sheet is a strip at all.
     *
     * <p>Stated as "nothing is lost" rather than "nothing changes", because the
     * writer legitimately adds: a hand-written seed says nothing about blocks
     * and comes back with an empty list of them. An addition is not a loss, and
     * requiring byte equality would make every hand-written seed illegal.
     */
    @Test
    void everyDocumentSurvivesBeingReadAndWrittenBack() throws Exception {
        for (Path seed : seeds()) {
            JSONObject onDisk = new JSONObject(Files.readString(seed, StandardCharsets.UTF_8));
            JSONObject rewritten = TilesetDocument.fromJson(onDisk).toJson();
            List<String> lost = new ArrayList<>();
            collectLosses(onDisk, rewritten, "", lost);
            assertEquals(List.of(), lost, seed.getFileName()
                    + " does not survive a read and a write: opening the sheet and saving it "
                    + "would drop or change what is listed here");
        }
    }

    /** Every value the document states, that a rewrite does not state the same way. */
    private static void collectLosses(Object before, Object after, String at, List<String> lost)
            throws JSONException {
        if (before instanceof JSONObject object) {
            if (!(after instanceof JSONObject written)) {
                lost.add(at + " is no longer an object");
                return;
            }
            for (Iterator<String> it = object.keys(); it.hasNext(); ) {
                String key = it.next();
                String path = at.isEmpty() ? key : at + "." + key;
                if (!written.has(key)) {
                    lost.add(path);
                    continue;
                }
                collectLosses(object.get(key), written.get(key), path, lost);
            }
            return;
        }
        if (before instanceof JSONArray array) {
            if (!(after instanceof JSONArray written) || written.length() != array.length()) {
                lost.add(at + " changed length");
                return;
            }
            for (int i = 0; i < array.length(); i++) {
                collectLosses(array.get(i), written.get(i), at + "[" + i + "]", lost);
            }
            return;
        }
        // Numerically, because JSON does not distinguish 0 from 0.0 and the
        // reader gives a measured origin a double whichever way it was written.
        if (before instanceof Number left && after instanceof Number right) {
            if (left.doubleValue() != right.doubleValue()) {
                lost.add(at + ": " + before + " -> " + after);
            }
            return;
        }
        if (!String.valueOf(before).equals(String.valueOf(after))) {
            lost.add(at + ": " + before + " -> " + after);
        }
    }
}
