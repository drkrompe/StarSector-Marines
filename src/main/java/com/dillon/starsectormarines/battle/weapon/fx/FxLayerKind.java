package com.dillon.starsectormarines.battle.weapon.fx;

import org.json.JSONException;

/** Existing ground-combat particle primitives available to authored effects. */
public enum FxLayerKind {
    GLOW("glow"),
    DUST("dust"),
    SMOKE("smoke"),
    FIRE("fire"),
    EXPLOSION("explosion"),
    RING("ring");

    public final String key;

    FxLayerKind(String key) {
        this.key = key;
    }

    public static FxLayerKind fromKey(String key, String definitionId) throws JSONException {
        for (FxLayerKind kind : values()) {
            if (kind.key.equalsIgnoreCase(key)) return kind;
        }
        throw new JSONException("FX definition '" + definitionId + "' has unknown layer kind '" + key + "'");
    }
}
