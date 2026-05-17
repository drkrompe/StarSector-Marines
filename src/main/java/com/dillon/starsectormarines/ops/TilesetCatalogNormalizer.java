package com.dillon.starsectormarines.ops;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure-data normalization for {@link TilesetCatalog.Entry} lists. Applied:
 * <ol>
 *   <li><b>Lowercase the name</b> — keeps the catalog in a single kebab style
 *       so {@code Fl-NW-Dam} → {@code fl-nw-dam} and similar drift heals
 *       itself the next time we touch the entry.</li>
 *   <li><b>Auto-increment same-name groups</b> — entries that share a base
 *       name on the same sheet get suffixed {@code -1, -2, ...}, sorted by
 *       {@code (row, col)} for stable numbering. So {@code grass} appearing
 *       three times becomes {@code grass-1, grass-2, grass-3}.</li>
 * </ol>
 *
 * <p>Idempotent: running normalize twice produces the same result the second
 * time. Empty names are left alone — they're "I clicked but didn't label",
 * not duplicates worth numbering.
 *
 * <p>Pure utility — no I/O. Callers feed it a list of entries and write the
 * returned list back to whatever storage they own ({@link TilesetCatalog#save}
 * for the in-game tool, or a direct file write for offline batch fixes).
 */
public final class TilesetCatalogNormalizer {

    private TilesetCatalogNormalizer() {}

    /**
     * Returns a new list with normalized names. Input is not mutated; each
     * output entry is a freshly-constructed {@link TilesetCatalog.Entry} so
     * the caller can safely diff input against output to count changes.
     */
    public static List<TilesetCatalog.Entry> normalize(List<TilesetCatalog.Entry> in) {
        // Step 1 — lowercase every name. Description left alone (free-text).
        List<TilesetCatalog.Entry> lowered = new ArrayList<>(in.size());
        for (TilesetCatalog.Entry e : in) {
            String n = e.name == null ? "" : e.name.toLowerCase(Locale.ROOT);
            lowered.add(new TilesetCatalog.Entry(e.col, e.row, n,
                    e.description == null ? "" : e.description));
        }

        // Step 2 — group by lowercased name (preserving first-seen ordering
        // so map iteration is deterministic for diff-friendly output).
        Map<String, List<TilesetCatalog.Entry>> byName = new LinkedHashMap<>();
        for (TilesetCatalog.Entry e : lowered) {
            byName.computeIfAbsent(e.name, k -> new ArrayList<>()).add(e);
        }

        // Step 3 — build a lookup (col, row) → renamed entry. Singletons and
        // empty-name groups pass through unchanged; multi-entry groups get
        // -1, -2, ... suffixes sorted by (row, col).
        Map<Long, TilesetCatalog.Entry> rewrites = new LinkedHashMap<>();
        for (Map.Entry<String, List<TilesetCatalog.Entry>> g : byName.entrySet()) {
            List<TilesetCatalog.Entry> group = g.getValue();
            if (group.size() == 1 || g.getKey().isEmpty()) {
                for (TilesetCatalog.Entry e : group) rewrites.put(key(e), e);
                continue;
            }
            group.sort(Comparator.<TilesetCatalog.Entry>comparingInt(e -> e.row)
                    .thenComparingInt(e -> e.col));
            for (int i = 0; i < group.size(); i++) {
                TilesetCatalog.Entry old = group.get(i);
                String newName = old.name + "-" + (i + 1);
                rewrites.put(key(old),
                        new TilesetCatalog.Entry(old.col, old.row, newName, old.description));
            }
        }

        // Step 4 — emit in original input order so a caller diff stays stable.
        List<TilesetCatalog.Entry> out = new ArrayList<>(in.size());
        for (TilesetCatalog.Entry orig : in) {
            out.add(rewrites.get(key(orig)));
        }
        return out;
    }

    private static long key(TilesetCatalog.Entry e) {
        return ((long) e.row << 32) | (e.col & 0xFFFFFFFFL);
    }
}
