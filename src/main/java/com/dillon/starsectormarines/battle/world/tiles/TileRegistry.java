package com.dillon.starsectormarines.battle.world.tiles;

import com.dillon.starsectormarines.catalog.CatalogSource;
import com.dillon.starsectormarines.catalog.MarineCatalogManifest.CatalogFile;
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
 * systems that consume it by id). See {@code moddable-tilesets-nouns.md}.
 *
 * <p>Parsing ({@link #ingestSheet}) is decoupled from the game's
 * {@link com.fs.starfarer.api.SettingsAPI} so tests can feed a {@link JSONObject}
 * read straight off disk; {@link #loadContributions(List)} is the in-game path
 * that installs every enabled provider's explicitly declared resources.
 *
 * <p>The registry is the installed authority used by generation and rendering.
 * Parity tests pin the bundled definitions to their established visual and
 * tactical behavior.
 */
public final class TileRegistry {

    private static final Logger LOG = Global.getLogger(TileRegistry.class);

    /**
     * Core resources retained for standalone tools and compatibility tests.
     * Production discovers these through the core manifest alongside external
     * provider contributions.
     */
    public static final List<String> BUILTIN_TILESETS = List.of(
            "data/tilesets/nature-tiles.tileset.json",
            "data/tilesets/urban-tileset-3.tileset.json",
            "data/tilesets/urban-tileset.tileset.json",
            "data/tilesets/urban-tileset-2.tileset.json",
            "data/tilesets/Floors_Tiles.tileset.json",
            "data/tilesets/Water_tiles.tileset.json",
            "data/tilesets/doodads.tileset.json");

    private static volatile TileRegistry installed;

    private final Map<String, TileDef> byId = new LinkedHashMap<>();
    /** Parallel to {@link #byId} insertion order — {@code byIndex.get(i).index == i}. The dense-handle reverse lookup. */
    private final List<TileDef> byIndex = new ArrayList<>();
    /** Fixed-grid autotile/single blocks (grid sheets), addressed by id — separate namespace from the sliced {@link TileDef}s. */
    private final Map<String, GridBlockDef> blocksById = new LinkedHashMap<>();
    /** Decorative props (Phase 2), addressed by id — one source cell + an intrinsic tactical cover. */
    private final Map<String, DoodadDef> doodadsById = new LinkedHashMap<>();
    /** Provider provenance for the shared tile/block/doodad id namespace. */
    private final Map<String, CatalogSource> sourceById = new LinkedHashMap<>();
    /**
     * Folded-in per-cell viewer annotations from each sheet's {@code "cells"}
     * array, keyed by {@code sheetPath} then a packed {@code (col,row)} key. The
     * successor to the old {@code .catalog.json} sidecars — doc-only, read solely
     * by the dev viewer via {@link #cellLabel}.
     */
    private final Map<String, Map<Long, CellLabel>> cellsBySheet = new LinkedHashMap<>();
    private final Map<String, Map<Long, CatalogSource>> cellSourcesBySheet = new LinkedHashMap<>();

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

    /** Doodad def by id (decorative props, Phase 2), or {@code null} if unknown. */
    public DoodadDef doodad(String id)          { return doodadsById.get(id); }
    public boolean hasDoodad(String id)         { return doodadsById.containsKey(id); }
    public Collection<DoodadDef> doodads()      { return doodadsById.values(); }

    /** Exact provider and catalog resource that declared {@code id}, or null when unknown. */
    public CatalogSource sourceOf(String id) { return sourceById.get(id); }

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
     * via {@code NatureZoneFiller}). Production code uses
     * {@link #loadContributions(List)}.
     */
    public static void install(TileRegistry reg) { installed = reg; }

    /**
     * Parses one tileset JSON document and adds its tiles. Fails loud on a
     * duplicate id — the registry is authoritative, so a colliding sheet is a
     * bug to surface, not a tile to silently drop. Cross-provider catalogs are
     * additive and retain this same no-override law.
     */
    public void ingestSheet(JSONObject root) throws JSONException {
        ingestSheet(root, CatalogSource.unspecified("<in-memory tileset>"));
    }

    public void ingestSheet(JSONObject root, CatalogSource source) throws JSONException {
        String sheet = root.getString("sheet");
        JSONArray tiles = root.optJSONArray("tiles");
        if (tiles != null) ingestTiles(tiles, sheet, source);
        JSONArray blocks = root.optJSONArray("blocks");
        if (blocks != null) ingestBlocks(blocks, sheet, root.optInt("cellPx", 0), source);
        JSONArray cells = root.optJSONArray("cells");
        if (cells != null) ingestCells(cells, sheet, source);
        JSONArray doodads = root.optJSONArray("doodads");
        if (doodads != null) {
            ingestDoodads(doodads, sheet, root.optInt("cellPx", 0), source);
        }
    }

    /** Decorative-prop defs (id + source cell + intrinsic cover). See {@link #ingestSheet}. */
    private void ingestDoodads(JSONArray doodads, String sheet, int cellPx,
                               CatalogSource source) throws JSONException {
        for (int i = 0; i < doodads.length(); i++) {
            JSONObject o = doodads.getJSONObject(i);
            String id = o.getString("id");
            requireUniqueId(id, source);
            int col = o.getInt("col");
            int row = o.getInt("row");
            DoodadCover cover = DoodadCover.fromJson(o.optString("cover", "none"));
            float ballisticHalfHeight = cover.defaultBallisticHalfHeight();
            if (o.has("ballisticHalfHeight")) {
                ballisticHalfHeight = (float) o.getDouble("ballisticHalfHeight");
                if (!Float.isFinite(ballisticHalfHeight) || ballisticHalfHeight < 0f) {
                    throw new IllegalStateException("TileRegistry: doodad '" + id
                            + "' has invalid ballisticHalfHeight " + ballisticHalfHeight);
                }
            }
            int footprintCellsX = 1;
            int footprintCellsY = 1;
            JSONArray footprint = o.optJSONArray("footprintCells");
            if (footprint != null) {
                if (footprint.length() != 2) {
                    throw new IllegalStateException("TileRegistry: doodad '" + id
                            + "' footprintCells must contain [width, height]");
                }
                footprintCellsX = footprint.getInt(0);
                footprintCellsY = footprint.getInt(1);
                if (footprintCellsX <= 0 || footprintCellsY <= 0) {
                    throw new IllegalStateException("TileRegistry: doodad '" + id
                            + "' footprintCells values must be positive");
                }
            }
            DoodadDef.WallSide preferredWallSide;
            try {
                preferredWallSide = DoodadDef.WallSide.fromJson(
                        o.optString("preferredWallSide", null));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("TileRegistry: doodad '" + id
                        + "' has invalid preferredWallSide", e);
            }
            doodadsById.put(id, new DoodadDef(
                    id, sheet, col, row, cover, ballisticHalfHeight,
                    footprintCellsX, footprintCellsY, preferredWallSide, cellPx));
            sourceById.put(id, source);
        }
    }

    /** Sliced-sheet tiles (frame-indexed). See {@link #ingestSheet}. */
    private void ingestTiles(JSONArray tiles, String sheet, CatalogSource source) throws JSONException {
        for (int i = 0; i < tiles.length(); i++) {
            JSONObject o = tiles.getJSONObject(i);
            String id = o.getString("id");
            requireUniqueId(id, source);
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
            sourceById.put(id, source);
        }
    }

    /**
     * Per-cell viewer annotations (folded in from the former {@code .catalog.json}).
     * Doc-only: not validated against block/tile coverage and never read by sim or
     * render — only {@link #cellLabel}. Duplicate {@code (col,row)} annotations
     * fail rather than becoming a hidden provider-order override.
     */
    private void ingestCells(JSONArray cells, String sheet, CatalogSource source) throws JSONException {
        Map<Long, CellLabel> bySheet = cellsBySheet.computeIfAbsent(sheet, k -> new LinkedHashMap<>());
        Map<Long, CatalogSource> sources = cellSourcesBySheet.computeIfAbsent(
                sheet, ignored -> new LinkedHashMap<>());
        for (int i = 0; i < cells.length(); i++) {
            JSONObject o = cells.getJSONObject(i);
            int col = o.getInt("col");
            int row = o.getInt("row");
            long key = cellKey(col, row);
            CatalogSource previous = sources.get(key);
            if (previous != null) {
                throw new IllegalStateException("TileRegistry: duplicate cell label for sheet '"
                        + sheet + "' at [" + col + "," + row + "]: first declared by "
                        + previous.describe() + ", then by " + source.describe());
            }
            bySheet.put(key, new CellLabel(o.optString("name", ""), o.optString("description", "")));
            sources.put(key, source);
        }
    }

    /** Fixed-grid autotile/single blocks (origin + named {@link GridLayout}). See {@link #ingestSheet}. */
    private void ingestBlocks(JSONArray blocks, String sheet, int cellPx,
                              CatalogSource source) throws JSONException {
        if (cellPx <= 0) {
            throw new IllegalStateException("TileRegistry: sheet '" + sheet + "' has blocks but no positive 'cellPx'");
        }
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject o = blocks.getJSONObject(i);
            String id = o.getString("id");
            requireUniqueId(id, source);
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
                sourceById.put(id, source);
                continue;
            }
            JSONArray origin = o.getJSONArray("origin");
            int oc = origin.getInt(0);
            int or = origin.getInt(1);
            GridLayout layout = GridLayout.fromJson(o.getString("layout"));
            Integer fillRgb = o.has("fillRgb") ? Integer.decode(o.getString("fillRgb")) : null;
            blocksById.put(id, new GridBlockDef(id, sheet, cellPx, oc, or, layout, fillRgb));
            sourceById.put(id, source);
        }
    }

    /** Ids share one namespace across sliced tiles, grid blocks, and doodads — a collision is a bug to surface. */
    private void requireUniqueId(String id, CatalogSource source) {
        if (byId.containsKey(id) || blocksById.containsKey(id) || doodadsById.containsKey(id)) {
            throw new IllegalStateException("TileRegistry: duplicate id '" + id
                    + "': first declared by " + sourceById.get(id).describe()
                    + ", then by " + source.describe());
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
                reg.ingestSheet(root, CatalogSource.unspecified(path));
            }
            reg.validateReferences();
            installed = reg;
            LOG.info("TileRegistry: loaded " + reg.size() + " tiles from "
                    + BUILTIN_TILESETS.size() + " built-in tilesets");
        } catch (Exception e) {
            LOG.error("TileRegistry: failed to load built-in tilesets — registry not installed", e);
        }
    }

    /** Loads every enabled-mod tileset contribution in manifest order. */
    public static void loadContributions(List<CatalogFile> catalogs) {
        TileRegistry registry = new TileRegistry();
        for (CatalogFile catalog : catalogs) {
            try {
                registry.ingestSheet(catalog.loadJson(), catalog.source());
            } catch (Exception failure) {
                throw new IllegalStateException("Failed to ingest tileset catalog "
                        + catalog.source().describe(), failure);
            }
        }
        registry.validateReferences();
        installed = registry;
        LOG.info("TileRegistry: loaded " + registry.size() + " sliced tiles from "
                + catalogs.size() + " contributed tilesets");
    }
}
