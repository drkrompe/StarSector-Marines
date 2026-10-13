package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import java.util.Locale;

/** Protection chassis, independent of infantry role and equipment quality. */
public enum ArmorClass {
    STANDARD("standard", "Regular infantry"),
    POWER_SUIT("power-suit", "Power suit");
    public final String key;
    private final String label;
    ArmorClass(String key, String label) { this.key = key; this.label = label; }
    public String displayName() { return label; }
    public static ArmorClass parse(String key, String armorId) throws JSONException {
        if (key != null) {
            String normalized = key.trim().toLowerCase(Locale.ROOT);
            for (ArmorClass value : values()) if (value.key.equals(normalized)) return value;
        }
        throw new JSONException("Armor '" + armorId + "' has unknown armorClass '" + key
                + "'; expected standard or power-suit");
    }
}
