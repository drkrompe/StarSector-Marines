package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.DrawCensus;

import java.util.Arrays;

/**
 * One frame's cost, split by {@link RenderLayer}: what each layer's collector
 * spent, what its drain spent, and what the drain actually submitted.
 *
 * <p>The split is the whole point. A frame is slow as a total, and a total says
 * nothing about what to do next — the answer is different for a layer that
 * emits forty thousand cheap commands, a layer that emits four hundred
 * uncoalescable sprite draws, and a layer whose cost is one custom pass owning
 * its own GL. Attributing time to a layer is what separates them, and the
 * ordinary render path has no reason to do it, so this is opt-in: every
 * {@code census} argument is nullable and null in the game.
 *
 * <p>Times are wall-clock nanoseconds around the collector and the drain of one
 * layer. They are a measurement rather than a determinism guarantee: the counts
 * on a given fixture and framing repeat exactly, and the times do not.
 *
 * <p>Not thread-safe; render is single-threaded.
 */
public final class FrameCensus {

    private final DrawCensus[] drains;
    private final long[] collectNanos;
    private final long[] drainNanos;

    public FrameCensus() {
        int layers = RenderLayer.values().length;
        drains = new DrawCensus[layers];
        for (int i = 0; i < layers; i++) drains[i] = new DrawCensus();
        collectNanos = new long[layers];
        drainNanos = new long[layers];
    }

    /** One layer's drain tally, accumulated across however many frames were folded in. */
    public DrawCensus drain(RenderLayer layer) {
        return drains[layer.ordinal()];
    }

    /**
     * Every layer's drain tally, added together.
     *
     * <p>A fresh object rather than a retained total, so a caller cannot mistake
     * it for a layer's own and keep adding to it.
     */
    public DrawCensus total() {
        DrawCensus total = new DrawCensus();
        for (DrawCensus layer : drains) total.add(layer);
        return total;
    }

    public long collectNanos(RenderLayer layer) {
        return collectNanos[layer.ordinal()];
    }

    public long drainNanos(RenderLayer layer) {
        return drainNanos[layer.ordinal()];
    }

    public long totalCollectNanos() {
        long sum = 0;
        for (long nanos : collectNanos) sum += nanos;
        return sum;
    }

    public long totalDrainNanos() {
        long sum = 0;
        for (long nanos : drainNanos) sum += nanos;
        return sum;
    }

    void addCollectNanos(RenderLayer layer, long nanos) {
        collectNanos[layer.ordinal()] += nanos;
    }

    void addDrainNanos(RenderLayer layer, long nanos) {
        drainNanos[layer.ordinal()] += nanos;
    }

    public void reset() {
        for (DrawCensus layer : drains) layer.reset();
        Arrays.fill(collectNanos, 0L);
        Arrays.fill(drainNanos, 0L);
    }
}
