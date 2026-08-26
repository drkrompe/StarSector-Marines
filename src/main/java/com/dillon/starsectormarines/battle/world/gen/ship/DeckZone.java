package com.dillon.starsectormarines.battle.world.gen.ship;

/**
 * A ship deck's longitudinal zone — the functional prior that decides where a
 * compartment purpose belongs without a coordinate table. Command and sensor
 * spaces belong {@link #FORE}, volume (hangar, cargo, habitation) belongs
 * {@link #MIDSHIPS}, and power, drive, and life support belong {@link #AFT}.
 *
 * <p>Zones are an authored generation fact published on the deck profile;
 * consumers ask the profile rather than re-deriving a zone from a cell's x
 * coordinate. See {@code ship-interiors-nouns.md}.
 */
public enum DeckZone {
    /** The bow third: command, sensors, and the narrowing hull that fronts them. */
    FORE,
    /** The broadest section: hangar, cargo, and habitation volume. */
    MIDSHIPS,
    /** The stern: power, drive, and life support. */
    AFT
}
