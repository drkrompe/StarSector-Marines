package com.dillon.starsectormarines.campaign;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Lazy loader for data-authored patron-linked Chronicle callbacks. */
public final class PatronChronicleVoice {

    private static final Logger LOG = Global.getLogger(
            PatronChronicleVoice.class);

    static final String CONTENT_KEY = "chronicleReference";

    private static volatile Map<PatronChronicleReferenceType, String[]> cache;

    private PatronChronicleVoice() {}

    public static String[] forType(PatronChronicleReferenceType type) {
        if (cache == null) {
            synchronized (PatronChronicleVoice.class) {
                if (cache == null) cache = loadOrEmpty();
            }
        }
        String[] pool = cache.get(type);
        return pool != null ? pool : new String[0];
    }

    public static Map<PatronChronicleReferenceType, String[]> parse(
            JSONObject root) {
        if (root == null) {
            throw new IllegalStateException("patron Chronicle voice: null root");
        }
        JSONObject chronicle = root.optJSONObject(CONTENT_KEY);
        if (chronicle == null) {
            throw new IllegalStateException("patron Chronicle voice: missing '"
                    + CONTENT_KEY + "' object");
        }
        Map<PatronChronicleReferenceType, String[]> result =
                new EnumMap<>(PatronChronicleReferenceType.class);
        for (PatronChronicleReferenceType type
                : PatronChronicleReferenceType.values()) {
            JSONArray array = chronicle.optJSONArray(type.name());
            if (array == null || array.length() == 0) {
                throw new IllegalStateException("patron Chronicle voice: '"
                        + type.name() + "' is missing or empty");
            }
            String[] pool = new String[array.length()];
            for (int i = 0; i < array.length(); i++) {
                Object value = array.opt(i);
                if (!(value instanceof String)
                        || ((String) value).trim().isEmpty()) {
                    throw new IllegalStateException(
                            "patron Chronicle voice: '" + type.name()
                                    + "' variant " + i
                                    + " is empty or not a string");
                }
                pool[i] = (String) value;
            }
            result.put(type, pool);
        }
        return result;
    }

    static void loadForTest(
            Map<PatronChronicleReferenceType, String[]> injected) {
        synchronized (PatronChronicleVoice.class) {
            cache = injected != null ? new HashMap<>(injected) : null;
        }
    }

    private static Map<PatronChronicleReferenceType, String[]> loadOrEmpty() {
        try {
            JSONObject root = Global.getSettings().loadJSON(
                    CommsOfficerVoice.CONTENT_PATH, true);
            Map<PatronChronicleReferenceType, String[]> parsed = parse(root);
            LOG.info("PatronChronicleVoice: loaded " + parsed.size()
                    + " reference pools from "
                    + CommsOfficerVoice.CONTENT_PATH);
            return parsed;
        } catch (Throwable failure) {
            LOG.warn("PatronChronicleVoice: load failed — skipping Chronicle "
                    + "callback: " + failure.getMessage());
            return new EnumMap<>(PatronChronicleReferenceType.class);
        }
    }
}
