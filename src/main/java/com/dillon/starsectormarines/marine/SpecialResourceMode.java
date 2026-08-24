package com.dillon.starsectormarines.marine;

import org.json.JSONException;

/** How a special-equipment item gates repeated battle use. */
public enum SpecialResourceMode {
    AMMUNITION("ammunition"),
    COOLDOWN("cooldown");

    public final String key;

    SpecialResourceMode(String key) {
        this.key = key;
    }

    public static SpecialResourceMode fromKey(String key, String equipmentId)
            throws JSONException {
        for (SpecialResourceMode mode : values()) {
            if (mode.key.equalsIgnoreCase(key)) return mode;
        }
        throw new JSONException("Special equipment '" + equipmentId
                + "' has unknown resource mode '" + key + "'");
    }
}
