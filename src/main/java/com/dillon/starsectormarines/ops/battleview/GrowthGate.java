package com.dillon.starsectormarines.ops.battleview;

/**
 * Answers, once per count, whether a collection that only grows has grown since the last ask. A renderer polls it
 * each frame to register batches for sheets loaded after attach without walking the collection when nothing changed.
 */
final class GrowthGate {
    private int seen = -1;

    /** True the first time it is given {@code count}, and again whenever {@code count} differs from the last one. */
    boolean changed(int count) {
        if (count == seen) return false;
        seen = count;
        return true;
    }
}
