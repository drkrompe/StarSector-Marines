package com.dillon.starsectormarines.campaign;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Lazy loader + validator for the patron-archetype briefing template bank.
 * Content lives in {@code mod/data/marines/patron_briefings.json}; this
 * class parses, validates, and caches it on first access.
 *
 * <p>Loaded with {@code withMods=true} so translation mods that ship a
 * {@code patron_briefings.json} of their own merge for free without code
 * changes — same pattern as vanilla strings.json.
 *
 * <h2>Failure modes</h2>
 * If the file is missing, malformed, or any archetype is unmapped /
 * empty, the loader logs at WARN and returns a one-element fallback
 * pool containing a placeholder string with the archetype name in it.
 * The placeholder is intentionally visible to the player — better a
 * "[FALLEN_NOBLE template missing]" briefing than a silent stub or
 * a crash.
 *
 * <p>The parse step is exposed as {@link #parse(JSONObject)} so tests
 * can validate the schema check without booting SettingsAPI.
 */
public final class PatronBriefingTemplates {

    private static final Logger LOG = Global.getLogger(PatronBriefingTemplates.class);

    /** Mod-relative path. Translators ship the same path in their own mod. */
    public static final String CONTENT_PATH = "data/marines/patron_briefings.json";

    /** Top-level JSON key holding the archetype → variants map. */
    static final String TEMPLATES_KEY = "templates";

    /**
     * Cached parse result. {@code null} until first {@link #forArchetype}
     * call (or test injection via {@link #loadForTest}). Field is volatile
     * so the lazy init publishes safely without locking.
     */
    private static volatile Map<PatronArchetype, String[]> cache;

    private PatronBriefingTemplates() {}

    /**
     * Returns the variant pool for an archetype. Triggers a one-time load
     * + validate of the content file on first call. Subsequent calls hit
     * the in-memory cache.
     *
     * <p>Guaranteed non-null and non-empty: a missing or malformed file
     * still yields a one-element placeholder pool per the loader's
     * fail-loud contract.
     */
    public static String[] forArchetype(PatronArchetype archetype) {
        if (cache == null) {
            synchronized (PatronBriefingTemplates.class) {
                if (cache == null) cache = loadOrFallback();
            }
        }
        String[] variants = cache.get(archetype);
        return variants != null ? variants : placeholderFor(archetype);
    }

    /**
     * Pure parse + validate over an in-memory JSONObject — exposed for
     * tests so the schema check can be exercised without SettingsAPI.
     * Throws {@link IllegalStateException} on any validation failure
     * (missing key, missing archetype, empty pool, non-string entry).
     */
    public static Map<PatronArchetype, String[]> parse(JSONObject root) {
        if (root == null) throw new IllegalStateException("patron briefings: null root");
        JSONObject templates = root.optJSONObject(TEMPLATES_KEY);
        if (templates == null) {
            throw new IllegalStateException("patron briefings: missing required '" + TEMPLATES_KEY + "' object");
        }

        Map<PatronArchetype, String[]> out = new EnumMap<>(PatronArchetype.class);
        for (PatronArchetype a : PatronArchetype.values()) {
            JSONArray arr = templates.optJSONArray(a.name());
            if (arr == null) {
                throw new IllegalStateException(
                        "patron briefings: archetype '" + a.name() + "' is missing or not an array");
            }
            if (arr.length() == 0) {
                throw new IllegalStateException(
                        "patron briefings: archetype '" + a.name() + "' has zero variants");
            }
            String[] variants = new String[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                Object v = arr.opt(i);
                if (!(v instanceof String) || ((String) v).isEmpty()) {
                    throw new IllegalStateException(
                            "patron briefings: archetype '" + a.name() + "' variant " + i
                                    + " is empty or not a string");
                }
                variants[i] = (String) v;
            }
            out.put(a, variants);
        }
        return out;
    }

    /**
     * Test seam — injects a parsed template map so tests can exercise
     * downstream code (PatronBriefingFlavor.render) without loading a
     * JSON file. Pass {@code null} to clear the cache and force the
     * real loader to run on next access.
     */
    static void loadForTest(Map<PatronArchetype, String[]> injected) {
        synchronized (PatronBriefingTemplates.class) {
            cache = injected != null ? new HashMap<>(injected) : null;
        }
    }

    /**
     * Real file load. Failures (no SettingsAPI in test, missing file,
     * malformed JSON, schema violation) all degrade to a placeholder
     * map covering every archetype so callers never NPE.
     */
    private static Map<PatronArchetype, String[]> loadOrFallback() {
        try {
            JSONObject root = Global.getSettings().loadJSON(CONTENT_PATH, true);
            Map<PatronArchetype, String[]> parsed = parse(root);
            LOG.info("PatronBriefingTemplates: loaded " + parsed.size()
                    + " archetypes from " + CONTENT_PATH);
            return parsed;
        } catch (Throwable t) {
            LOG.warn("PatronBriefingTemplates: load failed for " + CONTENT_PATH
                    + " — falling back to placeholder templates: " + t.getMessage());
            return placeholderMap();
        }
    }

    private static Map<PatronArchetype, String[]> placeholderMap() {
        Map<PatronArchetype, String[]> out = new EnumMap<>(PatronArchetype.class);
        for (PatronArchetype a : PatronArchetype.values()) {
            out.put(a, placeholderFor(a));
        }
        return out;
    }

    private static String[] placeholderFor(PatronArchetype a) {
        return new String[] { "[" + a.name() + " briefing template missing]" };
    }

    /**
     * Test predicate — true if a returned pool is the loud-fallback
     * placeholder. Lets tests distinguish "loader ran and got real
     * templates" from "loader degraded and emitted the warning string."
     */
    static boolean isPlaceholder(String[] pool) {
        return pool != null && pool.length == 1
                && pool[0] != null && pool[0].startsWith("[") && pool[0].endsWith("template missing]");
    }
}
