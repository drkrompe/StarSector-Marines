package com.dillon.starsectormarines.battle.squad;

/**
 * One squad's memory of whether it is being held back on its way to a room, so
 * the decision is made every few seconds rather than every tick.
 *
 * <p>The hold is a courtesy to the picture, not a rule of the fight: it never
 * outlasts {@code maxHold}, and a squad it has released stays released for
 * that hop.
 */
public final class EntryGate {

    private int zoneId = -1;
    private int checkedAt = Integer.MIN_VALUE / 2;
    private boolean holding;
    private int heldSince = -1;
    private boolean released;

    /**
     * Whether the last answer for this room is still fresh enough to reuse.
     * A different room always needs a fresh answer.
     */
    public boolean isFresh(int zone, int tick, int checkEvery) {
        if (zone != zoneId) return false;
        return tick - checkedAt < checkEvery;
    }

    /** The last answer, for a squad that is not due a fresh one. */
    public boolean holding() {
        return holding;
    }

    /**
     * Records a fresh decision.
     *
     * @param near      whether the squad is close enough to the cause of the
     *                  hold for it to apply
     * @param occupants squads causing a hold
     * @param slots     how many may be present before the squad waits
     * @param maxHold   ticks a squad waits before it goes in regardless
     */
    public boolean decide(int zone, int tick, boolean near, int occupants,
                          int slots, int maxHold) {
        if (zone != zoneId) {
            zoneId = zone;
            holding = false;
            heldSince = -1;
            released = false;
        }
        checkedAt = tick;
        if (released) {
            holding = false;
            return false;
        }
        if (!near || occupants < slots) {
            holding = false;
            heldSince = -1;
            return false;
        }
        if (heldSince < 0) heldSince = tick;
        if (tick - heldSince >= maxHold) {
            released = true;
            holding = false;
            return false;
        }
        holding = true;
        return true;
    }
}
