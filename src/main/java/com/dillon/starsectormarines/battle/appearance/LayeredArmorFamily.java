package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.unit.UnitType;

/** Stable tier-neutral ids for independently swappable modular body/head art. */
public enum LayeredArmorFamily {
    CHARCOAL,
    BLUE_SCOUT,
    RED_ELITE,
    OUTLAW,
    ARMY_GREEN,
    MILITIA,
    /** Cloth fatigues and a bare head; legacy ordinal 6 must remain stable. */
    ARMORLESS,
    /** Grimy frontier workwear and an uncovered colonist head. */
    CIVILIAN_COLONIST,
    /** Reinforced industrial workwear and a sealed hazard helmet. */
    ENGINEER,
    /** Worn field-lab clothing and a compact research headset. */
    SCIENTIST;

    public static LayeredArmorFamily spawnDefault(UnitType type) {
        switch (type) {
            case MARINE_BLUE: return BLUE_SCOUT;
            case MARINE_RED: return OUTLAW;
            case MILITIA: return MILITIA;
            case CIVILIAN: return CIVILIAN_COLONIST;
            case ENGINEER: return ENGINEER;
            case SCIENTIST: return SCIENTIST;
            case MARINE:
            default: return CHARCOAL;
        }
    }

    public static LayeredArmorFamily fromOrdinal(int ordinal) {
        LayeredArmorFamily[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : CHARCOAL;
    }
}
