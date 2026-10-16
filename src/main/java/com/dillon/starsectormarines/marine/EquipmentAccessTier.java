package com.dillon.starsectormarines.marine;

import org.json.JSONException;

import java.util.Locale;

/** Authored campaign-access band for one collectible equipment template. */
public enum EquipmentAccessTier {
    COMMON,
    ADVANCED,
    PRESTIGE,
    /**
     * Approved military issue. No market, licence, patron roll or recovery roll offers it on
     * its own authority: it becomes acquirable only once the company holds a military
     * warrant from the tradition that builds it ({@link MilitaryWarrants}). It ranks above
     * {@link #PRESTIGE} so that every ordinal ceiling refuses it, but it is not more Prestige:
     * no reputation or victory count ever reaches it.
     */
    RESTRICTED;

    static EquipmentAccessTier parse(String value) throws JSONException {
        if (value == null || value.isBlank()) {
            throw new JSONException("Equipment template entry is missing 'accessTier'");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new JSONException("Unknown equipment access tier '" + value
                    + "'; expected common, advanced, prestige, or restricted");
        }
    }
}
