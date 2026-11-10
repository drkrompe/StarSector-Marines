package com.dillon.starsectormarines.battle.derelict;

/**
 * One derelict walker's re-arming (`derelict-watch.md`, Re-arming): it carries no loader, so its missile racks keep
 * what they launched with, and the zone's service bay is the only place they fill again. The rules are here; the
 * director walks the walker and loads its racks.
 *
 * <p>A walker whose racks are all spent, with nothing to close on or fire at, sets off for its bay while the bay
 * stands and its zone is online. Within {@link #REARM_REACH} of the bay it stands and takes a pack into each rack every
 * {@link #PACK_SECONDS} until every rack is full. A fight on the way, the bay lost, the zone gone dark or the
 * contingency breaks it off; it sets off again only once its racks are all spent again.
 */
public final class BayRearm {

    /** Seconds a pack takes to go into each rack at the bay. UNTUNED. */
    public static final float PACK_SECONDS = 3f;
    /** How near the bay (cells, either axis, centre to centre) a walker stands to re-arm: past its own flank. */
    public static final float REARM_REACH = 4f;

    public enum Step {
        /** Not re-arming: the walker does as its posture says. */
        NONE,
        /** Walking to the bay. */
        WALK,
        /** Standing at the bay, its racks filling. */
        LOAD
    }

    private boolean going;
    private float progress;

    /**
     * This pulse's step.
     *
     * @param racksSpent  whether the walker has racks and every one is empty
     * @param racksFull   whether every rack it has is full
     * @param engaged     whether its posture has it closing on or firing at something
     * @param bayUsable   whether its zone's bay stands and the zone is online
     * @param contingency whether the contingency is on
     * @param atBay       whether it stands within {@link #REARM_REACH} of the bay
     */
    public Step decide(boolean racksSpent, boolean racksFull, boolean engaged, boolean bayUsable, boolean contingency,
                       boolean atBay) {
        if (!going && racksSpent && !engaged && bayUsable && !contingency) going = true;
        if (going && (racksFull || engaged || !bayUsable || contingency)) going = false;
        if (!going) {
            progress = 0f;
            return Step.NONE;
        }
        return atBay ? Step.LOAD : Step.WALK;
    }

    /** Advances the loading at the bay by {@code seconds}; returns how many packs go into each rack now. */
    public int packs(float seconds) {
        progress += Math.max(0f, seconds);
        int packs = (int) (progress / PACK_SECONDS);
        progress -= packs * PACK_SECONDS;
        return packs;
    }

    public static boolean atBay(float x, float y, float bayX, float bayY) {
        return Math.max(Math.abs(x - bayX), Math.abs(y - bayY)) <= REARM_REACH;
    }
}
