package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.model.DistrictTheme;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime catalog of the <em>generation mapping</em> — the "how tiles map to
 * generated things" data half of moddable-tilesets Phase 2, loaded from
 * {@code data/tilesets/*.mapping.json}. Sibling to {@link TileRegistry}: that
 * one owns the tile/doodad <em>defs</em> (what art exists); this one owns how
 * gen <em>uses</em> them (pools, and — later slices — {@code GroundKind} render
 * dispatch + per-{@code BlockKind} filler params).
 *
 * <p>The data/algorithm seam holds: pools/membership are data here; the scatter
 * and carve algorithms stay in Java (the fillers). See
 * {@code roadmap/moddable-tilesets/stories/phase-2-doodad-pools.md}.
 *
 * <p>v1 carries only the doodad pools (theme &rarr; doodad ids). Ids resolve
 * against {@link TileRegistry#installed()} — fail-loud on an unknown id.
 */
public final class GenMappingRegistry {

    private static final Logger LOG = Global.getLogger(GenMappingRegistry.class);

    /** Built-in mapping resources bundled with the mod. Phase 3 replaces this with discovery + merge. */
    public static final List<String> BUILTIN_MAPPINGS = List.of(
            "data/tilesets/urban.mapping.json");

    private static volatile GenMappingRegistry installed;

    /** Theme -> ordered doodad ids. Resolved against the TileRegistry on access. */
    private final Map<DistrictTheme, List<String>> doodadPoolIds = new EnumMap<>(DistrictTheme.class);

    /** The mapping installed at application load, or {@code null} if load failed / hasn't run. */
    public static GenMappingRegistry installed() { return installed; }

    /** Installs {@code reg} as the process-wide mapping. Tests use this with a disk-loaded instance. */
    public static void install(GenMappingRegistry reg) { installed = reg; }

    /** Parses one mapping JSON document and merges its sections. */
    public void ingest(JSONObject root) throws JSONException {
        JSONObject pools = root.optJSONObject("doodadPools");
        if (pools != null) {
            for (DistrictTheme theme : DistrictTheme.values()) {
                JSONArray arr = pools.optJSONArray(theme.name());
                if (arr == null) continue;
                List<String> ids = new ArrayList<>(arr.length());
                for (int i = 0; i < arr.length(); i++) ids.add(arr.getString(i));
                doodadPoolIds.put(theme, ids);
            }
        }
    }

    /** The raw doodad ids for {@code theme} (empty if none authored). */
    public List<String> doodadPoolIds(DistrictTheme theme) {
        return doodadPoolIds.getOrDefault(theme, List.of());
    }

    /**
     * The resolved doodad pool for {@code theme} — each id looked up in
     * {@link TileRegistry#installed()}. Throws if the registry isn't installed or
     * an id is unknown (an authored pool must resolve; a typo is a bug to surface).
     */
    public List<DoodadDef> doodadPool(DistrictTheme theme) {
        List<String> ids = doodadPoolIds(theme);
        if (ids.isEmpty()) return List.of();
        TileRegistry tiles = TileRegistry.installed();
        if (tiles == null) {
            throw new IllegalStateException("GenMappingRegistry: TileRegistry not installed — cannot resolve doodad pool " + theme);
        }
        List<DoodadDef> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            DoodadDef def = tiles.doodad(id);
            if (def == null) {
                throw new IllegalStateException("GenMappingRegistry: doodad pool " + theme + " references unknown doodad id '" + id + "'");
            }
            out.add(def);
        }
        return out;
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
                reg.ingest(root);
            }
            installed = reg;
            LOG.info("GenMappingRegistry: loaded " + BUILTIN_MAPPINGS.size() + " built-in mapping(s)");
        } catch (Exception e) {
            LOG.error("GenMappingRegistry: failed to load built-in mappings — registry not installed", e);
        }
    }
}
