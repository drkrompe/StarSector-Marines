package com.dillon.starsectormarines.battle.turret;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Fail-loud registry for bounded data-authored defense-post stamps. */
public final class DefensePostLayoutRegistry {

    private static final Logger LOG = Global.getLogger(DefensePostLayoutRegistry.class);

    public static final List<String> BUILTIN_CATALOGS = List.of(
            "data/marines/defense-post-layouts.layout.json");

    private static volatile DefensePostLayoutRegistry installed;

    private final Map<String, DefensePostLayoutDef> byId = new LinkedHashMap<>();
    private final Map<DefensePostKind, List<DefensePostLayoutDef>> byTier =
            new EnumMap<>(DefensePostKind.class);
    private final Map<String, DefensePostLayoutDef> byTierVariant = new LinkedHashMap<>();

    public static DefensePostLayoutRegistry installed() {
        return installed;
    }

    public static void install(DefensePostLayoutRegistry registry) {
        installed = registry;
    }

    /** Loads after turret structures so every placement resolves immediately. */
    public static void loadBuiltins() {
        TurretCatalogRegistry turrets = TurretCatalogRegistry.installed();
        if (turrets == null) {
            throw new IllegalStateException(
                    "Turret catalog must be installed before defense-post layouts");
        }
        DefensePostLayoutRegistry registry = new DefensePostLayoutRegistry();
        for (String path : BUILTIN_CATALOGS) {
            try {
                registry.ingest(Global.getSettings().loadJSON(path, true), turrets);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load defense-post layouts " + path, e);
            }
        }
        try {
            registry.validateCompleteness();
        } catch (JSONException e) {
            throw new IllegalStateException("Incomplete built-in defense-post layouts", e);
        }
        install(registry);
        LOG.info("Defense-post layout registry installed with " + registry.size() + " layouts");
    }

    public void ingest(JSONObject root, TurretCatalogRegistry turrets) throws JSONException {
        if (turrets == null) throw new JSONException("Turret catalog is required for layout ingestion");
        JSONArray layouts = root.getJSONArray("layouts");
        for (int i = 0; i < layouts.length(); i++) {
            DefensePostLayoutDef def = DefensePostLayoutDef.parse(
                    layouts.getJSONObject(i), turrets);
            if (byId.put(def.id, def) != null) {
                throw new JSONException("Duplicate defense-post layout id '" + def.id + "'");
            }
            String key = key(def.tier, def.variant);
            if (byTierVariant.put(key, def) != null) {
                throw new JSONException("Duplicate defense-post layout variant '"
                        + def.tier + "/" + def.variant + "'");
            }
            byTier.computeIfAbsent(def.tier, ignored -> new ArrayList<>()).add(def);
        }
    }

    public void validateCompleteness() throws JSONException {
        for (DefensePostKind tier : DefensePostKind.values()) {
            List<DefensePostLayoutDef> layouts = byTier.get(tier);
            if (layouts == null || layouts.isEmpty()) {
                throw new JSONException("No defense-post layout exists for tier " + tier);
            }
            if (tier != DefensePostKind.LARGE && layouts.size() != 1) {
                throw new JSONException("Tier " + tier
                        + " must have exactly one layout; found " + layouts.size());
            }
        }
    }

    public DefensePostLayoutDef get(String id) {
        return byId.get(id);
    }

    public DefensePostLayoutDef require(DefensePostKind tier, String variant) {
        DefensePostLayoutDef layout = byTierVariant.get(key(tier, variant));
        if (layout == null) {
            throw new IllegalStateException("Unknown defense-post layout " + tier + "/" + variant);
        }
        return layout;
    }

    public List<DefensePostLayoutDef> layoutsFor(DefensePostKind tier) {
        List<DefensePostLayoutDef> layouts = byTier.get(tier);
        return layouts != null ? List.copyOf(layouts) : List.of();
    }

    /** Preserves RNG cadence: singleton tiers consume no draw; LARGE consumes one. */
    public DefensePostLayoutDef pick(DefensePostKind tier, Random rng) {
        List<DefensePostLayoutDef> layouts = layoutsFor(tier);
        if (layouts.isEmpty()) {
            throw new IllegalStateException("No defense-post layout exists for tier " + tier);
        }
        return layouts.size() == 1 ? layouts.get(0) : layouts.get(rng.nextInt(layouts.size()));
    }

    public Collection<DefensePostLayoutDef> layouts() {
        return List.copyOf(byId.values());
    }

    public int size() {
        return byId.size();
    }

    public static DefensePostLayoutRegistry requireInstalled() {
        DefensePostLayoutRegistry registry = installed;
        if (registry == null) {
            throw new IllegalStateException("Defense-post layout registry is not installed");
        }
        return registry;
    }

    private static String key(DefensePostKind tier, String variant) {
        return tier.name() + "\u0000" + variant;
    }
}
