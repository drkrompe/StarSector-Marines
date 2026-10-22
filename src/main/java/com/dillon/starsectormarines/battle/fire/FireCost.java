package com.dillon.starsectormarines.battle.fire;

import java.util.Arrays;

/**
 * What the battle's fire has cost the simulation thread, tick by tick, while
 * anything burned or warmed: the field's step, its harm and its route costing
 * together. Two clock reads a tick while a fire exists and nothing otherwise;
 * read by the fire evidence (`fire-nouns.md`, Evidence).
 */
public final class FireCost {

    private int[] micros = new int[1024];
    private int count;
    private long total;
    private int steps;

    /** Records one tick's fire work, in nanoseconds; {@code stepped} marks a fire step. */
    public void record(long nanos, boolean stepped) {
        int us = (int) Math.min(Integer.MAX_VALUE, nanos / 1000L);
        if (count == micros.length) micros = Arrays.copyOf(micros, count * 2);
        micros[count++] = us;
        total += nanos;
        if (stepped) steps++;
    }

    /** Ticks recorded. */
    public int ticks() { return count; }
    /** Fire steps among them. */
    public int steps() { return steps; }
    /** Mean milliseconds a recorded tick spent on fire. */
    public double meanMillis() { return count == 0 ? 0 : total / 1e6 / count; }
    public double maxMillis() { return percentileMillis(1.0); }
    public double p99Millis() { return percentileMillis(0.99); }

    private double percentileMillis(double q) {
        if (count == 0) return 0;
        int[] sorted = Arrays.copyOf(micros, count);
        Arrays.sort(sorted);
        int at = (int) Math.min(count - 1, Math.max(0, Math.ceil(q * count) - 1));
        return sorted[at] / 1000.0;
    }
}
