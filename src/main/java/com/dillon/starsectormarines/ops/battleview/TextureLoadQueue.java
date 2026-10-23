package com.dillon.starsectormarines.ops.battleview;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.ObjLongConsumer;

/**
 * A battle's texture loads, spread across frames ({@code battle-textures.md}).
 *
 * <p>Uploading a texture is GL work and happens on the render thread, so a battle that loads
 * everything in one call freezes the game for as long as that takes. The queue runs steps until
 * a frame's budget is spent and leaves the rest for the next frame, so the loading screen keeps
 * drawing in between. A step is never split: one that is larger than the budget runs alone in
 * its frame, and every frame runs at least one, so the queue always finishes.
 */
public final class TextureLoadQueue implements LoadingProgress {

    /** One unit of loading and the stage the loading screen names while it runs. */
    public record Step(String stage, Runnable work) { }

    private final List<Step> steps;
    private final LongSupplier clock;
    private int next;
    private long slowStepNanos = Long.MAX_VALUE;
    private ObjLongConsumer<Step> slowStep = (step, nanos) -> { };

    public TextureLoadQueue(List<Step> steps) {
        this(steps, System::nanoTime);
    }

    TextureLoadQueue(List<Step> steps, LongSupplier clock) {
        this.steps = new ArrayList<>(steps);
        this.clock = clock;
    }

    /**
     * Reports every step that runs longer than {@code thresholdNanos}. A step that long holds the
     * GL thread past several frames, so the loading screen's spinner stops while it runs; it is
     * the step to split.
     */
    public TextureLoadQueue onSlowStep(long thresholdNanos, ObjLongConsumer<Step> listener) {
        slowStepNanos = thresholdNanos;
        slowStep = listener;
        return this;
    }

    /** Runs steps for up to {@code budgetNanos}, and always at least one while any remain. */
    public void advance(long budgetNanos) {
        long start = clock.getAsLong();
        while (next < steps.size()) {
            Step step = steps.get(next++);
            long stepStart = clock.getAsLong();
            step.work().run();
            long end = clock.getAsLong();
            if (end - stepStart > slowStepNanos) slowStep.accept(step, end - stepStart);
            if (end - start >= budgetNanos) return;
        }
    }

    /**
     * Adds {@code more} to the end of the queue, for a step that learns what is left to load only
     * by running: it is run in later frames, like the rest.
     */
    public void append(List<Step> more) {
        steps.addAll(more);
    }

    public boolean done() {
        return next >= steps.size();
    }

    @Override
    public String stage() {
        if (steps.isEmpty()) return "";
        return steps.get(Math.min(next, steps.size() - 1)).stage();
    }

    @Override
    public int stepCount() {
        return steps.size();
    }

    @Override
    public int stepsDone() {
        return next;
    }
}
