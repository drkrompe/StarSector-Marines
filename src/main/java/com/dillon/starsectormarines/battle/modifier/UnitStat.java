package com.dillon.starsectormarines.battle.modifier;

/**
 * A unit stat that a {@link UnitModifier} may change while a condition holds. The base value is
 * what the unit was issued (weapon, grade, armour, chassis) and is stored on the unit; every read
 * of the stat goes through {@link UnitModifiers#resolve}, so a condition that ends leaves nothing
 * behind to undo.
 */
public enum UnitStat {
    /** Weapon reach in cells: what targeting, the firing gate and range falloff measure against. */
    ATTACK_RANGE,
    /** Accuracy of the unit's own primary fire, before range falloff and stance. */
    ACCURACY,
    /** Movement speed in cells per second, before terrain. */
    MOVE_SPEED,
    /** Multiplier on the chance of rounds aimed at the unit to hit it. */
    INCOMING_ACCURACY,
    /** Multiplier on the morale a hit or near miss on the unit drains from its fire team. */
    MORALE_DRAIN;

    static final UnitStat[] VALUES = values();
}
