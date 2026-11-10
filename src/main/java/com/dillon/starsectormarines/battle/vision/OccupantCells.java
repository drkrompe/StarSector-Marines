package com.dillon.starsectormarines.battle.vision;

import java.util.Arrays;

/**
 * The cells ({@code x + y * gridWidth}) the player's own units stand on, for the building open rule
 * ({@link BuildingVisibilityPass}). It grows as it fills and keeps its storage across ticks.
 *
 * <p>The cells and their count travel together: a caller once passed the bare array beside a count that the same
 * call computed, so the array was read before the fill grew it and a 65th occupant indexed past the old 64 slots.
 */
public final class OccupantCells {

    private int[] cells;
    private int size;

    public OccupantCells(int initialCapacity) {
        cells = new int[Math.max(1, initialCapacity)];
    }

    /** Empties the list, keeping its storage. */
    public void clear() {
        size = 0;
    }

    /** Adds one occupied cell, growing the storage when full. */
    public void add(int cell) {
        if (size == cells.length) cells = Arrays.copyOf(cells, size * 2);
        cells[size++] = cell;
    }

    public int size() {
        return size;
    }

    /** The {@code k}th occupied cell, {@code 0 <= k < size()}. */
    public int get(int k) {
        return cells[k];
    }
}
