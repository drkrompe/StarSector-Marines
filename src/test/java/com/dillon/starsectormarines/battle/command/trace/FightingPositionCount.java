package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;

/**
 * How much prepared ground a battle stood on: the number of fighting-position runs and the cells
 * in them, at the first tick and at the last, so a fort-bearing map is shown to produce positions
 * and a shelling that destroys earthworks shows as a smaller end figure.
 */
record FightingPositionCount(int startRuns, int startCells, int endRuns, int endCells) {

    /** A report built without a replay, such as a unit test of the writer. */
    static final FightingPositionCount UNMEASURED = new FightingPositionCount(0, 0, 0, 0);

    /** Total cells over every run of an index. */
    static int cells(FightingPositionIndex index) {
        int total = 0;
        for (int run = 0; run < index.runCount(); run++) total += index.runSize(run);
        return total;
    }

    /** The count for a run that started with {@code start} and ended with {@code end}. */
    static FightingPositionCount of(FightingPositionIndex start, FightingPositionIndex end) {
        return new FightingPositionCount(start.runCount(), cells(start), end.runCount(), cells(end));
    }

    String json() {
        return "{\"startRuns\":" + startRuns + ",\"startCells\":" + startCells
                + ",\"endRuns\":" + endRuns + ",\"endCells\":" + endCells + "}";
    }
}
