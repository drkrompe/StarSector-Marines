package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
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

/** Runtime catalog of stable, data-authored special-equipment identities. */
public final class SpecialEquipmentRegistry {

    private static final Logger LOG = Global.getLogger(SpecialEquipmentRegistry.class);

    public static final String ROCKET_LAUNCHER_ID = "special.rocket-launcher";
    public static final String ANTI_MATERIEL_RIFLE_ID = "special.anti-materiel-rifle";
    public static final String SMOKE_GRENADE_ID = "special.smoke-grenade";
    public static final String SATCHEL_CHARGE_ID = "special.satchel-charge";
    public static final String FRAG_GRENADE_ID = "special.frag-grenade";
    public static final String BREACHING_CUTTER_ID = "special.breaching-cutter";
    public static final String VIBRO_BLADE_ID = "special.vibro-blade";

    public static final List<String> BUILTIN_CATALOGS = List.of(
            "data/marines/marine-special-equipment.equipment.json");

    private static volatile SpecialEquipmentRegistry installed;

    private final Map<String, SpecialEquipmentDef> byId = new LinkedHashMap<>();
    private final Map<String, CatalogSource> sourceById = new LinkedHashMap<>();

    public static SpecialEquipmentRegistry installed() {
        return installed;
    }

    public static void install(SpecialEquipmentRegistry registry) {
        installed = registry;
    }

    /** Loads after {@link WeaponRegistry}, so weapon-like equipment can validate its references. */
    public static void loadBuiltins() {
        SpecialEquipmentRegistry registry = new SpecialEquipmentRegistry();
        for (String path : BUILTIN_CATALOGS) {
            try {
                registry.ingest(Global.getSettings().loadJSON(path, true));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load special-equipment catalog " + path, e);
            }
        }
        registry.validateReferences();
        install(registry);
        LOG.info("Special-equipment registry installed with " + registry.size() + " items");
    }

    public static void loadContributions(List<CatalogFile> catalogs) {
        SpecialEquipmentRegistry registry = new SpecialEquipmentRegistry();
        for (CatalogFile catalog : catalogs) {
            try {
                registry.ingest(catalog.loadJson(), catalog.source());
            } catch (Exception failure) {
                throw new IllegalStateException("Failed to ingest special-equipment catalog "
                        + catalog.source().describe(), failure);
            }
        }
        registry.validateReferences();
        install(registry);
        LOG.info("Special-equipment registry installed with " + registry.size()
                + " items from " + catalogs.size() + " contributed catalogs");
    }

    public void ingest(JSONObject root) throws JSONException {
        ingest(root, CatalogSource.unspecified("<in-memory special-equipment catalog>"));
    }

    public void ingest(JSONObject root, CatalogSource source) throws JSONException {
        JSONArray equipment = root.getJSONArray("equipment");
        for (int i = 0; i < equipment.length(); i++) {
            SpecialEquipmentDef def = SpecialEquipmentDef.parse(equipment.getJSONObject(i));
            SpecialEquipmentDef previous = byId.get(def.id());
            if (previous != null) {
                throw new JSONException("Duplicate special-equipment id '" + def.id()
                        + "': first declared by " + sourceById.get(def.id()).describe()
                        + ", then by " + source.describe());
            }
            byId.put(def.id(), def);
            sourceById.put(def.id(), source);
        }
    }

    public CatalogSource sourceOf(String id) {
        return sourceById.get(id);
    }

    public void validateReferences() {
        for (SpecialEquipmentDef def : byId.values()) {
            if (def.weaponId() == null) continue;
            if (WeaponRegistry.require(def.weaponId()).mount != MountClass.MARINE_SECONDARY) {
                throw new IllegalStateException("Special equipment '" + def.id()
                        + "' references weapon '" + def.weaponId()
                        + "' outside the marine-secondary mount class");
            }
        }
    }

    public static SpecialEquipmentDef require(String id) {
        SpecialEquipmentRegistry registry = installed;
        if (registry == null) {
            throw new IllegalStateException("Special-equipment registry is not installed; cannot resolve '"
                    + id + "'");
        }
        SpecialEquipmentDef def = registry.byId.get(id);
        if (def == null) {
            throw new IllegalArgumentException("Unknown special-equipment id '" + id
                    + "'. Known ids: " + registry.byId.keySet());
        }
        return def;
    }

    public static SpecialEquipmentDef get(String id) {
        SpecialEquipmentRegistry registry = installed;
        return registry != null && id != null ? registry.byId.get(id) : null;
    }

    public Collection<SpecialEquipmentDef> all() {
        return byId.values();
    }

    public int size() {
        return byId.size();
    }

    /** Translates names written by the retired {@code MarineSecondary} enum. */
    public static String legacyId(String savedValue) {
        if (savedValue == null || savedValue.isBlank()) return null;
        if (savedValue.startsWith("special.")) return savedValue;
        return switch (savedValue) {
            case "ROCKET_LAUNCHER" -> ROCKET_LAUNCHER_ID;
            case "ANTI_MATERIEL_RIFLE" -> ANTI_MATERIEL_RIFLE_ID;
            case "SMOKE_GRENADE" -> SMOKE_GRENADE_ID;
            case "SATCHEL_CHARGE" -> SATCHEL_CHARGE_ID;
            case "FRAG_GRENADE" -> FRAG_GRENADE_ID;
            default -> null;
        };
    }
}
