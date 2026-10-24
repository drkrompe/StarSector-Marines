package com.dillon.starsectormarines.battle.decision.goap.action;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Gives each member of a squad, in ordinal order, a stand cell no earlier member was given.
 *
 * <p>Each member's own rule ("the best cover near my share of the line") is sound alone and
 * converges in a crowd: shares a couple of cells apart search overlapping ground, and the best
 * covered cell in it is the same cell for all of them. Every member then walks to one cell
 * centre, separation keeps any of them from reaching it, and none of them ever counts as
 * arrived. Picking in ordinal order with the earlier picks excluded settles the crowd once, and
 * the answer depends only on the inputs the picker reads, not on which member asks first.
 */
final class DistinctStands {

    /** Where member {@code ordinal} would stand, avoiding every cell {@code taken} accepts; {@code null} for a member that needs no stand. */
    interface Picker {
        int[] pick(int ordinal, BiPredicate<Integer, Integer> taken);
    }

    /** No stand for this ordinal. */
    static final int NONE = -1;

    private DistinctStands() { }

    /**
     * Each ordinal's stand as {@code y * width + x}, or {@link #NONE}. A picker that cannot avoid
     * an earlier pick (nothing else free) may still return it; the cell is then shared rather than
     * the member left without a stand.
     */
    static int[] assign(int count, int width, Picker picker) {
        int[] cells = new int[count];
        Arrays.fill(cells, NONE);
        Set<Integer> taken = new HashSet<>();
        BiPredicate<Integer, Integer> isTaken = (x, y) -> taken.contains(y * width + x);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            int[] cell = picker.pick(ordinal, isTaken);
            if (cell == null) continue;
            cells[ordinal] = cell[1] * width + cell[0];
            taken.add(cells[ordinal]);
        }
        return cells;
    }
}
