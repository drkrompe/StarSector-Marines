package com.dillon.starsectormarines.ops;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * One-shot dev tool that normalizes every catalog under
 * {@code mod/data/tilesets/} in place — lowercases names and appends
 * {@code -1, -2, ...} to entries that share a base name on the same sheet.
 * Driven by {@link TilesetCatalogNormalizer}, so the in-game save flow and
 * this offline pass apply the exact same rules.
 *
 * <p>Gated on the system property {@code tileset.normalize=true} so a plain
 * {@code gradlew test} doesn't touch the working tree. Invoke with:
 * <pre>
 *   gradlew :test --tests "*TilesetCatalogNormalizeTest*" -Dtileset.normalize=true
 * </pre>
 * After running, re-run the audit test to confirm naming drift and
 * un-numbered duplicates are gone.
 *
 * <p>Output is re-pretty-printed JSON sorted by {@code (row, col)} — same
 * layout {@link TilesetCatalog#save} uses, so the offline and in-game writes
 * produce byte-identical files for the same input.
 */
public class TilesetCatalogNormalizeTest {

    private static final Path CATALOG_DIR = Paths.get("mod/data/tilesets");
    private static final List<String> CATALOGS = List.of(
            "urban-tileset", "urban-tileset-2", "Floors_Tiles", "Water_tiles"
    );

    @Test
    void normalizeAllCatalogs() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getProperty("tileset.normalize")),
                "Skipped — set -Dtileset.normalize=true to run.");

        StringBuilder report = new StringBuilder();
        report.append("\n=== Tileset Catalog Normalization ===\n");

        for (String basename : CATALOGS) {
            Path file = CATALOG_DIR.resolve(basename + ".catalog.json");
            String original = Files.readString(file);

            JSONObject root = new JSONObject(original);
            JSONArray arr = root.getJSONArray("entries");

            List<TilesetCatalog.Entry> entries = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                entries.add(new TilesetCatalog.Entry(
                        o.getInt("col"),
                        o.getInt("row"),
                        o.optString("name", ""),
                        o.optString("description", "")));
            }

            List<TilesetCatalog.Entry> normalized = TilesetCatalogNormalizer.normalize(entries);

            // Count + record renames so the report quotes the diff.
            int renames = 0;
            StringBuilder diffLines = new StringBuilder();
            for (int i = 0; i < entries.size(); i++) {
                TilesetCatalog.Entry before = entries.get(i);
                TilesetCatalog.Entry after = normalized.get(i);
                if (!before.name.equals(after.name)) {
                    renames++;
                    if (renames <= 12) {
                        diffLines.append(String.format("      (%2d,%2d)  %-26s →  %s%n",
                                before.col, before.row, before.name, after.name));
                    }
                }
            }
            if (renames > 12) {
                diffLines.append(String.format("      … and %d more%n", renames - 12));
            }

            // Serialize sorted by (row, col) — matches TilesetCatalog.save layout.
            normalized = new ArrayList<>(normalized);
            normalized.sort(Comparator.<TilesetCatalog.Entry>comparingInt(e -> e.row)
                    .thenComparingInt(e -> e.col));

            JSONObject newRoot = new JSONObject();
            newRoot.put("sheet", root.optString("sheet"));
            JSONArray newArr = new JSONArray();
            for (TilesetCatalog.Entry e : normalized) {
                JSONObject o = new JSONObject();
                o.put("col", e.col);
                o.put("row", e.row);
                o.put("name", e.name);
                o.put("description", e.description);
                newArr.put(o);
            }
            newRoot.put("entries", newArr);

            String newText = newRoot.toString(2);
            boolean changed = !original.trim().equals(newText.trim());
            if (changed) {
                Files.writeString(file, newText);
                report.append(String.format("%n  %-18s  %3d renames  →  WRITTEN%n",
                        basename, renames));
                if (diffLines.length() > 0) report.append(diffLines);
            } else {
                report.append(String.format("%n  %-18s  no changes (already normalized)%n",
                        basename));
            }
        }

        report.append("\n=== End ===\n");
        System.out.println(report);
    }
}
