package com.dillon.starsectormarines.battle.command.allied;

import java.util.HashSet;
import java.util.Set;

/**
 * What one hull has been refused since it last moved ({@code allied-command.md}, refused goals): the goals, where
 * it stood, and how many stall retries in a row have found it there. Asking the route proof again from the same
 * place is asking it the same question, so the commander pulls a remembered goal back without an order. A move
 * forgets everything; age forgets the goals but not the retries, because the ground can change under a hull that
 * has not (a fire through the undergrowth, a wreck towed off) and a goal is worth asking once more.
 */
final class RefusedGoals {

    private final float originX, originY;
    private final float movedCells;
    private final int memoryTicks;
    private int since;
    private final Set<Long> goals = new HashSet<>();
    private int stallRetries;

    RefusedGoals(float originX, float originY, int tick, float movedCells, int memoryTicks) {
        this.originX = originX;
        this.originY = originY;
        this.since = tick;
        this.movedCells = movedCells;
        this.memoryTicks = memoryTicks;
    }

    /** Whether a hull now at ({@code x}, {@code y}) has moved away from where this was refused. */
    boolean movedAway(float x, float y) {
        float dx = x - originX, dy = y - originY;
        return dx * dx + dy * dy > movedCells * movedCells;
    }

    /** Forgets the goals once they are older than the memory, keeping the retry count. */
    void age(int tick) {
        if (tick - since <= memoryTicks) return;
        goals.clear();
        since = tick;
    }

    void remember(int goalX, int goalY) {
        goals.add(key(goalX, goalY));
    }

    boolean refused(int goalX, int goalY) {
        return goals.contains(key(goalX, goalY));
    }

    void stallRetried() {
        stallRetries++;
    }

    /** The stall retry wait: {@code baseTicks} doubled for each retry in a row here, at most {@code limit} times. */
    int stallWait(int baseTicks, int limit) {
        return baseTicks << Math.min(stallRetries, limit);
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }
}
