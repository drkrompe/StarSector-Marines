package com.dillon.starsectormarines.battle.weapon;

import org.json.JSONException;

/**
 * What carries a weapon. Replaces "which Java enum is it in" as the thing
 * that distinguishes a marine's rifle from a turret's autocannon, so all
 * four catalogs can share one {@link WeaponDef} schema.
 *
 * <p>Handheld primary, weapon-like secondary and turret-mount definitions are
 * populated today. Mech mounts retain their compatibility catalog until W3.
 */
public enum MountClass {

    /** Handheld primary carried by a marine, militia, or drone. */
    MARINE_PRIMARY("marine-primary"),
    /** Handheld secondary — the rocket launcher family. */
    MARINE_SECONDARY("marine-secondary"),
    /** Arm or shoulder mount on a mech chassis. */
    MECH_MOUNT("mech-mount"),
    /** Static emplacement or shuttle-mounted turret. */
    TURRET_MOUNT("turret-mount");

    /** The value authored in JSON. Kebab-case, matching the tile and doodad id conventions. */
    public final String key;

    MountClass(String key) {
        this.key = key;
    }

    static MountClass fromKey(String key, String weaponId) throws JSONException {
        for (MountClass mount : values()) {
            if (mount.key.equalsIgnoreCase(key)) return mount;
        }
        throw new JSONException("Weapon '" + weaponId + "' has unknown mount class '" + key + "'");
    }
}
