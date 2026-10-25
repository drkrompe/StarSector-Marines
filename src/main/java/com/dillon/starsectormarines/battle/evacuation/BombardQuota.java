package com.dillon.starsectormarines.battle.evacuation;

/**
 * How many Bombards the swarm keeps on the field, and where they grow ({@code bombard.md}, Count): at most
 * {@link #CAP} at once across the battle, each drawn only by one of the {@link #CAP} mature bores best placed to reach
 * the line, a bore holding one at a time. One that dies frees its place, so the long arm regrows. Without the battle
 * cap every maw kept one for the whole battle, out of reach of anything but the air wing.
 */
public final class BombardQuota {

    /** Living Bombards a battle holds at most, and the bores ranked nearest the line that may draw one. UNTUNED. */
    public static final int CAP = 3;

    private BombardQuota() {}

    /**
     * Whether a bore may draw a Bombard in its next surge.
     *
     * @param holdsOne    the bore's own Bombard lives, or is already planned in this surge
     * @param alive       Bombards living or planned across the battle
     * @param closerBores bores that could draw one standing nearer the line than this one
     */
    public static boolean allowed(boolean holdsOne, int alive, int closerBores) {
        return !holdsOne && alive < CAP && closerBores < CAP;
    }
}
