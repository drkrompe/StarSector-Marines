package com.dillon.starsectormarines.tools.tilesets;

import com.dillon.starsectormarines.StarsectorMarinesModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory catalog of human-authored notes for a single tileset sheet. Each
 * entry binds a {@code (col, row)} cell to a short identifier + free-text
 * description so the user can label what they see in the in-game viewer
 * without having to count grid cells in an external editor.
 *
 * <p>Persistence lives in two places because Starsector's script sandbox
 * forbids {@code java.nio.file} writes from mod code:
 * <ul>
 *   <li><b>Read fallback (committed):</b> {@code data/tilesets/<basename>.catalog.json}
 *       under the mod, loaded via {@link SettingsAPI#loadText}. This is what's
 *       checked into source; the mod ships with it.</li>
 *   <li><b>Read/write working copy:</b> {@code <starsectorDir>/saves/common/starsector_marines/tilesets/<basename>.catalog.json},
 *       via {@link SettingsAPI#writeJSONToCommon} /
 *       {@link SettingsAPI#readJSONFromCommon}. This is where the in-game
 *       editor saves and where it reads from first.</li>
 * </ul>
 *
 * <p>The {@code pullCatalogs} Gradle task copies the saves/common working
 * copies back into the mod source repo when you're ready to commit.
 */
public class TilesetCatalog {

    private static final Logger LOG = Global.getLogger(TilesetCatalog.class);

    /** Inline cell key — packed as a {@code col,row} string for stable JSON ordering / lookup. */
    private static String key(int col, int row) {
        return col + "," + row;
    }

    public static final class Entry {
        public final int col;
        public final int row;
        public String name;
        public String description;

        public Entry(int col, int row, String name, String description) {
            this.col = col;
            this.row = row;
            this.name = name == null ? "" : name;
            this.description = description == null ? "" : description;
        }

        public boolean isBlank() {
            return name.isEmpty() && description.isEmpty();
        }
    }

    private final String sheetPath;
    /** Map keyed by {@link #key(int, int)}. Iteration order is not guaranteed; the UI sorts when displaying. */
    private final Map<String, Entry> entries = new HashMap<>();

    public TilesetCatalog(String sheetPath) {
        this.sheetPath = sheetPath;
    }

    public String sheetPath() { return sheetPath; }

    public Entry get(int col, int row) {
        return entries.get(key(col, row));
    }

    /** Returns the existing entry or creates a blank one. Mutating its fields counts as a pending edit; {@link #save()} flushes. */
    public Entry getOrCreate(int col, int row) {
        return entries.computeIfAbsent(key(col, row), k -> new Entry(col, row, "", ""));
    }

    public void remove(int col, int row) {
        entries.remove(key(col, row));
    }

    /**
     * Loads the catalog. Prefers the in-progress working copy in
     * {@code saves/common} so in-game edits round-trip across sheet switches;
     * falls back to the committed copy bundled with the mod when no working
     * copy exists yet.
     */
    public void load() {
        entries.clear();
        SettingsAPI s = Global.getSettings();
        String commonPath = commonResourcePath();
        try {
            if (s.fileExistsInCommon(commonPath)) {
                JSONObject root = s.readJSONFromCommon(commonPath, false);
                ingest(root);
                LOG.info("TilesetCatalog: loaded " + entries.size() + " entries from saves/common/" + commonPath);
                return;
            }
        } catch (Exception e) {
            LOG.warn("TilesetCatalog: failed to read working copy " + commonPath + " — falling through to mod resource", e);
        }

        String modPath = modResourcePath();
        try {
            String text = s.loadText(modPath);
            if (text != null && !text.isEmpty()) {
                JSONObject root = new JSONObject(text);
                ingest(root);
                LOG.info("TilesetCatalog: loaded " + entries.size() + " entries from mod resource " + modPath);
            }
        } catch (Exception e) {
            LOG.info("TilesetCatalog: no committed catalog at " + modPath + " (treating as empty)");
        }
    }

    private void ingest(JSONObject root) throws Exception {
        JSONArray arr = root.optJSONArray("entries");
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            int col = o.getInt("col");
            int row = o.getInt("row");
            String name = o.optString("name", "");
            String desc = o.optString("description", "");
            entries.put(key(col, row), new Entry(col, row, name, desc));
        }
    }

    /**
     * Writes this catalog to the working copy in {@code saves/common}. Entries
     * with both {@code name} and {@code description} blank are dropped so the
     * file doesn't bloat with cells the user clicked but never labelled.
     */
    public void save() throws Exception {
        // Apply auto-increment + lowercase pass so on-disk names stay consistent
        // across in-game save sessions. Normalizer is idempotent — running it
        // again over an already-normalized catalog is a no-op.
        List<Entry> kept = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (!e.isBlank()) kept.add(e);
        }
        kept = TilesetCatalogNormalizer.normalize(kept);

        // Push the normalized names back into the in-memory map so the in-game
        // sidebar sees the post-save names on its next selection (the field
        // currently shows whatever the user typed before clicking Save; after
        // this round-trip it'll show the auto-incremented form).
        entries.clear();
        for (Entry e : kept) entries.put(key(e.col, e.row), e);

        // Sorted by (row, col) so diffs read top-down across the sheet.
        kept.sort(Comparator.<Entry>comparingInt(e -> e.row).thenComparingInt(e -> e.col));

        JSONObject root = new JSONObject();
        root.put("sheet", sheetPath);
        JSONArray arr = new JSONArray();
        for (Entry e : kept) {
            JSONObject o = new JSONObject();
            o.put("col", e.col);
            o.put("row", e.row);
            o.put("name", e.name);
            o.put("description", e.description);
            arr.put(o);
        }
        root.put("entries", arr);

        String commonPath = commonResourcePath();
        Global.getSettings().writeJSONToCommon(commonPath, root, false);
        LOG.info("TilesetCatalog: wrote " + arr.length() + " entries to saves/common/" + commonPath);
    }

    /** {@code graphics/tilesets/Foo_Tiles.png} → {@code data/tilesets/Foo_Tiles.catalog.json} (mod-bundled committed copy). */
    private String modResourcePath() {
        return "data/tilesets/" + basenameStem() + ".catalog.json";
    }

    /** {@code graphics/tilesets/Foo_Tiles.png} → {@code starsector_marines/tilesets/Foo_Tiles.catalog.json} (saves/common working copy). */
    private String commonResourcePath() {
        return StarsectorMarinesModPlugin.MOD_ID + "/tilesets/" + basenameStem() + ".catalog.json";
    }

    private String basenameStem() {
        int slash = sheetPath.lastIndexOf('/');
        String fname = slash < 0 ? sheetPath : sheetPath.substring(slash + 1);
        int dot = fname.lastIndexOf('.');
        return dot < 0 ? fname : fname.substring(0, dot);
    }
}
