package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.infantry.MarineWeapon;

/** Explicit bridge from combat loadout identity to a modular weapon sprite. */
public enum LayeredWeaponFamily {
    RIFLE,
    LASER_GUN,
    SMG,
    DMR;

    /** Null is the baked-stat militia/legacy rifle rather than an unknown weapon. */
    public static LayeredWeaponFamily fromPrimary(MarineWeapon weapon) {
        if (weapon == null) return RIFLE;
        return switch (weapon) {
            case PULSE_RIFLE, DRONE_PULSE -> LASER_GUN;
            case SMG -> SMG;
            case DMR -> DMR;
        };
    }
}
