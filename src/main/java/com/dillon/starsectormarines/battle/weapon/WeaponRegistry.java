package com.dillon.starsectormarines.battle.weapon;

import com.dillon.starsectormarines.catalog.CatalogSource;
import com.dillon.starsectormarines.catalog.MarineCatalogManifest.CatalogFile;
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
 * {@link JSONObject} read straight off disk; {@link #loadContributions(List)}
 * is the in-game path that installs every enabled provider's explicit resources.
 *
 * <p><b>Fail loud.</b> Unlike the tile registry, which degrades to "no
 * overlay scatter" when absent, a missing weapon registry would mean every
 * gun in the game reads zero range and zero damage. {@link #require} throws
 * rather than returning a harmless default, so a wiring mistake surfaces at
 * load instead of as a silently unwinnable battle.
 */
public final class WeaponRegistry {

    private static final Logger LOG = Global.getLogger(WeaponRegistry.class);

    /** Safe repair target for missing persisted marine-primary ids. */
    public static final String STARTER_PRIMARY_ID = "weapon.field-rifle";
    public static final String PULSE_RIFLE_ID = "weapon.pulse-rifle";
    public static final String SMG_ID = "weapon.smg";
    public static final String SQUAD_AUTOMATIC_ID = "weapon.squad-automatic";
    public static final String DMR_ID = "weapon.dmr";
    public static final String DRONE_PULSE_ID = "weapon.drone-pulse";
    public static final String MECH_CHAINGUN_ID = "weapon.mech-chaingun";
    public static final String MECH_LINEAR_CANNON_ID = "weapon.mech-linear-cannon";
    public static final String MECH_HEAVY_CANNON_ID = "weapon.mech-heavy-cannon";
    public static final String MECH_SRM_POD_ID = "weapon.mech-srm-pod";
    public static final String MECH_LRM_ARTILLERY_ID = "weapon.mech-lrm-artillery";
    public static final String MECH_SHOULDER_LASER_ID = "weapon.mech-shoulder-laser";
    public static final String MECH_PULSE_LASER_ID = "weapon.mech-pulse-laser";

    /**
     * Core resources retained for standalone tools and compatibility tests.
     * Production discovers the core manifest alongside every enabled provider.
     */
    public static final List<String> BUILTIN_CATALOGS = List.of(
            "data/marines/marine-weapons.weapon.json",
            "data/marines/turret-weapons.weapon.json");

    private static volatile WeaponRegistry installed;

    private final Map<String, WeaponDef> byId = new LinkedHashMap<>();
    private final Map<String, CatalogSource> sourceById = new LinkedHashMap<>();

    /** The installed registry, or null before application catalog loading has run. */
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

    /** Loads every enabled-mod contribution in manifest order. */
    public static void loadContributions(List<CatalogFile> catalogs) {
        WeaponRegistry registry = new WeaponRegistry();
        for (CatalogFile catalog : catalogs) {
            try {
                registry.ingest(catalog.loadJson(), catalog.source());
            } catch (Exception failure) {
                throw new IllegalStateException("Failed to ingest weapon catalog "
                        + catalog.source().describe(), failure);
            }
        }
        install(registry);
        LOG.info("Weapon registry installed with " + registry.size() + " weapons from "
                + catalogs.size() + " contributed catalogs");
    }

    /** Adds every entry in one catalog file. Duplicate ids are an authoring error, not a silent override. */
    public void ingest(JSONObject root) throws JSONException {
        ingest(root, CatalogSource.unspecified("<in-memory weapon catalog>"));
    }

    public void ingest(JSONObject root, CatalogSource source) throws JSONException {
        JSONArray weapons = root.getJSONArray("weapons");
        for (int i = 0; i < weapons.length(); i++) {
            WeaponDef def = WeaponDef.parse(weapons.getJSONObject(i));
            WeaponDef previous = byId.get(def.id);
            if (previous != null) {
                throw new JSONException("Duplicate weapon id '" + def.id + "': first declared by "
                        + sourceById.get(def.id).describe() + ", then by " + source.describe());
            }
            byId.put(def.id, def);
            sourceById.put(def.id, source);
        }
    }

    public CatalogSource sourceOf(String id) {
        return sourceById.get(id);
    }

    /**
     * The def for {@code id}. Throws when the registry is missing or the id
     * is unknown — see the fail-loud note on this class.
     */
    public static WeaponDef require(String id) {
        WeaponRegistry registry = installed;
        if (registry == null) {
            throw new IllegalStateException("Weapon registry is not installed; cannot resolve '" + id
                    + "'. In-game this is manifest contribution loading at application load;"
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

    /**
     * Translates the names written by the retired {@code MarineWeapon} enum.
     * Stable ids pass through unchanged so one string field can accept both
     * historical and current save data during {@code readResolve}.
     */
    public static String legacyMarinePrimaryId(String savedValue) {
        if (savedValue == null || savedValue.isBlank()) return null;
        if (savedValue.startsWith("weapon.")) return savedValue;
        return switch (savedValue) {
            case "FIELD_RIFLE" -> STARTER_PRIMARY_ID;
            case "PULSE_RIFLE" -> "weapon.pulse-rifle";
            case "SMG" -> "weapon.smg";
            case "SQUAD_AUTOMATIC" -> "weapon.squad-automatic";
            case "DMR" -> "weapon.dmr";
            case "DRONE_PULSE" -> "weapon.drone-pulse";
            default -> null;
        };
    }
}
