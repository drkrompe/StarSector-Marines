package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runtime catalog for authored squad-loadout tier, rarity, provenance, and lore. */
public final class SquadLoadoutPresentationRegistry {

    private static final Logger LOG = Global.getLogger(SquadLoadoutPresentationRegistry.class);

    public static final List<String> BUILTIN_CATALOGS = List.of(
            "data/marines/squad-loadout-presentations.loadout.json");

    private static volatile SquadLoadoutPresentationRegistry installed;

    private final Map<String, SquadLoadoutPresentationDef> byId = new LinkedHashMap<>();

    public static SquadLoadoutPresentationRegistry installed() {
        return installed;
    }

    public static void install(SquadLoadoutPresentationRegistry registry) {
        installed = registry;
    }

    public static void loadBuiltins() {
        SquadLoadoutPresentationRegistry registry = new SquadLoadoutPresentationRegistry();
        for (String path : BUILTIN_CATALOGS) {
            try {
                registry.ingest(Global.getSettings().loadJSON(path, true));
            } catch (Exception failure) {
                throw new IllegalStateException("Failed to load squad loadout catalog " + path,
                        failure);
            }
        }
        registry.validateBuiltins();
        install(registry);
        LOG.info("Squad loadout presentation catalog installed with "
                + registry.byId.size() + " entries");
    }

    public void ingest(JSONObject root) throws JSONException {
        JSONArray entries = root.getJSONArray("loadouts");
        for (int index = 0; index < entries.length(); index++) {
            SquadLoadoutPresentationDef def = SquadLoadoutPresentationDef.parse(
                    entries.getJSONObject(index));
            if (byId.put(def.id(), def) != null) {
                throw new JSONException("Duplicate squad loadout presentation id '"
                        + def.id() + "'");
            }
        }
    }

    public void validateBuiltins() {
        for (SquadWeaponDoctrine doctrine : SquadEquipmentDoctrines.weaponDoctrines()) {
            requireKind(doctrine.id(), SquadLoadoutPresentationDef.Kind.WEAPON);
        }
        for (SquadArmorPlan plan : SquadEquipmentDoctrines.armorPlans()) {
            requireKind(plan.id(), SquadLoadoutPresentationDef.Kind.ARMOR);
        }
    }

    public static SquadLoadoutPresentationDef get(String id) {
        return installed != null && id != null ? installed.byId.get(id) : null;
    }

    private void requireKind(String id, SquadLoadoutPresentationDef.Kind kind) {
        SquadLoadoutPresentationDef def = byId.get(id);
        if (def == null) {
            throw new IllegalStateException("Missing squad loadout presentation '" + id + "'");
        }
        if (def.kind() != kind) {
            throw new IllegalStateException("Squad loadout presentation '" + id
                    + "' has kind " + def.kind() + ", expected " + kind);
        }
    }
}
