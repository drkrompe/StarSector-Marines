package com.dillon.starsectormarines.battle.decision;

/** Battle-local admission bound shared by the parallel squad readers. */
final class FiringPositionRefreshBudget {
    private final int limit;
    private int tick = Integer.MIN_VALUE;
    private int used;

    FiringPositionRefreshBudget(int limit) {
        if (limit < 1) throw new IllegalArgumentException("positive refresh limit required");
        this.limit = limit;
    }

    synchronized boolean acquire(int currentTick) {
        if (currentTick != tick) {
            tick = currentTick;
            used = 0;
        }
        if (used >= limit) return false;
        used++;
        return true;
    }
}
