package com.dillon.starsectormarines.battle.command.allied;

/** The kinds of allied tool a mission can ask its producer for (`allied-tools.md`, The catalogue). */
public enum AlliedTool {
    ARMOUR_PAIR,
    CARRIER_LIFT,
    COLUMN,
    MECH_LANCE,
    AMMO_TRUCK,
    GUNSHIP,
    /** An LRM carrier and a mortar carrier: indirect fire for a lane, on its spotters' calls (`heart-allied-tools-next.md`). */
    FIRE_SECTION
}
