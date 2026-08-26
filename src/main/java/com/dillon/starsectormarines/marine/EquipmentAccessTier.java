package com.dillon.starsectormarines.marine;

import org.json.JSONException;

import java.util.Locale;

/** Authored campaign-access band for one collectible equipment template. */
public enum EquipmentAccessTier {
    COMMON,
    ADVANCED,
    PRESTIGE;

    static EquipmentAccessTier parse(String value) throws JSONException {
        if (value == null || value.isBlank()) {
            throw new JSONException("Equipment template entry is missing 'accessTier'");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new JSONException("Unknown equipment access tier '" + value
                    + "'; expected common, advanced, or prestige");
        }
    }
}
