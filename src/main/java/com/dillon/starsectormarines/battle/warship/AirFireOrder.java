package com.dillon.starsectormarines.battle.warship;

/**
 * Whether, and at what, a warship's weapons may fire at hostile aircraft: the
 * mission's voice about ship-versus-air, beside the area orders that are its
 * voice about ground. Which weapon may engage which aircraft is
 * {@link WarshipAirRules}'s; this only says what the ship is allowed to look at.
 */
public enum AirFireOrder {
    /**
     * The ship's posture decides (the default). Air superiority engages the armed craft it counts and any
     * other hostile aircraft in reach; intercept engages the loaded boats it is trying to deny; every other
     * posture, and a ship with no doctrine, fires at ground only, exactly as a ship always did.
     */
    AUTO,
    /** Never fire at aircraft, whatever the posture. */
    OFF,
    /** Fire at any hostile aircraft in the air, under any posture or none: an explicit order. */
    ALL
}
