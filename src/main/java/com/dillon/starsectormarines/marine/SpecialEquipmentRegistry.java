package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
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

    public static final List<String> BUILTIN_CATALOGS = List.of(
            "data/marines/marine-special-equipment.equipment.json");

    private static volatile SpecialEquipmentRegistry installed;

    private final Map<String, SpecialEquipmentDef> byId = new LinkedHashMap<>();

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

    public void ingest(JSONObject root) throws JSONException {
        JSONArray equipment = root.getJSONArray("equipment");
        for (int i = 0; i < equipment.length(); i++) {
            SpecialEquipmentDef def = SpecialEquipmentDef.parse(equipment.getJSONObject(i));
            SpecialEquipmentDef previous = byId.put(def.id(), def);
            if (previous != null) {
                throw new JSONException("Duplicate special-equipment id '" + def.id() + "'");
            }
        }
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

    public static MarineSecondary compatibilityHandle(String id) {
        if (ROCKET_LAUNCHER_ID.equals(id)) return MarineSecondary.ROCKET_LAUNCHER;
        if (ANTI_MATERIEL_RIFLE_ID.equals(id)) return MarineSecondary.ANTI_MATERIEL_RIFLE;
        if (SMOKE_GRENADE_ID.equals(id)) return MarineSecondary.SMOKE_GRENADE;
        if (SATCHEL_CHARGE_ID.equals(id)) return MarineSecondary.SATCHEL_CHARGE;
        return null;
    }
}
