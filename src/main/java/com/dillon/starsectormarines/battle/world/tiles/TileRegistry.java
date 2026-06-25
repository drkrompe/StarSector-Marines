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
            "data/tilesets/urban-tileset-3.tileset.json");

    private static volatile TileRegistry installed;

    private final Map<String, TileDef> byId = new LinkedHashMap<>();

    public TileDef tile(String id)      { return byId.get(id); }
    public boolean has(String id)       { return byId.containsKey(id); }
    public Collection<TileDef> all()    { return byId.values(); }
    public int size()                   { return byId.size(); }

    /** The registry installed at application load, or {@code null} if load failed / hasn't run. */
    public static TileRegistry installed() { return installed; }

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
        if (tiles == null) return;
        for (int i = 0; i < tiles.length(); i++) {
            JSONObject o = tiles.getJSONObject(i);
            String id = o.getString("id");
            if (byId.containsKey(id)) {
                throw new IllegalStateException("TileRegistry: duplicate tile id '" + id
                        + "' (sheet " + sheet + ")");
            }
            int frame = o.optInt("frame", -1);
            TileLayer layer = TileLayer.fromJson(o.optString("layer", "ground"));
            TileCover cover = TileCover.fromJson(o.optString("cover", "none"));
            boolean passable = o.optBoolean("passable", true);
            List<String> validOn = new ArrayList<>();
            JSONArray vo = o.optJSONArray("validOn");
            if (vo != null) {
                for (int k = 0; k < vo.length(); k++) validOn.add(vo.getString(k));
            }
            byId.put(id, new TileDef(id, sheet, frame, layer, cover, passable, validOn));
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
                if (ref.startsWith("layer:")) continue;
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
