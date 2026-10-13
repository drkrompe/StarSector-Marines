package com.dillon.starsectormarines.battle.fort;

/**
 * The kinds of fort a mission can declare. A fort is a walled compound built around one defensive purpose; see
 * {@code forts.md}. The battery fort and the shield fort are built.
 */
public enum FortKind {
    /** The battery platform with its fire-control bunker, magazine and power building inside one wall. */
    BATTERY_FORT,
    /** The shield generator inside an inner keep, with a power plant and a control room, inside one wall. */
    SHIELD_FORT
}
