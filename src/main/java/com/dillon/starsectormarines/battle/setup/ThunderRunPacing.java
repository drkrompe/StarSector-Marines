package com.dillon.starsectormarines.battle.setup;

/** Finite dispatch timetable: repeated capture cannot replenish attackers or restart a wave. */
public final class ThunderRunPacing {
    public static final int TICKS_PER_SECOND = 30;
    public static final int RAIDERS_PER_BATCH = 4;
    public static final int BATCHES = 23;
    public static final int RESERVE_SIZE = RAIDERS_PER_BATCH * BATCHES;
    public static final int FREIGHT_SPACING_TICKS = 22 * TICKS_PER_SECOND;
    private static final int[] PHASE = {0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2};
    private static final int[] SECOND = {0, 0, 24, 48, 82, 116, 0, 24, 48, 78, 90, 108, 138, 0, 22, 46, 64, 132, 160, 190, 220, 250, 280};
    private final boolean[] released = new boolean[BATCHES];
    private final int[] phaseStarted = {-1, -1, -1, -1};
    private int nextReleaseTick;

    public static int batchPhase(int batch) { return PHASE[batch]; }
    /** Later Crossing squads block the next leg instead of chasing the company's rear. */
    public static boolean usesOutboundRoad(int batch) { return batch == 10 || (batch >= 12 && batch <= 16); }

    /** Rear attacks start after the Crossing hold, while the company advances on the last objective. */
    public static int rearPost(int batch) { return batch >= 17 ? (batch - 17) % 2 : -1; }

    public static int holdTicks(int phase) {
        return (phase == 1 ? 90 : phase == 2 ? 120 : 0) * TICKS_PER_SECOND;
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

    public static boolean cargoDue(int ordinal, int elapsedTicks) {
        return elapsedTicks >= 14 * TICKS_PER_SECOND + ordinal * FREIGHT_SPACING_TICKS;
    }
}
