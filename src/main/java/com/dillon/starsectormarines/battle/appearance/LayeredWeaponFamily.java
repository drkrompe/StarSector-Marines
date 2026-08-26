package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import org.json.JSONException;

/** Explicit bridge from combat loadout identity to a modular weapon sprite. */
public enum LayeredWeaponFamily {
    RIFLE,
    LASER_GUN,
    SMG,
    DMR;

    /** Null is the baked-stat militia/legacy rifle rather than an unknown weapon. */
    public static LayeredWeaponFamily fromPrimary(WeaponDef weapon) {
        return weapon != null ? weapon.heldSpriteFamily : RIFLE;
    }

    public static LayeredWeaponFamily fromKey(String key, String weaponId)
            throws JSONException {
        if (key != null) {
            String normalized = key.trim().replace('-', '_');
            for (LayeredWeaponFamily family : values()) {
                if (family.name().equalsIgnoreCase(normalized)) return family;
            }
        }
        throw new JSONException("Marine primary '" + weaponId
                + "' has unknown heldSpriteFamily '" + key + "'");
    }
}
