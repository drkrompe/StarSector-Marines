package com.dillon.starsectormarines.marine;

/**
 * A place on a ship's boat where one fitting is installed.
 *
 * <p>Two of them, and they divide the boat the way a yard's work order does:
 * what is between the crew and the ground fire, and what gets them off the
 * deck. A hardpoint slot would be a third, and is deliberately absent — a
 * boat's door gun is decided by what the sortie is for
 * ({@code AirArmament.kitFor}), not by what the company bolted on last month.
 */
public enum BoatFittingSlot {

    /** Hull. What an anti-air gun has to get through on the run in. */
    PLATING,

    /** Engines. How fast the boat is through that fire and away again. */
    DRIVE
}
