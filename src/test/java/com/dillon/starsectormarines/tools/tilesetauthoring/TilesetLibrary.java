package com.dillon.starsectormarines.tools.tilesetauthoring;

import org.json.JSONException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Every tileset in the project, found rather than browsed for.
 *
 * <p>A sheet exists in up to three places at once — the raw art, the authoring
 * document that annotates it, and the exported tileset the game loads — and
 * knowing where a given sheet has got to means checking all three. Making the
 * operator do that with a file chooser turns "what still needs annotating?"
 * into an act of memory.
 *
 * <p>So the library pairs them up by name and reports the state of each sheet.
 * The listing is also the ingestion queue: a raw sheet with no document is work
 * not yet started, and it sorts to the top.
 *
 * <p>A document with no entries is a legitimate starting point rather than a
 * broken file. It is the natural thing for a person or a model to write when
 * setting a sheet up — the sheet path, the id prefix, the slice parameters —
 * leaving the pieces to be found by slicing. Such a sheet is reported as
 * {@link Sheet#isSeeded() seeded}.
 */
public final class TilesetLibrary {

    private TilesetLibrary() {}

    /** Where raw sheets and their authoring documents live, relative to the project root. */
    public static final String SOURCE_DIR = "art-source/tilesets";
    /** Where exported tilesets live, relative to the project root. */
    public static final String EXPORT_DIR = "mod/data/tilesets";

    private static final String DOCUMENT_SUFFIX = ".tileset-authoring.json";
    private static final String EXPORT_SUFFIX = ".tileset.json";

    /**
     * One sheet and everything the project holds about it.
     *
     * @param name the base name shared by the art, the document and the export
     * @param rawSheet the raw art, or null when only an export exists
     * @param document the authoring document, or null when the sheet is unannotated
     * @param exportedTileset the tileset the game loads, or null when never exported
     * @param annotatedPieces how many pieces the document describes
     */
    public record Sheet(String name, Path rawSheet, Path document, Path exportedTileset,
                        int annotatedPieces) {

        public boolean isAnnotated() {
            return document != null;
        }

        public boolean isExported() {
            return exportedTileset != null;
        }

        /** A document that names its sheet and its slice settings but no pieces yet. */
        public boolean isSeeded() {
            return document != null && annotatedPieces == 0;
        }

        /** What still has to happen to this sheet, in the operator's terms. */
        public String status() {
            if (document == null) {
                return exportedTileset == null ? "raw — not annotated"
                        : "shipped, but no authoring document";
            }
            if (annotatedPieces == 0) return "seeded — slice to begin";
            return annotatedPieces + " pieces" + (exportedTileset == null ? " — not exported" : " — exported");
        }

        @Override
        public String toString() {
            return name + "  (" + status() + ")";
        }
    }

    /**
     * Every sheet the project knows about, work-to-do first.
     *
     * <p>Never throws for a malformed document: one unreadable file must not
     * hide the rest of the library, and the failure is more useful when it
     * arrives on opening that sheet than on listing all of them.
     */
    public static List<Sheet> scan(Path projectRoot) {
        Path sourceDir = projectRoot.resolve(SOURCE_DIR);
        Path exportDir = projectRoot.resolve(EXPORT_DIR);

        Map<String, Path> raw = new LinkedHashMap<>();
        Map<String, Path> documents = new LinkedHashMap<>();
        for (Path path : list(sourceDir)) {
            String file = path.getFileName().toString();
            if (file.endsWith(DOCUMENT_SUFFIX)) {
                documents.put(file.substring(0, file.length() - DOCUMENT_SUFFIX.length()), path);
            } else if (file.toLowerCase(Locale.ROOT).endsWith(".png")) {
                raw.put(baseName(file), path);
            }
        }
        Map<String, Path> exports = new LinkedHashMap<>();
        for (Path path : list(exportDir)) {
            String file = path.getFileName().toString();
            if (file.endsWith(EXPORT_SUFFIX)) {
                exports.put(file.substring(0, file.length() - EXPORT_SUFFIX.length()), path);
            }
        }

        List<String> names = new ArrayList<>();
        for (String name : raw.keySet()) if (!names.contains(name)) names.add(name);
        for (String name : documents.keySet()) if (!names.contains(name)) names.add(name);
        for (String name : exports.keySet()) if (!names.contains(name)) names.add(name);

        List<Sheet> sheets = new ArrayList<>();
        for (String name : names) {
            Path document = documents.get(name);
            sheets.add(new Sheet(name, raw.get(name), document, exports.get(name),
                    document == null ? 0 : pieceCount(document)));
        }
        // Unannotated raw art first: it is the work that has not started.
        sheets.sort(Comparator.comparingInt(TilesetLibrary::rank)
                .thenComparing(Sheet::name, String.CASE_INSENSITIVE_ORDER));
        return sheets;
    }

    /**
     * Queue position: what most needs attention first.
     *
     * <p>A sheet that already ships without ever having been annotated here is
     * not urgent — the hand-written tilesets are all in that state, and putting
     * them above genuinely new art would bury the thing the list is for.
     */
    private static int rank(Sheet sheet) {
        if (sheet.rawSheet() == null) return 5;             // nothing to open
        if (!sheet.isAnnotated()) return sheet.isExported() ? 4 : 0;
        if (sheet.isSeeded()) return 1;
        return sheet.isExported() ? 3 : 2;
    }

    /**
     * The raw sheet's base name, with the {@code .raw} marker removed so
     * {@code urban-tileset.raw.png} pairs with {@code urban-tileset.tileset.json}.
     */
    private static String baseName(String file) {
        String name = file.substring(0, file.length() - ".png".length());
        return name.endsWith(".raw") ? name.substring(0, name.length() - ".raw".length()) : name;
    }

    private static int pieceCount(Path document) {
        try {
            return TilesetDocument.read(document).entries.size();
        } catch (IOException | JSONException | RuntimeException unreadable) {
            return 0;
        }
    }

    private static List<Path> list(Path directory) {
        if (!Files.isDirectory(directory)) return List.of();
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException unreadable) {
            return List.of();
        }
    }
}
