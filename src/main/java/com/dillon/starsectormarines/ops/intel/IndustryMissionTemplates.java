package com.dillon.starsectormarines.ops.intel;

import com.dillon.starsectormarines.ops.MissionType;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Lazy loader + validator for the industry → mission-archetype bank.
 * Content lives in {@code mod/data/marines/industry_missions.json}; this
 * class parses, validates, and caches it on first access.
 *
 * <p>Loaded with {@code withMods=true} so translation mods (or content
 * expansions that ship a richer mission bank) merge for free.
 *
 * <h2>Failure modes</h2>
 * If the file is missing or malformed, the loader logs at WARN and
 * returns an empty map. That degrades to "no industry-driven missions"
 * in-game, which is visible enough during playtest to be noticed and
 * fixed — preferable to silently substituting placeholder prose, since
 * unlike patron briefings there's no finite enum of industries to
 * enumerate stubs for.
 *
 * <p>Unknown industry ids in the JSON are kept (they may belong to
 * other mods); industries the catalog doesn't mention simply return an
 * empty archetype list, the same as before this content split.
 */
public final class IndustryMissionTemplates {

    private static final Logger LOG = Global.getLogger(IndustryMissionTemplates.class);

    /** Mod-relative path. Translators and content mods ship the same path. */
    public static final String CONTENT_PATH = "data/marines/industry_missions.json";

    /** Top-level JSON key holding the industryId → archetype-list map. */
    static final String MISSIONS_KEY = "missions";

    static final String FIELD_TYPE   = "type";
    static final String FIELD_NAME   = "name";
    static final String FIELD_FLAVOR = "flavor";

    /**
     * Cached parse result. {@code null} until first {@link #forIndustry}
     * call (or test injection via {@link #loadForTest}). Volatile so the
     * lazy init publishes safely without locking on the read path.
     */
    private static volatile Map<String, List<MissionArchetype>> cache;

    private IndustryMissionTemplates() {}

    /**
     * Returns the archetype pool for an industry id. Triggers a one-time
     * load + validate on first call; subsequent calls hit the in-memory
     * cache. Unknown industries return {@link Collections#emptyList()}.
     */
    public static List<MissionArchetype> forIndustry(String industryId) {
        if (cache == null) {
            synchronized (IndustryMissionTemplates.class) {
                if (cache == null) cache = loadOrFallback();
            }
        }
        List<MissionArchetype> pool = cache.get(industryId);
        return pool != null ? pool : Collections.<MissionArchetype>emptyList();
    }

    /**
     * Pure parse + validate over an in-memory JSONObject — exposed for
     * tests so the schema check can run without SettingsAPI. Throws
     * {@link IllegalStateException} on any validation failure (missing
     * key, missing required field, unknown MissionType enum, empty
     * string).
     */
    public static Map<String, List<MissionArchetype>> parse(JSONObject root) {
        if (root == null) throw new IllegalStateException("industry missions: null root");
        JSONObject missions = root.optJSONObject(MISSIONS_KEY);
        if (missions == null) {
            throw new IllegalStateException(
                    "industry missions: missing required '" + MISSIONS_KEY + "' object");
        }

        Map<String, List<MissionArchetype>> out = new HashMap<>();
        Iterator<?> keys = missions.keys();
        while (keys.hasNext()) {
            String industryId = (String) keys.next();
            JSONArray arr = missions.optJSONArray(industryId);
            if (arr == null) {
                throw new IllegalStateException(
                        "industry missions: industry '" + industryId + "' is not an array");
            }
            if (arr.length() == 0) {
                throw new IllegalStateException(
                        "industry missions: industry '" + industryId + "' has zero archetypes");
            }
            List<MissionArchetype> archetypes = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject entry = arr.optJSONObject(i);
                if (entry == null) {
                    throw new IllegalStateException(
                            "industry missions: '" + industryId + "' entry " + i
                                    + " is not an object");
                }
                archetypes.add(parseEntry(industryId, i, entry));
            }
            out.put(industryId, archetypes);
        }
        return out;
    }

    private static MissionArchetype parseEntry(String industryId, int index, JSONObject entry) {
        String typeStr = entry.optString(FIELD_TYPE, null);
        String name    = entry.optString(FIELD_NAME, null);
        String flavor  = entry.optString(FIELD_FLAVOR, null);
        requireNonEmpty(industryId, index, FIELD_TYPE,   typeStr);
        requireNonEmpty(industryId, index, FIELD_NAME,   name);
        requireNonEmpty(industryId, index, FIELD_FLAVOR, flavor);
        MissionType type;
        try {
            type = MissionType.valueOf(typeStr);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "industry missions: '" + industryId + "' entry " + index
                            + " has unknown type '" + typeStr + "'");
        }
        return new MissionArchetype(type, name, flavor);
    }

    private static void requireNonEmpty(String industryId, int index, String field, String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(
                    "industry missions: '" + industryId + "' entry " + index
                            + " is missing required '" + field + "'");
        }
    }

    /**
     * Test seam — injects a parsed map so downstream tests can exercise
     * IndustryMissionCatalog without loading a JSON file. Pass
     * {@code null} to clear the cache and let the real loader run on
     * next access.
     */
    static void loadForTest(Map<String, List<MissionArchetype>> injected) {
        synchronized (IndustryMissionTemplates.class) {
            cache = injected != null ? new HashMap<>(injected) : null;
        }
    }

    /**
     * Real file load. Any failure (no SettingsAPI in test, missing file,
     * malformed JSON, schema violation) degrades to an empty map and a
     * loud WARN. Empty-everywhere is the loud failure mode here — no
     * industry-driven missions will appear in-game until it's fixed.
     */
    private static Map<String, List<MissionArchetype>> loadOrFallback() {
        try {
            JSONObject root = Global.getSettings().loadJSON(CONTENT_PATH, true);
            Map<String, List<MissionArchetype>> parsed = parse(root);
            int total = 0;
            for (List<MissionArchetype> v : parsed.values()) total += v.size();
            LOG.info("IndustryMissionTemplates: loaded " + parsed.size()
                    + " industries (" + total + " archetypes) from " + CONTENT_PATH);
            return parsed;
        } catch (Throwable t) {
            LOG.warn("IndustryMissionTemplates: load failed for " + CONTENT_PATH
                    + " — no industry-driven missions will be generated: " + t.getMessage());
            return Collections.emptyMap();
        }
    }
}
