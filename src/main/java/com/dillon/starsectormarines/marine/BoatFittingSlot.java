package com.dillon.starsectormarines.marine;

/**
 * A place on a ship's boat where one fitting is installed.
 *
 * <p>Six of them, one per refit axis, dividing the boat the way a yard's work
 * order does: what is between the crew and the ground fire, what gets them off
 * the deck, what the hold lifts, how many ride, what keeps the fire off, and
 * what powers the shield and the guns. A boat's door guns are not a slot here:
 * a boat has one gun socket per turret slot on her hull, each takes one
 * {@link BoatGun}, and they are fitted by hardpoint index.
 */
public enum BoatFittingSlot {

    /** Hull. What an anti-air gun has to get through on the run in. */
    PLATING,

    /** Engines. How fast the boat is through that fire, out of the ramp and away again. */
    DRIVE,

    /**
     * The hold's machine lift. The standard hold lifts nothing; each tier above
     * lifts one heavier class of machine, and never at the cost of a seat.
     */
    LIFT,

    /** The cabin. How many marines one sortie lands. */
    CABIN,

    /** The shield emitter. How wide the shield is and how much flux a blocked shot costs. */
    EMITTER,

    /** Capacitors and vents. The flux pool the shield and the guns share. */
    POWER
}
