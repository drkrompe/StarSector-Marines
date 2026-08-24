package com.dillon.starsectormarines.battle.weapon;

import org.json.JSONException;

/** Data-facing projectile-ribbon identity; render consumers resolve its geometry. */
public enum ContrailProfile {
    NONE("none"),
    MISSILE_SMOKE("missile-smoke");

    public final String key;

    ContrailProfile(String key) {
        this.key = key;
    }

    static ContrailProfile fromKey(String key, String weaponId) throws JSONException {
        if (key == null || key.isBlank()) return NONE;
        for (ContrailProfile profile : values()) {
            if (profile.key.equalsIgnoreCase(key.trim())) return profile;
        }
        throw new JSONException("Weapon '" + weaponId
                + "' declares unknown contrail profile '" + key + "'");
    }
}
