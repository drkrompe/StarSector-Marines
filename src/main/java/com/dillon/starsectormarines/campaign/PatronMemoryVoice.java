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
    static final String CONTINUITY_KEY = "continuity";
    static final String LOCAL_ECHO_KEY = "localEcho";

    private static volatile Map<PatronEngagementOutcome, String[]> cache;
    private static volatile Map<PatronRelationshipPattern, String[]>
            continuityCache;
    private static volatile Map<PatronEngagementOutcome, String[]>
            localEchoCache;

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

    public static String[] forPattern(PatronRelationshipPattern pattern) {
        if (continuityCache == null) {
            synchronized (PatronMemoryVoice.class) {
                if (continuityCache == null) {
                    continuityCache = loadContinuityOrEmpty();
                }
            }
        }
        String[] pool = continuityCache.get(pattern);
        return pool != null ? pool : new String[0];
    }

    public static String[] forLocalEcho(PatronEngagementOutcome outcome) {
        if (localEchoCache == null) {
            synchronized (PatronMemoryVoice.class) {
                if (localEchoCache == null) {
                    localEchoCache = loadLocalEchoOrEmpty();
                }
            }
        }
        String[] pool = localEchoCache.get(outcome);
        return pool != null ? pool : new String[0];
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

    public static Map<PatronRelationshipPattern, String[]> parseContinuity(
            JSONObject root) {
        if (root == null) {
            throw new IllegalStateException(
                    "patron continuity voice: null root");
        }
        JSONObject continuity = root.optJSONObject(CONTINUITY_KEY);
        if (continuity == null) {
            throw new IllegalStateException("patron continuity voice: missing '"
                    + CONTINUITY_KEY + "' object");
        }
        Map<PatronRelationshipPattern, String[]> result =
                new EnumMap<>(PatronRelationshipPattern.class);
        for (PatronRelationshipPattern pattern
                : PatronRelationshipPattern.values()) {
            JSONArray array = continuity.optJSONArray(pattern.name());
            if (array == null || array.length() == 0) {
                throw new IllegalStateException("patron continuity voice: '"
                        + pattern.name() + "' is missing or empty");
            }
            String[] pool = new String[array.length()];
            for (int i = 0; i < array.length(); i++) {
                Object value = array.opt(i);
                if (!(value instanceof String)
                        || ((String) value).trim().isEmpty()) {
                    throw new IllegalStateException(
                            "patron continuity voice: '" + pattern.name()
                                    + "' variant " + i
                                    + " is empty or not a string");
                }
                pool[i] = (String) value;
            }
            result.put(pattern, pool);
        }
        return result;
    }

    public static Map<PatronEngagementOutcome, String[]> parseLocalEcho(
            JSONObject root) {
        if (root == null) {
            throw new IllegalStateException("patron local echo: null root");
        }
        JSONObject localEcho = root.optJSONObject(LOCAL_ECHO_KEY);
        if (localEcho == null) {
            throw new IllegalStateException("patron local echo: missing '"
                    + LOCAL_ECHO_KEY + "' object");
        }
        Map<PatronEngagementOutcome, String[]> result =
                new EnumMap<>(PatronEngagementOutcome.class);
        for (PatronEngagementOutcome outcome
                : PatronEngagementOutcome.values()) {
            JSONArray array = localEcho.optJSONArray(outcome.name());
            if (array == null || array.length() == 0) {
                throw new IllegalStateException("patron local echo: '"
                        + outcome.name() + "' is missing or empty");
            }
            String[] pool = new String[array.length()];
            for (int i = 0; i < array.length(); i++) {
                Object value = array.opt(i);
                if (!(value instanceof String)
                        || ((String) value).trim().isEmpty()) {
                    throw new IllegalStateException("patron local echo: '"
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

    static void loadContinuityForTest(
            Map<PatronRelationshipPattern, String[]> injected) {
        synchronized (PatronMemoryVoice.class) {
            continuityCache = injected != null
                    ? new HashMap<>(injected) : null;
        }
    }

    static void loadLocalEchoForTest(
            Map<PatronEngagementOutcome, String[]> injected) {
        synchronized (PatronMemoryVoice.class) {
            localEchoCache = injected != null
                    ? new HashMap<>(injected) : null;
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

    private static Map<PatronRelationshipPattern, String[]>
            loadContinuityOrEmpty() {
        try {
            JSONObject root = Global.getSettings().loadJSON(
                    CommsOfficerVoice.CONTENT_PATH, true);
            Map<PatronRelationshipPattern, String[]> parsed =
                    parseContinuity(root);
            LOG.info("PatronMemoryVoice: loaded " + parsed.size()
                    + " continuity pools from "
                    + CommsOfficerVoice.CONTENT_PATH);
            return parsed;
        } catch (Throwable failure) {
            LOG.warn("PatronMemoryVoice: continuity load failed — using S1 "
                    + "callbacks: " + failure.getMessage());
            return new EnumMap<>(PatronRelationshipPattern.class);
        }
    }

    private static Map<PatronEngagementOutcome, String[]>
            loadLocalEchoOrEmpty() {
        try {
            JSONObject root = Global.getSettings().loadJSON(
                    CommsOfficerVoice.CONTENT_PATH, true);
            Map<PatronEngagementOutcome, String[]> parsed =
                    parseLocalEcho(root);
            LOG.info("PatronMemoryVoice: loaded " + parsed.size()
                    + " local-echo pools from "
                    + CommsOfficerVoice.CONTENT_PATH);
            return parsed;
        } catch (Throwable failure) {
            LOG.warn("PatronMemoryVoice: local-echo load failed — keeping "
                    + "first-time briefing unchanged: "
                    + failure.getMessage());
            return new EnumMap<>(PatronEngagementOutcome.class);
        }
    }

    private static String[] placeholderFor(PatronEngagementOutcome outcome) {
        return new String[] { "[" + outcome.name()
                + " patron memory missing]" };
    }
}
