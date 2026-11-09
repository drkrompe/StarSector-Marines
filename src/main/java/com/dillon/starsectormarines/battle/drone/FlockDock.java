package com.dillon.starsectormarines.battle.drone;

/**
 * A flock's drones docked in its cradle (`air-nouns.md`, Flocks): out of play, neither seen nor seeing nor shot at,
 * until the flock is given something to do.
 *
 * <ul>
 *   <li>A flock going home ({@link FlockTask#RETURN}) and home lands each member that comes within
 *       {@link #LAND_CELLS} of its cradle: the drone leaves the field and is aboard.</li>
 *   <li>Any other task puts the drones aboard back up, one every {@link #LAUNCH_SECONDS}, the first at once, so a
 *       woken flock pours out of its cradle.</li>
 *   <li>A drone the cradle rebuilds while the flock is home is built aboard.</li>
 *   <li>The drones aboard are lost with the cradle; nothing lands in or leaves a cradle that is down.</li>
 * </ul>
 */
public final class FlockDock {

    /** A returning member this near its cradle lands, cells. */
    public static final float LAND_CELLS = 1.5f;
    /** Seconds between two drones leaving the cradle. */
    public static final float LAUNCH_SECONDS = 0.15f;

    private int aboard;
    private float launchClock = LAUNCH_SECONDS;

    /** Drones aboard the cradle now. */
    public int aboard() { return aboard; }

    /** Whether a member {@code distance} cells from its cradle lands this tick. */
    public static boolean lands(FlockTask task, boolean home, boolean cradleStands, float distance) {
        return task == FlockTask.RETURN && home && cradleStands && distance <= LAND_CELLS;
    }

    /** One member landed, or one drone built aboard. */
    public void board() {
        aboard++;
    }

    /**
     * Advances by {@code dt}; how many drones leave the cradle this tick. None while the flock is going home or the
     * cradle is down; the cadence starts again each time the flock is sent out.
     */
    public int launches(float dt, FlockTask task, boolean cradleStands) {
        if (task == FlockTask.RETURN || !cradleStands || aboard == 0) {
            launchClock = LAUNCH_SECONDS;
            if (!cradleStands) aboard = 0;
            return 0;
        }
        launchClock += dt;
        int out = 0;
        while (launchClock >= LAUNCH_SECONDS && aboard > 0) {
            launchClock -= LAUNCH_SECONDS;
            aboard--;
            out++;
        }
        return out;
    }
}
