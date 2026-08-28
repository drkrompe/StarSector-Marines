package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
            assertTrue(document.gridCell > 0, seed.getFileName() + " has no source grid size");
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
}
