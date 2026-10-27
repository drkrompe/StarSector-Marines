package com.dillon.starsectormarines.marine;

import org.json.JSONException;

/** How a special-equipment item gates repeated battle use. */
public enum SpecialResourceMode {
    AMMUNITION("ammunition"),
    COOLDOWN("cooldown"),
    /**
     * A regenerating pool spent per point of effect, the squad medic's
     * ({@code squad-medic.md}). Only a field-aid integral system declares it; its
     * numbers live in {@code squad-medicine.json}, not on the item.
     */
    ENERGY("energy");

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
