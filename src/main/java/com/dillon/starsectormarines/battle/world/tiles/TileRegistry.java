package com.dillon.starsectormarines.battle.world.tiles;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime catalog of {@link TileDef}s loaded from {@code data/tilesets/*.tileset.json},
 * addressed by stable string id. The asset-store half of the moddable-tilesets
 * split ([[battle_services_systems]]: registry = store, fillers / renderer =
 * systems that consume it by id). See {@code roadmap/moddable-tilesets/}.
 *
 * <p>Parsing ({@link #ingestSheet}) is decoupled from the game's
 * {@link com.fs.starfarer.api.SettingsAPI} so tests can feed a {@link JSONObject}
 * read straight off disk; {@link #loadBuiltins()} is the in-game path that pulls
 * the bundled resources and installs the result.
 *
 * <p>Phase 1a builds and validates the registry but does not yet drive
 * rendering or generation — the consumers migrate off the enums in 1b/1c. The
 * {@code TileRegistryParityTest} pins the JSON's semantics to the current enum
 * fields so that migration is provably behavior-preserving.
 */
public final class TileRegistry {

    private static final Logger LOG = Global.getLogger(TileRegistry.class);

    /**
     * Built-in tileset resources bundled with the mod. Phase 3 (submod support)
     * replaces this fixed list with discovery + cross-mod merge; for now the
     * sliced sheets migrated in Phase 1 are listed explicitly.
     */
    public static final List<String> BUILTIN_TILESETS = List.of(
            "data/tilesets/nature-tiles.tileset.json",
            "data/tilesets/urban-tileset-3.tileset.json",
            "data/tilesets/urban-tileset.tileset.json",
            "data/tilesets/urban-tileset-2.tileset.json",
            "data/tilesets/Floors_Tiles.tileset.json",
            "data/tilesets/Water_tiles.tileset.json");

    private static volatile TileRegistry installed;

    private final Map<String, TileDef> byId = new LinkedHashMap<>();
    /** Parallel to {@link #byId} insertion order — {@code byIndex.get(i).index == i}. The dense-handle reverse lookup. */
    private final List<TileDef> byIndex = new ArrayList<>();
    /** Fixed-grid autotile/single blocks (grid sheets), addressed by id — separate namespace from the sliced {@link TileDef}s. */
    private final Map<String, GridBlockDef> blocksById = new LinkedHashMap<>();
    /**
     * Folded-in per-cell viewer annotations from each sheet's {@code "cells"}
     * array, keyed by {@code sheetPath} then a packed {@code (col,row)} key. The
     * successor to the old {@code .catalog.json} sidecars — doc-only, read solely
     * by the dev viewer via {@link #cellLabel}.
     */
    private final Map<String, Map<Long, CellLabel>> cellsBySheet = new LinkedHashMap<>();

    private static long cellKey(int col, int row) { return ((long) col << 32) | (row & 0xFFFFFFFFL); }

    public TileDef tile(String id)      { return byId.get(id); }
    public boolean has(String id)       { return byId.containsKey(id); }
    public Collection<TileDef> all()    { return byId.values(); }
    public int size()                   { return byId.size(); }

    /** Reverse lookup by dense {@link TileDef#index}. Throws on an out-of-range handle — a stale index is a bug, not a silent miss. */
    public TileDef byIndex(int index) {
        if (index < 0 || index >= byIndex.size()) {
            throw new IndexOutOfBoundsException("TileRegistry: no tile at index " + index + " (size " + byIndex.size() + ")");
        }
        return byIndex.get(index);
    }

    /** Dense index for {@code id}. Throws if unknown — callers resolving a built-in id should never miss. */
    public int indexOf(String id) {
        return Objects.requireNonNull(byId.get(id), () -> "TileRegistry: unknown tile id '" + id + "'").index;
    }

    /** Grid block by id (fixed-grid sheets), or {@code null} if unknown. */
    public GridBlockDef block(String id)        { return blocksById.get(id); }
    public boolean hasBlock(String id)          { return blocksById.containsKey(id); }
    public Collection<GridBlockDef> blocks()    { return blocksById.values(); }

    /**
     * The viewer annotation for one source cell of {@code sheetPath}, or
     * {@code null} if unlabelled. Grid sheets resolve from the folded-in
     * {@code "cells"} array; sliced sheets fall back to the tile whose
     * {@link TileDef#frame} equals {@code col} (their cells are a single
     * left-to-right strip, {@code row == 0}). Doc-only — for the dev viewer.
     */
    public CellLabel cellLabel(String sheetPath, int col, int row) {
        Map<Long, CellLabel> sheet = cellsBySheet.get(sheetPath);
        if (sheet != null) {
            CellLabel label = sheet.get(cellKey(col, row));
            if (label != null) return label;
        }
        if (row == 0) {
            for (TileDef def : byIndex) {
                if (def.frame == col && sheetPath.equals(def.sheetPath)) {
                    if (def.name.isEmpty() && def.description.isEmpty()) return null;
                    return new CellLabel(def.name, def.description);
                }
            }
        }
        return null;
    }

    /** The registry installed at application load, or {@code null} if load failed / hasn't run. */
    public static TileRegistry installed() { return installed; }

    /**
     * Installs {@code reg} as the process-wide registry. Intended for tests that
     * need {@link #installed()} populated before calling gen code that reads it
     * (e.g. {@link com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator}
     * via {@code NatureZoneFiller}). Production code uses {@link #loadBuiltins()}.
     */
    public static void install(TileRegistry reg) { installed = reg; }

    /**
     * Parses one tileset JSON document and adds its tiles. Fails loud on a
     * duplicate id — the registry is authoritative, so a colliding sheet is a
     * bug to surface, not a tile to silently drop. (Submod <em>override</em>
     * semantics are a deliberate Phase 3 concern, not an accidental
     * last-one-wins here.)
     */
    public void ingestSheet(JSONObject root) throws JSONException {
        String sheet = root.getString("sheet");
        JSONArray tiles = root.optJSONArray("tiles");
        if (tiles != null) ingestTiles(tiles, sheet);
        JSONArray blocks = root.optJSONArray("blocks");
        if (blocks != null) ingestBlocks(blocks, sheet, root.optInt("cellPx", 0));
        JSONArray cells = root.optJSONArray("cells");
        if (cells != null) ingestCells(cells, sheet);
    }

    /** Sliced-sheet tiles (frame-indexed). See {@link #ingestSheet}. */
    private void ingestTiles(JSONArray tiles, String sheet) throws JSONException {
        for (int i = 0; i < tiles.length(); i++) {
            JSONObject o = tiles.getJSONObject(i);
            String id = o.getString("id");
            requireUniqueId(id, sheet);
            // Sliced tiles must pin a frame explicitly — a missing 'frame' must
            // fail loud, not silently default to the -1 block sentinel (grid
            // blocks carry origin+layout instead, parsed by ingestBlocks).
            if (!o.has("frame")) {
                throw new IllegalStateException("TileRegistry: tile '" + id
                        + "' is missing 'frame' (sheet " + sheet + ")");
            }
            int frame = o.getInt("frame");
            TileLayer layer = TileLayer.fromJson(o.optString("layer", "ground"));
            TileCover cover = TileCover.fromJson(o.optString("cover", "none"));
            boolean passable = o.optBoolean("passable", true);
            List<String> validOn = new ArrayList<>();
            JSONArray vo = o.optJSONArray("validOn");
            if (vo != null) {
                for (int k = 0; k < vo.length(); k++) validOn.add(vo.getString(k));
            }
            String name = o.optString("name", "");
            String description = o.optString("description", "");
            TileDef def = new TileDef(id, sheet, byIndex.size(), frame, layer, cover, passable,
                    validOn, name, description);
            byId.put(id, def);
            byIndex.add(def);
        }
    }

    /**
     * Per-cell viewer annotations (folded in from the former {@code .catalog.json}).
     * Doc-only: not validated against block/tile coverage and never read by sim or
     * render — only {@link #cellLabel}. Last duplicate {@code (col,row)} wins.
     */
    private void ingestCells(JSONArray cells, String sheet) throws JSONException {
        Map<Long, CellLabel> bySheet = cellsBySheet.computeIfAbsent(sheet, k -> new LinkedHashMap<>());
        for (int i = 0; i < cells.length(); i++) {
            JSONObject o = cells.getJSONObject(i);
            int col = o.getInt("col");
            int row = o.getInt("row");
            bySheet.put(cellKey(col, row), new CellLabel(o.optString("name", ""), o.optString("description", "")));
        }
    }

    /** Fixed-grid autotile/single blocks (origin + named {@link GridLayout}). See {@link #ingestSheet}. */
    private void ingestBlocks(JSONArray blocks, String sheet, int cellPx) throws JSONException {
        if (cellPx <= 0) {
            throw new IllegalStateException("TileRegistry: sheet '" + sheet + "' has blocks but no positive 'cellPx'");
        }
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject o = blocks.getJSONObject(i);
            String id = o.getString("id");
            requireUniqueId(id, sheet);
            JSONArray cellsArr = o.optJSONArray("cells");
            if (cellsArr != null) {
                // Variant pool — explicit {col,row} cells, hash-picked.
                if (cellsArr.length() == 0) {
                    throw new IllegalStateException("TileRegistry: block '" + id + "' has empty 'cells' (sheet " + sheet + ")");
                }
                int[][] cells = new int[cellsArr.length()][];
                for (int k = 0; k < cellsArr.length(); k++) {
                    JSONArray cell = cellsArr.getJSONArray(k);
                    cells[k] = new int[]{cell.getInt(0), cell.getInt(1)};
                }
                blocksById.put(id, GridBlockDef.variantPool(id, sheet, cellPx, cells));
                continue;
            }
            JSONArray origin = o.getJSONArray("origin");
            int oc = origin.getInt(0);
            int or = origin.getInt(1);
            GridLayout layout = GridLayout.fromJson(o.getString("layout"));
            Integer fillRgb = o.has("fillRgb") ? Integer.decode(o.getString("fillRgb")) : null;
            blocksById.put(id, new GridBlockDef(id, sheet, cellPx, oc, or, layout, fillRgb));
        }
    }

    /** Ids share one namespace across sliced tiles and grid blocks — a collision is a bug to surface. */
    private void requireUniqueId(String id, String sheet) {
        if (byId.containsKey(id) || blocksById.containsKey(id)) {
            throw new IllegalStateException("TileRegistry: duplicate id '" + id + "' (sheet " + sheet + ")");
        }
    }

    /**
     * Validates cross-tile references once every sheet is ingested: each
     * {@code validOn} id selector (positive or {@code !}-exclusion) must resolve
     * to a known tile. {@code layer:} selectors need no resolution. Run after
     * all {@link #ingestSheet} calls, since references may point across sheets.
     */
    public void validateReferences() {
        for (TileDef def : byId.values()) {
            for (String sel : def.validOn) {
                String ref = sel.startsWith("!") ? sel.substring(1) : sel;
                if (ref.startsWith("layer:")) {
                    // A bad layer token (e.g. "layer:grund") silently degrades the
                    // tile to "overlays nothing" at eval time — validate it here so
                    // the typo fails at load, not as an unexplained render diff.
                    String token = ref.substring("layer:".length());
                    try {
                        TileLayer.fromJson(token);
                    } catch (RuntimeException e) {
                        throw new IllegalStateException("TileRegistry: tile '" + def.id
                                + "' validOn has unknown layer token '" + ref + "'");
                    }
                    continue;
                }
                if (!byId.containsKey(ref)) {
                    throw new IllegalStateException("TileRegistry: tile '" + def.id
                            + "' validOn references unknown tile '" + ref + "'");
                }
            }
        }
    }

    /**
     * Loads every {@link #BUILTIN_TILESETS} resource via the modded-JSON path
     * and installs the result as {@link #installed()}. Fully defensive — a
     * failure logs and leaves any prior install in place rather than throwing
     * out of {@code onApplicationLoad}.
     */
    public static void loadBuiltins() {
        try {
            TileRegistry reg = new TileRegistry();
            for (String path : BUILTIN_TILESETS) {
                JSONObject root = Global.getSettings().loadJSON(path, true);
                reg.ingestSheet(root);
            }
            reg.validateReferences();
            installed = reg;
            LOG.info("TileRegistry: loaded " + reg.size() + " tiles from "
                    + BUILTIN_TILESETS.size() + " built-in tilesets");
        } catch (Exception e) {
            LOG.error("TileRegistry: failed to load built-in tilesets — registry not installed", e);
        }
    }
}
