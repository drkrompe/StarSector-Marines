package com.dillon.starsectormarines.battle.nav;

/**
 * Pure read helpers over a flat {@code int[]} path — the interleaved
 * {@code x,y}-pair encoding {@link GridPathfinder} produces (cell {@code i} sits
 * at {@code (path[i*2], path[i*2+1])}). Stateless statics so a caller that has
 * fetched the path reference once can interrogate it without a per-read store
 * lookup — the fetch-once idiom the hot nav/render loops use now that the path
 * reference lives in the entity world's {@code MOVEMENT} component rather than
 * as an {@code Entity} field.
 *
 * <p>An empty path ({@link GridPathfinder#EMPTY_PATH}, length 0) means "nothing
 * scheduled"; {@link #destX}/{@link #destY} report {@link Integer#MIN_VALUE} for
 * it (the "no destination" sentinel the occupancy bookkeeping keys off).
 */
public final class Paths {

    private Paths() {}

    /** Number of cells in {@code path}. */
    public static int cellCount(int[] path) {
        return path.length >> 1;
    }

    /** X coordinate of the {@code i}-th cell along {@code path}. */
    public static int cellX(int[] path, int i) {
        return path[i << 1];
    }

    /** Y coordinate of the {@code i}-th cell along {@code path}. */
    public static int cellY(int[] path, int i) {
        return path[(i << 1) | 1];
    }

    /** True when {@code path} schedules no cells. */
    public static boolean isEmpty(int[] path) {
        return path.length == 0;
    }

    /** X coordinate of the final path cell, or {@link Integer#MIN_VALUE} if the path is empty. */
    public static int destX(int[] path) {
        return path.length == 0 ? Integer.MIN_VALUE : path[path.length - 2];
    }

    /** Y coordinate of the final path cell, or {@link Integer#MIN_VALUE} if the path is empty. */
    public static int destY(int[] path) {
        return path.length == 0 ? Integer.MIN_VALUE : path[path.length - 1];
    }
}
