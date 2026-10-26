package com.dillon.starsectormarines.marine;

/**
 * A place on a ship's boat where one fitting is installed.
 *
 * <p>Three of them, and they divide the boat the way a yard's work order does:
 * what is between the crew and the ground fire, what gets them off the deck,
 * and whether the hold takes people or a machine. A boat's door guns are not a
 * slot here: a boat has as many hardpoints as its pattern, each takes one
 * {@link BoatGun}, and they are fitted by hardpoint index.
 */
public enum BoatFittingSlot {

    /** Hull. What an anti-air gun has to get through on the run in. */
    PLATING,

    /** Engines. How fast the boat is through that fire and away again. */
    DRIVE,

    /**
     * The hold. Standard seats people; a cradle keeps the seats and adds a
     * lift rating, so a cradled boat flies squad loads and then one machine a
     * sortie. Bus-class hulls only.
     */
    LIFT
}
