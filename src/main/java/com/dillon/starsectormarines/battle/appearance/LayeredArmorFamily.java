package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.unit.UnitType;

/** Stable tier-neutral ids for independently swappable modular body/head art. */
public enum LayeredArmorFamily {
    CHARCOAL,
    BLUE_SCOUT,
    RED_ELITE,
    OUTLAW,
    ARMY_GREEN,
    MILITIA;

    public static LayeredArmorFamily spawnDefault(UnitType type) {
        switch (type) {
            case MARINE_BLUE: return BLUE_SCOUT;
            case MARINE_RED: return OUTLAW;
            case MILITIA: return MILITIA;
            case MARINE:
            default: return CHARCOAL;
        }
    }

    public static LayeredArmorFamily fromOrdinal(int ordinal) {
        LayeredArmorFamily[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : CHARCOAL;
    }
}
