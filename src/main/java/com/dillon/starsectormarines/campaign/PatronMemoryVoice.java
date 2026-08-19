package com.dillon.starsectormarines.campaign;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Lazy loader for data-authored comms-officer patron-memory lines. */
public final class PatronMemoryVoice {

    private static final Logger LOG = Global.getLogger(PatronMemoryVoice.class);

    static final String MEMORY_KEY = "memory";

    private static volatile Map<PatronEngagementOutcome, String[]> cache;

    private PatronMemoryVoice() {}

    public static String[] forOutcome(PatronEngagementOutcome outcome) {
        if (cache == null) {
            synchronized (PatronMemoryVoice.class) {
                if (cache == null) cache = loadOrFallback();
            }
        }
        String[] pool = cache.get(outcome);
        return pool != null ? pool : placeholderFor(outcome);
    }

    public static Map<PatronEngagementOutcome, String[]> parse(
            JSONObject root) {
        if (root == null) {
            throw new IllegalStateException("patron memory voice: null root");
        }
        JSONObject memory = root.optJSONObject(MEMORY_KEY);
        if (memory == null) {
            throw new IllegalStateException(
                    "patron memory voice: missing '" + MEMORY_KEY + "' object");
        }
        Map<PatronEngagementOutcome, String[]> result =
                new EnumMap<>(PatronEngagementOutcome.class);
        for (PatronEngagementOutcome outcome
                : PatronEngagementOutcome.values()) {
            JSONArray array = memory.optJSONArray(outcome.name());
            if (array == null || array.length() == 0) {
                throw new IllegalStateException("patron memory voice: '"
                        + outcome.name() + "' is missing or empty");
            }
            String[] pool = new String[array.length()];
            for (int i = 0; i < array.length(); i++) {
                Object value = array.opt(i);
                if (!(value instanceof String)
                        || ((String) value).trim().isEmpty()) {
                    throw new IllegalStateException("patron memory voice: '"
                            + outcome.name() + "' variant " + i
                            + " is empty or not a string");
                }
                pool[i] = (String) value;
            }
            result.put(outcome, pool);
        }
        return result;
    }

    static void loadForTest(
            Map<PatronEngagementOutcome, String[]> injected) {
        synchronized (PatronMemoryVoice.class) {
            cache = injected != null ? new HashMap<>(injected) : null;
        }
    }

    private static Map<PatronEngagementOutcome, String[]> loadOrFallback() {
        try {
            JSONObject root = Global.getSettings().loadJSON(
                    CommsOfficerVoice.CONTENT_PATH, true);
            Map<PatronEngagementOutcome, String[]> parsed = parse(root);
            LOG.info("PatronMemoryVoice: loaded " + parsed.size()
                    + " outcome pools from "
                    + CommsOfficerVoice.CONTENT_PATH);
            return parsed;
        } catch (Throwable failure) {
            LOG.warn("PatronMemoryVoice: load failed — using placeholders: "
                    + failure.getMessage());
            Map<PatronEngagementOutcome, String[]> fallback =
                    new EnumMap<>(PatronEngagementOutcome.class);
            for (PatronEngagementOutcome outcome
                    : PatronEngagementOutcome.values()) {
                fallback.put(outcome, placeholderFor(outcome));
            }
            return fallback;
        }
    }

    private static String[] placeholderFor(PatronEngagementOutcome outcome) {
        return new String[] { "[" + outcome.name()
                + " patron memory missing]" };
    }
}
