package com.dillon.starsectormarines.battle.weapon.fx;

import org.json.JSONException;

/** Authored presentation moments a weapon may compose independently. */
public enum FxSlot {
    LAUNCH("launch"),
    MUZZLE("muzzle"),
    TRACER("tracer"),
    TRAIL("trail"),
    IMPACT("impact"),
    AFTERMATH("aftermath");

    public final String key;

    FxSlot(String key) {
        this.key = key;
    }

    public static FxSlot fromKey(String key, String definitionId) throws JSONException {
        for (FxSlot slot : values()) {
            if (slot.key.equalsIgnoreCase(key)) return slot;
        }
        throw new JSONException("FX definition '" + definitionId + "' has unknown slot '" + key + "'");
    }
}
