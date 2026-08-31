package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.DistrictTheme;
import com.dillon.starsectormarines.battle.world.model.SurfaceRole;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.catalog.CatalogSource;
import com.dillon.starsectormarines.catalog.MarineCatalogManifest.CatalogFile;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runtime catalog of the <em>generation mapping</em> — the "how tiles map to
 * generated things" data half of moddable-tilesets Phase 2, loaded from
 * {@code data/tilesets/*.mapping.json}. Sibling to {@link TileRegistry}: that
 * one owns the tile/doodad <em>defs</em> (what art exists); this one owns how
 * gen <em>uses</em> them: ordered doodad pools, {@code GroundKind} and
 * {@link SurfaceRole} render dispatch, per-{@code BlockKind} filler parameters, and surface-relief
 * material overrides.
 *
 * <p>The data/algorithm seam holds: pools/membership are data here; the scatter
 * and carve algorithms stay in Java (the fillers). See
 * {@code moddable-tilesets-nouns.md}. Doodad-pool ids resolve against
 * {@link TileRegistry#installed()} and fail loudly when unknown; render and
 * filler references remain consumer-resolved pending cross-mapping preflight.
 */
public final class GenMappingRegistry {

    private static final Logger LOG = Global.getLogger(GenMappingRegistry.class);

    /** Core resources retained for standalone tools and compatibility tests. */
    public static final List<String> BUILTIN_MAPPINGS = List.of(
            "data/tilesets/urban.mapping.json");

    private static volatile GenMappingRegistry installed;

    /** Pool id -> ordered doodad ids. Pool ids are arbitrary names (the {@link DistrictTheme} names, plus bespoke pools like {@code COMMERCIAL}). Resolved against the TileRegistry on access. */
    private final Map<String, List<String>> doodadPoolIds = new LinkedHashMap<>();
    private final Map<String, CatalogSource> doodadPoolSources = new LinkedHashMap<>();
    /** {@link GroundKind} -> the tileset block/tile id its primary surface renders as. The render-dispatch data half ({@code GroundRenderSystem} reads it instead of hardcoding ids). */
    private final Map<GroundKind, String> groundRender = new EnumMap<>(GroundKind.class);
    private final Map<GroundKind, CatalogSource> groundRenderSources = new EnumMap<>(GroundKind.class);
    /** {@link SurfaceRole} -> the tileset block/tile id that orthogonal surface renders as. The wall, doorway and roof half of render dispatch; {@code groundRender}'s sibling, separate because the two vocabularies are orthogonal per cell. */
    private final Map<SurfaceRole, String> surfaceRender = new EnumMap<>(SurfaceRole.class);
    private final Map<SurfaceRole, CatalogSource> surfaceRenderSources = new EnumMap<>(SurfaceRole.class);
    /** {@link BlockKind} -> its code filler's tunables (pools/chances). The filler reads these instead of hardcoding them; the carve/scatter algorithm stays in the filler. */
    private final Map<BlockKind, FillerParams> fillerParams = new EnumMap<>(BlockKind.class);
    private final Map<BlockKind, CatalogSource> fillerSources = new EnumMap<>(BlockKind.class);
    /**
     * Surface-relief per-{@link GroundKind} macro-height overrides <b>in metres
     * above the ground datum</b>, keyed by the kind's {@code name()} plus the
     * sentinel key {@code "WALL"} (walls aren't a {@code GroundKind} — they're
     * {@code CellTopology.isWall}, orthogonal to ground kind). Sparse:
     * {@link #macroHeightMeters} / {@link #wallMacroHeightMeters} fall back to
     * the sane code defaults below for any key absent here, so an unmapped tile
     * sits at the datum rather than unresolved.
     */
    private final Map<String, Float> macroHeightOverride = new LinkedHashMap<>();
    private final Map<String, CatalogSource> macroHeightSources = new LinkedHashMap<>();

    /** The mapping installed at application load, or {@code null} if load failed / hasn't run. */
    public static GenMappingRegistry installed() { return installed; }

    /** Installs {@code reg} as the process-wide mapping. Tests use this with a disk-loaded instance. */
    public static void install(GenMappingRegistry reg) { installed = reg; }

    /** Parses one mapping JSON document and merges its sections. */
    public void ingest(JSONObject root) throws JSONException {
        ingest(root, CatalogSource.unspecified("<in-memory tile mapping>"));
    }

    public void ingest(JSONObject root, CatalogSource source) throws JSONException {
        JSONObject pools = root.optJSONObject("doodadPools");
        if (pools != null) {
            for (Iterator<String> it = pools.keys(); it.hasNext(); ) {
                String poolId = it.next();
                requireUnique("doodad pool", poolId, doodadPoolSources, source);
                JSONArray arr = pools.getJSONArray(poolId);
                List<String> ids = new ArrayList<>(arr.length());
                for (int i = 0; i < arr.length(); i++) ids.add(arr.getString(i));
                doodadPoolIds.put(poolId, ids);
                doodadPoolSources.put(poolId, source);
            }
        }
        JSONObject ground = root.optJSONObject("groundRender");
        if (ground != null) {
            for (Iterator<String> it = ground.keys(); it.hasNext(); ) {
                String kindName = it.next();
                GroundKind kind = GroundKind.valueOf(kindName);
                requireUnique("ground render mapping", kind, groundRenderSources, source);
                groundRender.put(kind, ground.getString(kindName));
                groundRenderSources.put(kind, source);
            }
        }
        JSONObject surfaces = root.optJSONObject("surfaceRender");
        if (surfaces != null) {
            for (Iterator<String> it = surfaces.keys(); it.hasNext(); ) {
                String roleName = it.next();
                SurfaceRole role = SurfaceRole.valueOf(roleName);
                requireUnique("surface render mapping", role, surfaceRenderSources, source);
                surfaceRender.put(role, surfaces.getString(roleName));
                surfaceRenderSources.put(role, source);
            }
        }
        JSONObject fillers = root.optJSONObject("fillers");
        if (fillers != null) {
            for (Iterator<String> it = fillers.keys(); it.hasNext(); ) {
                String blockKindName = it.next();
                BlockKind kind = BlockKind.valueOf(blockKindName);
                requireUnique("filler mapping", kind, fillerSources, source);
                fillerParams.put(kind, parseFillerParams(fillers.getJSONObject(blockKindName)));
                fillerSources.put(kind, source);
            }
        }
        if (root.has("macroHeight")) {
            // The unitless 0..1 scale that key carried is gone. Reading those
            // numbers as metres would silently make a wall 0.9 m tall and its
            // sun shadow a third of the length it should be, so refuse the
            // document rather than reinterpret it.
            throw new JSONException("Obsolete 'macroHeight' section in " + source.describe()
                    + ": macro heights are now authored in metres under 'macroHeightMeters'"
                    + " (1 cell = 1 metre; a wall is about "
                    + DEFAULT_WALL_MACRO_HEIGHT_METERS + " m).");
        }
        JSONObject macroHeight = root.optJSONObject("macroHeightMeters");
        if (macroHeight != null) {
            for (Iterator<String> it = macroHeight.keys(); it.hasNext(); ) {
                String key = it.next();
                if (SurfaceRole.fromKeyOrNull(key) == null) GroundKind.valueOf(key);
                requireUnique("macro-height mapping", key, macroHeightSources, source);
                macroHeightOverride.put(key, (float) macroHeight.getDouble(key));
                macroHeightSources.put(key, source);
            }
        }
    }

    private static <K> void requireUnique(String kind, K id,
                                          Map<K, CatalogSource> sources,
                                          CatalogSource source) throws JSONException {
        CatalogSource previous = sources.get(id);
        if (previous != null) {
            throw new JSONException("Duplicate " + kind + " '" + id
                    + "': first declared by " + previous.describe()
                    + ", then by " + source.describe());
        }
    }

    private static FillerParams parseFillerParams(JSONObject o) throws JSONException {
        List<GroundKind> groundKinds = new ArrayList<>();
        List<Integer> groundWeights = new ArrayList<>();
        JSONArray pool = o.optJSONArray("groundPool");
        if (pool != null) {
            for (int i = 0; i < pool.length(); i++) {
                JSONObject e = pool.getJSONObject(i);
                groundKinds.add(GroundKind.valueOf(e.getString("kind")));
                groundWeights.add(e.getInt("w"));
            }
        }
        return new FillerParams(groundKinds, groundWeights,
                (float) o.optDouble("plantChance", 0.0),
                (float) o.optDouble("rockChance", 0.0),
                parseStringArray(o.optJSONArray("plantPool")),
                parseStringArray(o.optJSONArray("rockPool")));
    }

    private static List<String> parseStringArray(JSONArray arr) throws JSONException {
        if (arr == null) return List.of();
        List<String> out = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        return out;
    }

    /**
     * The tileset block/tile id {@code kind}'s primary surface renders as, or
     * {@code null} if unmapped (e.g. the special-cased SIDEWALK / dead SNOW).
     * {@code GroundRenderSystem} resolves it against the {@link TileRegistry}.
     */
    public String groundBlockId(GroundKind kind) {
        return groundRender.get(kind);
    }

    /**
     * The tileset block/tile id {@code role} renders as, or {@code null} if
     * unmapped — in which case the consumer keeps its compiled fallback, the
     * same way an absent {@code groundRender} entry leaves a kind undrawn.
     *
     * <p>This is what makes a second authored wall reachable: the renderer asks
     * for the wall, not for {@code urban.wall}.
     */
    public String surfaceBlockId(SurfaceRole role) {
        return surfaceRender.get(role);
    }

    /**
     * The block id {@code role} resolves to right now — the installed mapping's
     * entry, else {@link SurfaceRole#shippedBlockId()}. Never {@code null}, so
     * a caller with no catalog still draws this mod's own art.
     */
    public static String installedSurfaceBlockId(SurfaceRole role) {
        GenMappingRegistry mapping = installed();
        String id = (mapping == null) ? null : mapping.surfaceBlockId(role);
        return id != null ? id : role.shippedBlockId();
    }

    /**
     * The {@link GridBlockDef} {@code role} resolves to, or {@code null} when no
     * tile catalog is installed or the mapped id names no block. Resolves the
     * role for callers that hold no mapping of their own, such as preview
     * scenes and the render passes that need the block once per frame.
     */
    public static GridBlockDef installedSurfaceBlock(SurfaceRole role) {
        TileRegistry tiles = TileRegistry.installed();
        return (tiles == null) ? null : tiles.block(installedSurfaceBlockId(role));
    }

    /** The code filler's data tunables for {@code kind}, or {@code null} if none authored. */
    public FillerParams fillerParams(BlockKind kind) {
        return fillerParams.get(kind);
    }

    /**
     * Sane per-{@link GroundKind} macro-height default, <b>in metres above the
     * ground datum</b>: ordinary ground is the datum itself, a building floor
     * stands on its slab, craters and water cut below it.
     *
     * <p>Metres rather than an invented 0..1 scale because
     * {@link com.dillon.starsectormarines.battle.air.AirScale#METERS_PER_CELL}
     * already anchors this world at one cell per metre. That makes every relief
     * number checkable against something real, and it is what lets a sun at a
     * stated elevation cast a shadow of the right length with nothing to
     * calibrate: a 3 m wall under a 30° sun reaches 5.2 m, which is 5.2 cells.
     *
     * <p>{@link #macroHeightMeters(GroundKind)} /
     * {@link #wallMacroHeightMeters()} prefer a {@code "macroHeightMeters"}
     * mapping-JSON override for the same key ({@code kind.name()}, or
     * {@code "WALL"}) over this table.
     */
    private static float defaultMacroHeightMeters(GroundKind kind) {
        switch (kind) {
            case INDOOR: return 0.30f;  // building floor — up a slab's step
            case RUBBLE: return -0.25f; // craters/rubble — scooped out
            case WATER:  return -0.50f; // lowest
            default:     return 0f;     // ordinary ground — the datum
        }
    }

    /**
     * Walls aren't a {@link GroundKind} ({@code CellTopology.isWall} is
     * orthogonal) — one storey, in the same metres.
     *
     * <p>A single height for every wall is the current limit of the model: a
     * compound's perimeter and a habitat's outer shell cast the same shadow.
     * Per-{@link SurfaceRole} heights are the natural next authoring step, and
     * the override map is already keyed to accept them.
     */
    public static final float DEFAULT_WALL_MACRO_HEIGHT_METERS = 3.0f;

    /** {@code kind}'s macro height in metres — {@code "macroHeightMeters"} override, else {@link #defaultMacroHeightMeters}. */
    public float macroHeightMeters(GroundKind kind) {
        Float override = macroHeightOverride.get(kind.name());
        return override != null ? override : defaultMacroHeightMeters(kind);
    }

    /** Wall macro height in metres — {@code "macroHeightMeters": {"WALL": ...}} override, else {@link #DEFAULT_WALL_MACRO_HEIGHT_METERS}. */
    public float wallMacroHeightMeters() {
        Float override = macroHeightOverride.get("WALL");
        return override != null ? override : DEFAULT_WALL_MACRO_HEIGHT_METERS;
    }

    /**
     * The tallest macro height any cell of this mapping can report, in metres.
     *
     * <p>The sun-shadow march needs a finite distance to search, and the
     * tallest thing that can stand on the map is what sets it: nothing reaches
     * further than {@code tallest / tan(elevation)}. Reading it from the data
     * means authoring a taller wall lengthens the search on its own, instead of
     * quietly clipping every shadow against a constant nobody updated.
     */
    public float tallestMacroHeightMeters() {
        float tallest = wallMacroHeightMeters();
        for (GroundKind kind : GroundKind.values()) {
            tallest = Math.max(tallest, macroHeightMeters(kind));
        }
        for (Float override : macroHeightOverride.values()) {
            tallest = Math.max(tallest, override);
        }
        return tallest;
    }

    /** The raw doodad ids for {@code poolId} (empty if none authored). */
    public List<String> doodadPoolIds(String poolId) {
        return doodadPoolIds.getOrDefault(poolId, List.of());
    }

    /**
     * Every authored pool name, in ingest order.
     *
     * <p>What separates a prop from the rest of a sheet is that a pool names it:
     * a doodad a pool can scatter is something a marine walks up to, while a
     * deck marking is art laid by id and nothing else. That distinction has no
     * other home — a {@link DoodadDef} does not know whether anything scatters
     * it — so the pools are where it has to be asked.
     */
    public Set<String> doodadPoolNames() {
        return Collections.unmodifiableSet(doodadPoolIds.keySet());
    }

    public CatalogSource sourceOfDoodadPool(String poolId) {
        return doodadPoolSources.get(poolId);
    }

    /** Validates every mapping reference against the complete installed tile catalog. */
    public void validateReferences() {
        TileRegistry tiles = TileRegistry.installed();
        if (tiles == null) {
            throw new IllegalStateException("GenMappingRegistry: TileRegistry not installed");
        }
        for (Map.Entry<String, List<String>> pool : doodadPoolIds.entrySet()) {
            for (String id : pool.getValue()) {
                if (!tiles.hasDoodad(id)) {
                    throw new IllegalStateException("GenMappingRegistry: doodad pool '"
                            + pool.getKey() + "' from "
                            + doodadPoolSources.get(pool.getKey()).describe()
                            + " references unknown doodad id '" + id + "'");
                }
            }
        }
        for (Map.Entry<GroundKind, String> entry : groundRender.entrySet()) {
            String id = entry.getValue();
            if (!tiles.has(id) && !tiles.hasBlock(id)) {
                throw new IllegalStateException("GenMappingRegistry: ground render mapping '"
                        + entry.getKey() + "' from "
                        + groundRenderSources.get(entry.getKey()).describe()
                        + " references unknown tile or block id '" + id + "'");
            }
        }
        for (Map.Entry<SurfaceRole, String> entry : surfaceRender.entrySet()) {
            String id = entry.getValue();
            if (!tiles.has(id) && !tiles.hasBlock(id)) {
                throw new IllegalStateException("GenMappingRegistry: surface render mapping '"
                        + entry.getKey() + "' from "
                        + surfaceRenderSources.get(entry.getKey()).describe()
                        + " references unknown tile or block id '" + id + "'");
            }
        }
        for (Map.Entry<BlockKind, FillerParams> entry : fillerParams.entrySet()) {
            validateTilePool(entry.getKey(), "plantPool", entry.getValue().plantPool(), tiles);
            validateTilePool(entry.getKey(), "rockPool", entry.getValue().rockPool(), tiles);
        }
    }

    private void validateTilePool(BlockKind kind, String poolName, List<String> ids,
                                  TileRegistry tiles) {
        for (String id : ids) {
            if (!tiles.has(id)) {
                throw new IllegalStateException("GenMappingRegistry: filler mapping '"
                        + kind + "' " + poolName + " from "
                        + fillerSources.get(kind).describe()
                        + " references unknown tile id '" + id + "'");
            }
        }
    }

    /**
     * The resolved doodad pool for {@code poolId} — each id looked up in
     * {@link TileRegistry#installed()}. Throws if the registry isn't installed or
     * an id is unknown (an authored pool must resolve; a typo is a bug to surface).
     */
    public List<DoodadDef> doodadPool(String poolId) {
        List<String> ids = doodadPoolIds(poolId);
        if (ids.isEmpty()) return List.of();
        TileRegistry tiles = TileRegistry.installed();
        if (tiles == null) {
            throw new IllegalStateException("GenMappingRegistry: TileRegistry not installed — cannot resolve doodad pool " + poolId);
        }
        List<DoodadDef> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            DoodadDef def = tiles.doodad(id);
            if (def == null) {
                throw new IllegalStateException("GenMappingRegistry: doodad pool '" + poolId + "' references unknown doodad id '" + id + "'");
            }
            out.add(def);
        }
        return out;
    }

    /** Convenience for theme-keyed callers — the pool whose id is {@code theme.name()}. */
    public List<DoodadDef> doodadPool(DistrictTheme theme) {
        return doodadPool(theme.name());
    }

    /**
     * Loads every {@link #BUILTIN_MAPPINGS} resource via the modded-JSON path and
     * installs the result. Defensive — a failure logs and leaves any prior install
     * in place rather than throwing out of {@code onApplicationLoad}. Call after
     * {@link TileRegistry#loadBuiltins()} so id resolution has tiles to resolve against.
     */
    public static void loadBuiltins() {
        try {
            GenMappingRegistry reg = new GenMappingRegistry();
            for (String path : BUILTIN_MAPPINGS) {
                JSONObject root = Global.getSettings().loadJSON(path, true);
                reg.ingest(root, CatalogSource.unspecified(path));
            }
            reg.validateReferences();
            installed = reg;
            LOG.info("GenMappingRegistry: loaded " + BUILTIN_MAPPINGS.size() + " built-in mapping(s)");
        } catch (Exception e) {
            LOG.error("GenMappingRegistry: failed to load built-in mappings — registry not installed", e);
        }
    }

    /** Loads every enabled-mod mapping contribution in manifest order. */
    public static void loadContributions(List<CatalogFile> catalogs) {
        GenMappingRegistry registry = new GenMappingRegistry();
        for (CatalogFile catalog : catalogs) {
            try {
                registry.ingest(catalog.loadJson(), catalog.source());
            } catch (Exception failure) {
                throw new IllegalStateException("Failed to ingest tile mapping "
                        + catalog.source().describe(), failure);
            }
        }
        registry.validateReferences();
        installed = registry;
        LOG.info("GenMappingRegistry: loaded " + catalogs.size()
                + " contributed tile mapping(s)");
    }
}
