package com.dillon.starsectormarines.battle.drone;

/**
 * A cradle that launches its flock from a bay on a cadence of its own, in place of the slow one-drone rebuild
 * ({@link FlockCradle.Clock}): a guardian's housing (`ruins-guardians.md`). {@link FlockSystem} ticks it in the serial
 * part of the tick, puts up what it launches at the flock's home and sets the housing's plate to its rating.
 */
public interface FlockHatch {

    /**
     * Advances the hatch by {@code dt} seconds.
     *
     * @param fed        whether its branch feeds it
     * @param nodeStands whether its zone's control node stands
     * @param airborne   the flock's drones now up
     * @return the drones launched this tick
     */
    int tick(float dt, boolean fed, boolean nodeStands, int airborne);

    /** The plate rating its housing takes hits on now. */
    float armorRating();
}
