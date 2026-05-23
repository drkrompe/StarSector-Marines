package com.dillon.starsectormarines.battle.mapgen.bsp.fill;

/**
 * Describes the interior partition(s) a {@link PartitionStrategy} carved
 * inside a building shell. Consumers read orientation + axis positions to
 * align perimeter doorways and compute chamber indices for room labeling.
 *
 * <p>Binary partitions carry a single axis; ternary (Slice C) will carry
 * two. The {@link #chamberIndex} method abstracts over the count so
 * callers don't need to know how many walls exist.
 */
final class PartitionLayout {

    enum Orient { NONE, VERTICAL, HORIZONTAL }

    static final PartitionLayout NONE = new PartitionLayout(Orient.NONE, -1);

    final Orient orient;
    final int axis;

    PartitionLayout(Orient orient, int axis) {
        this.orient = orient;
        this.axis = axis;
    }

    /**
     * Returns the chamber index (0..N-1) for a cell, or {@code -1} when
     * the cell sits exactly on a partition axis (wall or doorway cell).
     * Single-chamber buildings ({@link #NONE}) always return 0.
     */
    int chamberIndex(int x, int y) {
        if (orient == Orient.NONE) return 0;
        int coord = (orient == Orient.VERTICAL) ? x : y;
        if (coord == axis) return -1;
        return coord < axis ? 0 : 1;
    }
}
