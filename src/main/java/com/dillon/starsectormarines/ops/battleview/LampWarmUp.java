package com.dillon.starsectormarines.ops.battleview;

/**
 * How a lamp comes on when its circuit is powered ({@code battle-render-nouns.md}, night lighting): not all at once,
 * but as tubes strike. Each lamp waits its own short delay, flickers for a moment and then holds steady, so a band of
 * the plant comes up across a second or two rather than in one frame. Presentation only; a circuit cut goes dark at
 * once. Pure: a lamp's state is a function of its index and the seconds since its circuit was powered.
 */
final class LampWarmUp {

    /** The longest a lamp waits before it first strikes. */
    static final float MAX_DELAY_SECONDS = 1.4f;
    /** How long a lamp flickers once it first strikes, before it holds. */
    static final float FLICKER_SECONDS = 0.7f;
    /** Flicker steps a second while it strikes. */
    static final int FLICKERS_PER_SECOND = 14;
    /** Share of flicker steps the tube is lit, rising as it settles. */
    static final float LIT_SHARE = 0.45f;

    private LampWarmUp() {}

    /** Seconds the lamp at {@code index} waits after its circuit is powered before it first strikes. */
    static float delay(int index) {
        return hash(index, 0x51ed27) * MAX_DELAY_SECONDS;
    }

    /** Seconds from its circuit being powered after which the lamp holds steady. */
    static float settled(int index) {
        return delay(index) + FLICKER_SECONDS;
    }

    /** Whether the lamp at {@code index} is lit {@code seconds} after its circuit was powered. */
    static boolean lit(int index, float seconds) {
        float t = seconds - delay(index);
        if (t < 0f) return false;
        if (t >= FLICKER_SECONDS) return true;
        // The first step always strikes; later steps are lit more often as the tube settles.
        int step = (int) (t * FLICKERS_PER_SECOND);
        if (step == 0) return true;
        float settle = t / FLICKER_SECONDS;
        return hash(index, step) < LIT_SHARE + (1f - LIT_SHARE) * settle;
    }

    /** A stable value in [0, 1) for a lamp and a salt. */
    static float hash(int index, int salt) {
        int h = index * 0x9E3779B1 ^ salt * 0x85EBCA6B;
        h ^= h >>> 16;
        h *= 0x7FEB352D;
        h ^= h >>> 15;
        h *= 0x846CA68B;
        h ^= h >>> 16;
        return (h >>> 8) / (float) (1 << 24);
    }
}
