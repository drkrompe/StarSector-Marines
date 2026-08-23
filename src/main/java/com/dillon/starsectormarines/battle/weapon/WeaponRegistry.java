package com.dillon.starsectormarines.battle.weapon;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime catalog of {@link WeaponDef}s loaded from
 * {@code data/marines/*.weapon.json}, addressed by stable string id. The
 * weapon half of the same store/consumer split
 * {@link com.dillon.starsectormarines.battle.world.tiles.TileRegistry} uses
 * for tiles — the registry owns the data, the firing and presentation
 * systems consume it by id. See {@code moddable-weapons-nouns.md}.
 *
 * <p>Parsing ({@link #ingest}) is decoupled from the game's
 * {@link com.fs.starfarer.api.SettingsAPI} so tests can feed a
 * {@link JSONObject} read straight off disk; {@link #loadBuiltins()} is the
 * in-game path that pulls the bundled resources and installs the result.
 *
 * <p><b>Fail loud.</b> Unlike the tile registry, which degrades to "no
 * overlay scatter" when absent, a missing weapon registry would mean every
 * gun in the game reads zero range and zero damage. {@link #require} throws
 * rather than returning a harmless default, so a wiring mistake surfaces at
 * load instead of as a silently unwinnable battle.
 */
public final class WeaponRegistry {

    private static final Logger LOG = Global.getLogger(WeaponRegistry.class);

    /**
     * Built-in weapon catalogs bundled with the mod. W5 (submod merge)
     * replaces this fixed list with discovery across enabled mods; until a
     * real submod exists the bundled files are listed explicitly, matching
     * the call {@code TileRegistry.BUILTIN_TILESETS} made.
     */
    public static final List<String> BUILTIN_CATALOGS = List.of(
            "data/marines/marine-weapons.weapon.json");

    private static volatile WeaponRegistry installed;

    private final Map<String, WeaponDef> byId = new LinkedHashMap<>();

    /** The installed registry, or null before {@link #loadBuiltins()} has run. */
    public static WeaponRegistry installed() {
        return installed;
    }

    public static void install(WeaponRegistry registry) {
        installed = registry;
    }

    /**
     * Parses and installs the bundled catalogs. Called once from
     * {@code onApplicationLoad}, before any save exists.
     */
    public static void loadBuiltins() {
        WeaponRegistry registry = new WeaponRegistry();
        for (String path : BUILTIN_CATALOGS) {
            try {
                registry.ingest(Global.getSettings().loadJSON(path, true));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load weapon catalog " + path, e);
            }
        }
        install(registry);
        LOG.info("Weapon registry installed with " + registry.size() + " weapons");
    }

    /** Adds every entry in one catalog file. Duplicate ids are an authoring error, not a silent override. */
    public void ingest(JSONObject root) throws JSONException {
        JSONArray weapons = root.getJSONArray("weapons");
        for (int i = 0; i < weapons.length(); i++) {
            WeaponDef def = WeaponDef.parse(weapons.getJSONObject(i));
            WeaponDef previous = byId.put(def.id, def);
            if (previous != null) {
                throw new JSONException("Duplicate weapon id '" + def.id + "'");
            }
        }
    }

    /**
     * The def for {@code id}. Throws when the registry is missing or the id
     * is unknown — see the fail-loud note on this class.
     */
    public static WeaponDef require(String id) {
        WeaponRegistry registry = installed;
        if (registry == null) {
            throw new IllegalStateException("Weapon registry is not installed; cannot resolve '" + id
                    + "'. In-game this is WeaponRegistry.loadBuiltins() at application load;"
                    + " in tests it is the auto-registered registry installer extension.");
        }
        WeaponDef def = registry.byId.get(id);
        if (def == null) {
            throw new IllegalStateException("Unknown weapon id '" + id + "'. Known ids: " + registry.byId.keySet());
        }
        return def;
    }

    public WeaponDef get(String id) {
        return byId.get(id);
    }

    public Collection<WeaponDef> all() {
        return byId.values();
    }

    public int size() {
        return byId.size();
    }
}
