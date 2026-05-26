package com.dillon.starsectormarines.tools.tilesets;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audits the hand-authored tile catalogs in {@code mod/data/tilesets/}.
 * Asserts structural invariants (files exist, parse, entries are well-formed
 * within sheet bounds); prints a per-sheet report to stdout listing duplicate
 * names, naming-convention drift, and any mismatches against {@link
 * com.dillon.starsectormarines.battle.map.TileManifest}'s hand-coded cell
 * references.
 *
 * <p>This is intentionally a tool dressed as a test — run with
 * {@code gradlew test --tests "*TilesetCatalogAuditTest*" -i} to see the full
 * report. The assertions only cover things that would corrupt the in-game
 * loader; everything else is reported, not enforced, because pool tiles (e.g.,
 * {@code chest} appearing twice) are valid even though they duplicate a name.
 */
public class TilesetCatalogAuditTest {

    private static final Path CATALOG_DIR = Paths.get("mod/data/tilesets");

    private static final class SheetMeta {
        final String basename;
        final int tileSize;
        final int cols;
        final int rows;
        SheetMeta(String basename, int tileSize, int cols, int rows) {
            this.basename = basename;
            this.tileSize = tileSize;
            this.cols = cols;
            this.rows = rows;
        }
    }

    // Sheet dimensions match the PNG sizes recorded in the in-game loader:
    //   urban-tileset.png   = 320x320 @ 32px → 10x10
    //   urban-tileset-2.png = 640x320 @ 32px → 20x10  (actually 544x96 — see note in TileManifest)
    //   Floors_Tiles.png    = 400x416 @ 16px → 25x26
    //   Water_tiles.png     = 400x400 @ 16px → 25x25
    // urban-2's exact pixel-W is recomputed at runtime; we only need an upper bound for bounds-checking.
    private static final List<SheetMeta> SHEETS = List.of(
            new SheetMeta("urban-tileset",   32, 10, 10),
            new SheetMeta("urban-tileset-2", 32, 20, 10),
            new SheetMeta("Floors_Tiles",    16, 25, 26),
            new SheetMeta("Water_tiles",     16, 25, 25)
    );

    private static final class Entry {
        final int col, row;
        final String name, description;
        Entry(int col, int row, String name, String description) {
            this.col = col; this.row = row; this.name = name; this.description = description;
        }
        String coord() { return "(" + col + "," + row + ")"; }
    }

    /**
     * Hand-curated cells that {@link com.dillon.starsectormarines.battle.map.TileManifest}
     * pins by absolute {@code (col, row)} — used to flag drift between the
     * art TileManifest was coded against and the art the catalog describes.
     * Constructed from the constants in TileManifest at the time this test
     * was written; update when TileManifest's pinned cells change.
     *
     * <p>{@code placeholder=true} marks refs where TileManifest points at a
     * cell whose visual is a stand-in (e.g., SIDEWALK = plain floor on this
     * sheet because no dedicated sidewalk art exists). Placeholders still
     * "match" the audit when the catalog name lines up with what TileManifest
     * is currently pointing at — the audit's job is to catch coordinate drift,
     * and the placeholder annotation calls out that the visual is a known
     * compromise.
     */
    private static final List<TileManifestRef> TILE_MANIFEST_REFS = List.of(
            new TileManifestRef("urban-tileset",    7, 2, "DOOR_OPEN",                      "door-open",  false),
            new TileManifestRef("urban-tileset",    6, 2, "(implicit closed-door doodad)",  "door-closed", false),
            new TileManifestRef("urban-tileset-2", 12, 0, "ROAD origin (12..14, 0..2)",     "road-nw",    false),
            new TileManifestRef("urban-tileset-2", 11, 1, "SIDEWALK (placeholder)",         "fl-3",       true),
            new TileManifestRef("urban-tileset-2", 16, 2, "LZ_PAD (placeholder)",           "grate-2",    true),
            new TileManifestRef("urban-tileset-2",  0, 0, "COURTYARD origin (0..2, 0..2)",  "courtyard-nw", false)
    );

    private static final class TileManifestRef {
        final String sheet;
        final int col, row;
        final String constantName;
        final String expectedNameHint; // "starts with"-style hint, lowercase
        final boolean placeholder;
        TileManifestRef(String sheet, int col, int row, String constantName, String expectedNameHint, boolean placeholder) {
            this.sheet = sheet; this.col = col; this.row = row;
            this.constantName = constantName; this.expectedNameHint = expectedNameHint;
            this.placeholder = placeholder;
        }
    }

    @Test
    void runFullAudit() throws Exception {
        StringBuilder out = new StringBuilder();
        out.append("\n=== Tileset Catalog Audit ===\n");

        Map<String, List<Entry>> bySheet = new LinkedHashMap<>();
        for (SheetMeta sheet : SHEETS) {
            Path file = CATALOG_DIR.resolve(sheet.basename + ".catalog.json");
            assertTrue(Files.exists(file), "missing catalog file: " + file);
            String text = Files.readString(file);
            JSONObject root = new JSONObject(text);
            JSONArray arr = root.optJSONArray("entries");
            assertNotNull(arr, "no 'entries' array in " + file);

            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                int col = o.getInt("col");
                int row = o.getInt("row");
                String name = o.optString("name", "");
                String desc = o.optString("description", "");
                assertTrue(col >= 0 && col < sheet.cols,
                        sheet.basename + " entry " + i + " col " + col + " out of bounds [0, " + sheet.cols + ")");
                assertTrue(row >= 0 && row < sheet.rows,
                        sheet.basename + " entry " + i + " row " + row + " out of bounds [0, " + sheet.rows + ")");
                entries.add(new Entry(col, row, name, desc));
            }
            bySheet.put(sheet.basename, entries);
            out.append(String.format("%n%-18s  %3d entries  (%dx%d @ %dpx)%n",
                    sheet.basename, entries.size(), sheet.cols, sheet.rows, sheet.tileSize));
        }

        out.append("\n--- Duplicate name analysis (pools / re-use) ---\n");
        for (Map.Entry<String, List<Entry>> e : bySheet.entrySet()) {
            String sheet = e.getKey();
            Map<String, List<Entry>> byName = new TreeMap<>();
            for (Entry x : e.getValue()) {
                byName.computeIfAbsent(x.name, k -> new ArrayList<>()).add(x);
            }
            boolean any = false;
            for (Map.Entry<String, List<Entry>> n : byName.entrySet()) {
                if (n.getValue().size() < 2) continue;
                if (!any) { out.append("  ").append(sheet).append(":\n"); any = true; }
                List<String> coords = new ArrayList<>();
                for (Entry x : n.getValue()) coords.add(x.coord());
                out.append(String.format("    %-30s × %d  %s%n",
                        n.getKey(), n.getValue().size(), String.join(" ", coords)));
            }
        }

        out.append("\n--- Naming-convention drift ---\n");
        out.append("    (expected: lowercase kebab — [a-z0-9-]+; flagged: anything else)\n");
        for (Map.Entry<String, List<Entry>> e : bySheet.entrySet()) {
            String sheet = e.getKey();
            List<Entry> bad = new ArrayList<>();
            for (Entry x : e.getValue()) {
                if (x.name.isEmpty()) continue;
                if (!x.name.matches("[a-z0-9-]+")) bad.add(x);
            }
            if (bad.isEmpty()) continue;
            bad.sort(Comparator.comparingInt((Entry x) -> x.row).thenComparingInt(x -> x.col));
            out.append("  ").append(sheet).append(":\n");
            for (Entry x : bad) {
                out.append(String.format("    %-12s  %s%n", x.coord(), x.name));
            }
        }

        out.append("\n--- TileManifest cross-reference ---\n");
        out.append("    (TileManifest's pinned cells — flags mismatch with catalog)\n");
        for (TileManifestRef ref : TILE_MANIFEST_REFS) {
            List<Entry> entries = bySheet.get(ref.sheet);
            Entry hit = null;
            for (Entry x : entries) {
                if (x.col == ref.col && x.row == ref.row) { hit = x; break; }
            }
            String status;
            if (hit == null) {
                status = "MISSING   (no catalog entry)";
            } else if (hit.name.toLowerCase().startsWith(ref.expectedNameHint)) {
                status = (ref.placeholder ? "match*   " : "match    ")
                        + "(\"" + hit.name + "\""
                        + (ref.placeholder ? " — placeholder visual" : "")
                        + ")";
            } else {
                status = "MISMATCH  (catalog says \"" + hit.name + "\", expected ~ \"" + ref.expectedNameHint + "*\")";
            }
            out.append(String.format("    %-18s (%2d,%2d)  %-30s  %s%n",
                    ref.sheet, ref.col, ref.row, ref.constantName, status));
        }

        out.append("\n--- Empty description coverage ---\n");
        for (Map.Entry<String, List<Entry>> e : bySheet.entrySet()) {
            int total = e.getValue().size();
            int withDesc = 0;
            for (Entry x : e.getValue()) if (!x.description.isEmpty()) withDesc++;
            out.append(String.format("    %-18s  %d / %d entries have descriptions%n",
                    e.getKey(), withDesc, total));
        }

        out.append("\n=== End audit ===\n");
        System.out.println(out);
    }
}
