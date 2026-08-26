package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.catalog.CatalogSource;
import com.dillon.starsectormarines.catalog.MarineCatalogManifest.CatalogFile;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Additive faction availability for collectible infantry-equipment templates. */
public final class FactionEquipmentCatalog {

    private static final Logger LOG = Global.getLogger(FactionEquipmentCatalog.class);
    private static final Set<String> SOURCE_KEYS = Set.of("market", "license", "patron", "recovery");
    private static volatile FactionEquipmentCatalog installed;

    private final Map<String, MutablePool> pools = new LinkedHashMap<>();
    private final Map<Claim, CatalogSource> claimSources = new LinkedHashMap<>();
    private final Map<String, CatalogSource> exclusionSources = new LinkedHashMap<>();
    private String fallbackFactionId;

    public static FactionEquipmentCatalog installed() {
        return installed;
    }

    public static void install(FactionEquipmentCatalog catalog) {
        installed = catalog;
    }

    /** Loads after collectible templates because every offer validates its stable card id. */
    public static void loadContributions(List<CatalogFile> catalogs) {
        FactionEquipmentCatalog registry = new FactionEquipmentCatalog();
        for (CatalogFile catalog : catalogs) {
            try {
                registry.ingest(catalog.loadJson(), catalog.source());
            } catch (Exception failure) {
                throw new IllegalStateException("Failed to ingest faction-equipment catalog "
                        + catalog.source().describe(), failure);
            }
        }
        registry.validateCompleteness();
        install(registry);
        LOG.info("Faction-equipment catalog installed with " + registry.size()
                + " faction pools from " + catalogs.size() + " contributed catalogs");
    }

    public void ingest(JSONObject root) throws JSONException {
        ingest(root, CatalogSource.unspecified("<in-memory faction-equipment catalog>"));
    }

    public void ingest(JSONObject root, CatalogSource source) throws JSONException {
        String declaredFallback = optionalText(root, "fallbackFactionId");
        if (declaredFallback != null) {
            declaredFallback = normalizeFactionId(declaredFallback);
            if (fallbackFactionId != null && !fallbackFactionId.equals(declaredFallback)) {
                throw new JSONException("Conflicting faction-equipment fallbacks '"
                        + fallbackFactionId + "' and '" + declaredFallback + "'");
            }
            fallbackFactionId = declaredFallback;
        }

        JSONArray factions = root.getJSONArray("factions");
        for (int index = 0; index < factions.length(); index++) {
            parseFaction(factions.getJSONObject(index), source);
        }
    }

    public void validateCompleteness() {
        MutablePool fallback = pools.get(fallbackFactionId);
        if (fallbackFactionId == null || fallback == null || fallback.offers.isEmpty()) {
            throw new IllegalStateException("Faction-equipment fallback faction '"
                    + fallbackFactionId + "' has no player equipment pool");
        }
        for (MutablePool pool : pools.values()) {
            if (pool.offers.isEmpty() == (pool.noPlayerEquipmentReason == null)) {
                throw new IllegalStateException("Faction-equipment pool '" + pool.factionId
                        + "' requires offers or one explicit noPlayerEquipmentReason");
            }
        }
    }

    public static FactionEquipmentPool resolve(String factionId) {
        FactionEquipmentCatalog catalog = requireInstalled();
        MutablePool exact = catalog.pools.get(normalizeFactionId(factionId));
        MutablePool resolved = exact != null ? exact : catalog.pools.get(catalog.fallbackFactionId);
        return resolved.snapshot();
    }

    public static List<FactionEquipmentOffer> offers(
            String factionId, FactionEquipmentSource source) {
        return resolve(factionId).offers(source);
    }

    public static boolean hasExactFaction(String factionId) {
        return requireInstalled().pools.containsKey(normalizeFactionId(factionId));
    }

    public List<FactionEquipmentPool> entries() {
        return pools.values().stream().map(MutablePool::snapshot).toList();
    }

    public CatalogSource sourceOf(
            String factionId, String templateId, FactionEquipmentSource source) {
        return claimSources.get(new Claim(normalizeFactionId(factionId), templateId, source));
    }

    public int size() {
        return pools.size();
    }

    private void parseFaction(JSONObject json, CatalogSource source) throws JSONException {
        String factionId = normalizeFactionId(requireText(json, "factionId"));
        MutablePool pool = pools.computeIfAbsent(factionId, MutablePool::new);
        String exclusion = optionalText(json, "noPlayerEquipmentReason");
        JSONArray offers = json.optJSONArray("offers");
        if (exclusion == null && (offers == null || offers.length() == 0)) {
            throw new JSONException("Faction equipment entry '" + factionId
                    + "' requires offers or noPlayerEquipmentReason");
        }
        if (exclusion != null) {
            if (!pool.offers.isEmpty()) {
                throw new JSONException("Faction '" + factionId
                        + "' cannot exclude player equipment after declaring offers");
            }
            CatalogSource prior = exclusionSources.putIfAbsent(factionId, source);
            if (prior != null) {
                throw new JSONException("Duplicate no-player-equipment claim for faction '"
                        + factionId + "': first declared by " + prior.describe()
                        + ", then by " + source.describe());
            }
            pool.noPlayerEquipmentReason = exclusion;
        }
        if (offers == null) return;
        if (pool.noPlayerEquipmentReason != null) {
            throw new JSONException("Faction '" + factionId
                    + "' cannot declare offers with noPlayerEquipmentReason");
        }
        for (int index = 0; index < offers.length(); index++) {
            parseOffer(pool, offers.getJSONObject(index), source);
        }
    }

    private void parseOffer(MutablePool pool, JSONObject json, CatalogSource source)
            throws JSONException {
        String templateId = requireText(json, "templateId");
        EquipmentTemplateCard template;
        try {
            template = EquipmentTemplateCatalog.require(templateId);
        } catch (IllegalArgumentException failure) {
            throw new JSONException("Faction '" + pool.factionId
                    + "' references unknown equipment template '" + templateId + "'");
        }
        JSONObject sources = json.getJSONObject("sources");
        rejectUnknownSources(sources, pool.factionId, templateId);
        MutableOffer offer = pool.offers.computeIfAbsent(templateId,
                ignored -> new MutableOffer(template));
        int added = 0;
        for (FactionEquipmentSource acquisition : FactionEquipmentSource.values()) {
            String key = acquisition.name().toLowerCase(Locale.ROOT);
            if (!sources.has(key)) continue;
            int weight = sources.getInt(key);
            if (weight <= 0) {
                throw new JSONException("Faction '" + pool.factionId + "' template '"
                        + templateId + "' source '" + key + "' must have positive weight");
            }
            Claim claim = new Claim(pool.factionId, templateId, acquisition);
            CatalogSource prior = claimSources.putIfAbsent(claim, source);
            if (prior != null) {
                throw new JSONException("Duplicate faction-equipment claim for faction '"
                        + pool.factionId + "', template '" + templateId + "', source '"
                        + key + "': first declared by " + prior.describe()
                        + ", then by " + source.describe());
            }
            offer.sourceWeights.put(acquisition, weight);
            added++;
        }
        if (added == 0) {
            throw new JSONException("Faction '" + pool.factionId + "' template '"
                    + templateId + "' has no acquisition source");
        }
    }

    private static void rejectUnknownSources(JSONObject sources, String factionId,
                                             String templateId) throws JSONException {
        Iterator<?> keys = sources.keys();
        while (keys.hasNext()) {
            String key = String.valueOf(keys.next());
            if (!SOURCE_KEYS.contains(key)) {
                throw new JSONException("Faction '" + factionId + "' template '"
                        + templateId + "' has unknown acquisition source '" + key + "'");
            }
        }
    }

    private static String requireText(JSONObject json, String key) throws JSONException {
        String value = optionalText(json, key);
        if (value == null) throw new JSONException("Faction equipment entry is missing '" + key + "'");
        return value;
    }

    private static String optionalText(JSONObject json, String key) {
        String value = json.optString(key, null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeFactionId(String factionId) {
        return factionId == null ? "" : factionId.trim().toLowerCase(Locale.ROOT);
    }

    private static FactionEquipmentCatalog requireInstalled() {
        FactionEquipmentCatalog catalog = installed;
        if (catalog == null) throw new IllegalStateException("Faction-equipment catalog is not installed");
        return catalog;
    }

    private static final class MutablePool {
        private final String factionId;
        private final Map<String, MutableOffer> offers = new LinkedHashMap<>();
        private String noPlayerEquipmentReason;

        private MutablePool(String factionId) {
            this.factionId = factionId;
        }

        private FactionEquipmentPool snapshot() {
            List<FactionEquipmentOffer> snapshot = new ArrayList<>();
            for (MutableOffer offer : offers.values()) snapshot.add(offer.snapshot());
            return new FactionEquipmentPool(factionId, snapshot, noPlayerEquipmentReason);
        }
    }

    private static final class MutableOffer {
        private final EquipmentTemplateCard template;
        private final EnumMap<FactionEquipmentSource, Integer> sourceWeights =
                new EnumMap<>(FactionEquipmentSource.class);

        private MutableOffer(EquipmentTemplateCard template) {
            this.template = template;
        }

        private FactionEquipmentOffer snapshot() {
            return new FactionEquipmentOffer(template, sourceWeights);
        }
    }

    private record Claim(String factionId, String templateId, FactionEquipmentSource source) {}
}
