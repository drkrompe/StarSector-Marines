package com.dillon.starsectormarines.battle.setup;

/**
 * Finite dispatch timetable: repeated capture cannot replenish attackers or restart a wave.
 *
 * <p>Batches are listed by role, not by time: the opening rocket team, Dispatch
 * pressure, Crossing pressure, one Outbound-road blocker, the final-run
 * interdiction that meets the column itself, and the rear attacks on the
 * militia posts. Each is released on its own phase's clock.
 *
 * <p>Most of the pressure lands on the final run. The company still has to
 * take the Outbound Gate with what it has left after two holds, so the holds
 * stay short and the Crossing gets four batches, not seven; a timetable that
 * wears the company out there never plays the convoy the mission is about.
 */
public final class ThunderRunPacing {
    public static final int TICKS_PER_SECOND = 30;
    public static final int RAIDERS_PER_BATCH = 4;
    public static final int BATCHES = 18;
    public static final int RESERVE_SIZE = RAIDERS_PER_BATCH * BATCHES;
    /** First of the rear-post attacks; every later batch is one too. */
    public static final int FIRST_REAR_BATCH = 14;
    private static final int[] PHASE = {0, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 3, 2, 2, 2, 2};
    private static final int[] SECOND = {0, 0, 20, 40, 64, 0, 22, 44, 68, 96, 0, 18, 36, 54, 84, 108, 132, 156};
    private final boolean[] released = new boolean[BATCHES];
    private final int[] phaseStarted = {-1, -1, -1, -1};
    private int nextReleaseTick;

    public static int batchPhase(int batch) { return PHASE[batch]; }
    /** The Outbound-road blocker and the final-run interdiction come in by the eastern road. */
    public static boolean usesOutboundRoad(int batch) { return batch >= 9 && batch < FIRST_REAR_BATCH; }

    /** Rear attacks start after the Crossing hold, while the company advances on the last objective. */
    public static int rearPost(int batch) { return batch >= FIRST_REAR_BATCH ? (batch - FIRST_REAR_BATCH) % 2 : -1; }

    /**
     * Raiders in this batch issued crude scrap rockets: the opening team against the
     * advance load, one Dispatch and one Crossing wave against the militia technical and
     * guns, and the first rear attack on each gun post.
     */
    public static int rocketCarriers(int batch) {
        return batch == 0 || batch == 2 || batch == 6 ? 2
                : batch == FIRST_REAR_BATCH || batch == FIRST_REAR_BATCH + 1 ? 1 : 0;
    }

    public static int holdTicks(int phase) {
        return (phase == 1 ? 60 : phase == 2 ? 80 : 0) * TICKS_PER_SECOND;
    }

    public boolean takeBatch(int batch, int phase, int tick) {
        if (phaseStarted[phase] < 0) phaseStarted[phase] = tick;
        int start = phaseStarted[PHASE[batch]];
        // Keep the original clock across territorial progress. A fast advance
        // neither cancels trailing pressure nor releases overdue squads in a pile.
        if (released[batch] || phase < PHASE[batch] || start < 0
                || tick < nextReleaseTick || tick - start < SECOND[batch] * TICKS_PER_SECOND)
            return false;
        released[batch] = true;
        nextReleaseTick = tick + 12 * TICKS_PER_SECOND;
        return true;
    }

    public int pendingMembers() {
        int pending = 0;
        for (boolean sent : released) if (!sent) pending += RAIDERS_PER_BATCH;
        return pending;
    }

    /** The final run's freight goes as one column, every hauler at once, after the armor's lead interval. */
    public static boolean freightDue(int elapsedTicks) {
        return elapsedTicks >= 12 * TICKS_PER_SECOND;
    }
}
