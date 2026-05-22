package com.dillon.starsectormarines.campaign;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Lazy loader + validator for the comms-officer mood-frame voice bank.
 * Content lives in {@code mod/data/marines/comms_officer_voice.json};
 * this class parses, validates, and caches it on first access.
 *
 * <p>Each {@link OfficerMood} carries two pools: a {@code prefix} (the
 * officer's framing line that opens the briefing) and a {@code suffix}
 * (an optional closing aside). Pools are mood-pure — no archetype or
 * patron references — so prefix/suffix composes freely with any
 * archetype briefing variant.
 *
 * <p>Loaded with {@code withMods=true} so translation mods can ship
 * their own bank without code changes.
 *
 * <h2>Failure modes</h2>
 * Missing or malformed file → loud WARN + per-mood placeholder pools
 * (single {@code [MOOD prefix missing]} / {@code [MOOD suffix missing]}
 * entries). Same fail-loud philosophy as
 * {@link PatronBriefingTemplates}.
 */
public final class CommsOfficerVoice {

    private static final Logger LOG = Global.getLogger(CommsOfficerVoice.class);

    public static final String CONTENT_PATH = "data/marines/comms_officer_voice.json";

    static final String MOODS_KEY  = "moods";
    static final String PREFIX_KEY = "prefix";
    static final String SUFFIX_KEY = "suffix";

    /** A mood's two pools, both guaranteed non-empty. */
    public static final class Frame {
        public final String[] prefix;
        public final String[] suffix;
        public Frame(String[] prefix, String[] suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }
    }

    private static volatile Map<OfficerMood, Frame> cache;

    private CommsOfficerVoice() {}

    /**
     * Returns the prefix+suffix frame for a mood. Lazy-loads on first
     * call. Guaranteed non-null; missing-file fallback yields a frame
     * with one loud placeholder entry per pool.
     */
    public static Frame forMood(OfficerMood mood) {
        if (cache == null) {
            synchronized (CommsOfficerVoice.class) {
                if (cache == null) cache = loadOrFallback();
            }
        }
        Frame f = cache.get(mood);
        return f != null ? f : placeholderFor(mood);
    }

    /**
     * Pure parse + validate over an in-memory JSONObject. Exposed for
     * tests so the schema check runs without SettingsAPI. Throws
     * {@link IllegalStateException} on any validation failure.
     */
    public static Map<OfficerMood, Frame> parse(JSONObject root) {
        if (root == null) throw new IllegalStateException("comms officer voice: null root");
        JSONObject moods = root.optJSONObject(MOODS_KEY);
        if (moods == null) {
            throw new IllegalStateException(
                    "comms officer voice: missing required '" + MOODS_KEY + "' object");
        }
        Map<OfficerMood, Frame> out = new EnumMap<>(OfficerMood.class);
        for (OfficerMood m : OfficerMood.values()) {
            JSONObject entry = moods.optJSONObject(m.name());
            if (entry == null) {
                throw new IllegalStateException(
                        "comms officer voice: mood '" + m.name() + "' is missing or not an object");
            }
            out.put(m, new Frame(
                    parsePool(m, PREFIX_KEY, entry.optJSONArray(PREFIX_KEY)),
                    parsePool(m, SUFFIX_KEY, entry.optJSONArray(SUFFIX_KEY))));
        }
        return out;
    }

    private static String[] parsePool(OfficerMood mood, String key, JSONArray arr) {
        if (arr == null) {
            throw new IllegalStateException(
                    "comms officer voice: mood '" + mood.name() + "' is missing '" + key + "' array");
        }
        if (arr.length() == 0) {
            throw new IllegalStateException(
                    "comms officer voice: mood '" + mood.name() + "' '" + key + "' has zero variants");
        }
        String[] out = new String[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            Object v = arr.opt(i);
            if (!(v instanceof String) || ((String) v).isEmpty()) {
                throw new IllegalStateException(
                        "comms officer voice: mood '" + mood.name() + "' '" + key
                                + "' variant " + i + " is empty or not a string");
            }
            out[i] = (String) v;
        }
        return out;
    }

    /** Test seam — inject a parsed map; pass {@code null} to reset. */
    static void loadForTest(Map<OfficerMood, Frame> injected) {
        synchronized (CommsOfficerVoice.class) {
            cache = injected != null ? new HashMap<>(injected) : null;
        }
    }

    private static Map<OfficerMood, Frame> loadOrFallback() {
        try {
            JSONObject root = Global.getSettings().loadJSON(CONTENT_PATH, true);
            Map<OfficerMood, Frame> parsed = parse(root);
            LOG.info("CommsOfficerVoice: loaded " + parsed.size()
                    + " moods from " + CONTENT_PATH);
            return parsed;
        } catch (Throwable t) {
            LOG.warn("CommsOfficerVoice: load failed for " + CONTENT_PATH
                    + " — falling back to placeholders: " + t.getMessage());
            Map<OfficerMood, Frame> out = new EnumMap<>(OfficerMood.class);
            for (OfficerMood m : OfficerMood.values()) out.put(m, placeholderFor(m));
            return out;
        }
    }

    private static Frame placeholderFor(OfficerMood m) {
        return new Frame(
                new String[] { "[" + m.name() + " prefix missing]" },
                new String[] { "[" + m.name() + " suffix missing]" });
    }
}
