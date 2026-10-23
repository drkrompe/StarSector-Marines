package com.dillon.starsectormarines.ops.battleview;

import java.util.List;
import java.util.function.LongSupplier;

/**
 * A battle's texture loads, spread across frames ({@code battle-textures.md}).
 *
 * <p>Uploading a texture is GL work and happens on the render thread, so a battle that loads
 * everything in one call freezes the game for as long as that takes. The queue runs steps until
 * a frame's budget is spent and leaves the rest for the next frame, so the loading screen keeps
 * drawing in between. A step is never split: one that is larger than the budget runs alone in
 * its frame, and every frame runs at least one, so the queue always finishes.
 */
public final class TextureLoadQueue {

    /** One unit of loading and the words the loading screen shows while it runs. */
    public record Step(String label, Runnable work) { }

    private final List<Step> steps;
    private final LongSupplier clock;
    private int next;

    public TextureLoadQueue(List<Step> steps) {
        this(steps, System::nanoTime);
    }

    TextureLoadQueue(List<Step> steps, LongSupplier clock) {
        this.steps = List.copyOf(steps);
        this.clock = clock;
    }

    /** Runs steps for up to {@code budgetNanos}, and always at least one while any remain. */
    public void advance(long budgetNanos) {
        long start = clock.getAsLong();
        while (next < steps.size()) {
            steps.get(next++).work().run();
            if (clock.getAsLong() - start >= budgetNanos) return;
        }
    }

    public boolean done() {
        return next >= steps.size();
    }

    /** Share of the steps already run, 0 to 1; an empty queue is complete. */
    public float progress() {
        return steps.isEmpty() ? 1f : (float) next / steps.size();
    }

    /** What the next step will load, or the last step's label once all have run. */
    public String label() {
        if (steps.isEmpty()) return "";
        return steps.get(Math.min(next, steps.size() - 1)).label();
    }

    public int stepCount() {
        return steps.size();
    }

    public int stepsRun() {
        return next;
    }
}
