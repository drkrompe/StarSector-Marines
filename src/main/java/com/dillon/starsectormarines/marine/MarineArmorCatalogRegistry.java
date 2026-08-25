package com.dillon.starsectormarines.marine;

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

/** Runtime catalog for armor classification and in-universe descriptive copy. */
public final class MarineArmorCatalogRegistry {

    private static final Logger LOG = Global.getLogger(MarineArmorCatalogRegistry.class);

    public static final List<String> BUILTIN_CATALOGS = List.of(
            "data/marines/marine-armor-catalog.armor.json");

    private static volatile MarineArmorCatalogRegistry installed;

    private final Map<String, MarineArmorCatalogDef> byId = new LinkedHashMap<>();
    private final Map<String, CatalogSource> sourceById = new LinkedHashMap<>();

    public static MarineArmorCatalogRegistry installed() {
        return installed;
    }

    public static void install(MarineArmorCatalogRegistry registry) {
        installed = registry;
    }

    public static void loadBuiltins() {
        MarineArmorCatalogRegistry registry = new MarineArmorCatalogRegistry();
        for (String path : BUILTIN_CATALOGS) {
            try {
                registry.ingest(Global.getSettings().loadJSON(path, true));
            } catch (Exception failure) {
                throw new IllegalStateException("Failed to load marine armor catalog " + path,
                        failure);
            }
        }
        registry.validateCompleteness();
        install(registry);
        LOG.info("Marine armor catalog installed with " + registry.size() + " entries");
    }

    public static void loadContributions(List<CatalogFile> catalogs) {
        MarineArmorCatalogRegistry registry = new MarineArmorCatalogRegistry();
        for (CatalogFile catalog : catalogs) {
            try {
                registry.ingest(catalog.loadJson(), catalog.source());
            } catch (Exception failure) {
                throw new IllegalStateException("Failed to ingest marine armor catalog "
                        + catalog.source().describe(), failure);
            }
        }
        registry.validateCompleteness();
        install(registry);
        LOG.info("Marine armor catalog installed with " + registry.size()
                + " entries from " + catalogs.size() + " contributed catalogs");
    }

    public void ingest(JSONObject root) throws JSONException {
        ingest(root, CatalogSource.unspecified("<in-memory armor catalog>"));
    }

    public void ingest(JSONObject root, CatalogSource source) throws JSONException {
        JSONArray armor = root.getJSONArray("armor");
        for (int index = 0; index < armor.length(); index++) {
            MarineArmorCatalogDef def = MarineArmorCatalogDef.parse(armor.getJSONObject(index));
            if (byId.containsKey(def.id())) {
                throw new JSONException("Duplicate marine armor catalog id '" + def.id()
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

    public void validateCompleteness() {
        for (MarineArmorPattern pattern : MarineArmorPattern.values()) {
            if (!byId.containsKey(pattern.id)) {
                throw new IllegalStateException("Missing marine armor catalog entry '"
                        + pattern.id + "'");
            }
        }
    }

    public static MarineArmorCatalogDef require(String id) {
        MarineArmorCatalogRegistry registry = installed;
        if (registry == null) {
            throw new IllegalStateException("Marine armor catalog is not installed; cannot resolve '"
                    + id + "'");
        }
        MarineArmorCatalogDef def = registry.byId.get(id);
        if (def == null) {
            throw new IllegalArgumentException("Unknown marine armor catalog id '" + id
                    + "'. Known ids: " + registry.byId.keySet());
        }
        return def;
    }

    public Collection<MarineArmorCatalogDef> all() {
        return byId.values();
    }

    public int size() {
        return byId.size();
    }
}
