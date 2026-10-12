package com.dillon.starsectormarines.battle.setup;

/** Finite dispatch timetable: repeated capture cannot replenish attackers or restart a wave. */
public final class ThunderRunPacing {
    public static final int TICKS_PER_SECOND = 30;
    public static final int RAIDERS_PER_BATCH = 4;
    public static final int BATCHES = 11;
    public static final int RESERVE_SIZE = RAIDERS_PER_BATCH * BATCHES;
    public static final int FREIGHT_SPACING_TICKS = 45 * TICKS_PER_SECOND;
    private static final int[] PHASE = {0, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3};
    private static final int[] SECOND = {0, 0, 25, 55, 0, 25, 55, 85, 0, 35, 70};
    private final boolean[] released = new boolean[BATCHES];

    public static int holdTicks(int phase) {
        return (phase == 1 ? 90 : phase == 2 ? 120 : 0) * TICKS_PER_SECOND;
    }

    public boolean takeBatch(int batch, int phase, int elapsedTicks) {
        if (released[batch] || phase != PHASE[batch] || elapsedTicks < SECOND[batch] * TICKS_PER_SECOND)
            return false;
        released[batch] = true;
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
