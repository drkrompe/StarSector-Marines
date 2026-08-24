package com.dillon.starsectormarines.marine;

import org.json.JSONException;

/** Typed battle channel used by one item in a marine's special-equipment slot. */
public enum SpecialActivation {
    DIRECT_EXPLOSIVE("direct-explosive"),
    DIRECT_PRECISION("direct-precision"),
    UTILITY_SMOKE("utility-smoke"),
    UTILITY_SATCHEL("utility-satchel");

    public final String key;

    SpecialActivation(String key) {
        this.key = key;
    }

    public static SpecialActivation fromKey(String key, String equipmentId)
            throws JSONException {
        for (SpecialActivation activation : values()) {
            if (activation.key.equalsIgnoreCase(key)) return activation;
        }
        throw new JSONException("Special equipment '" + equipmentId
                + "' has unknown activation type '" + key + "'");
    }
}
