package com.dillon.starsectormarines.ops.battleview;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Opt-in timing of the headless review drain ({@code -Dsnapshot.profile=true}): where a review frame's wall clock
 * goes by phase, and where the drain's draw time goes by kind of draw and by image. Prints a table every
 * {@code -Dsnapshot.profile.every=N} frames (default 100) and once at exit. Off, it costs one boolean test per
 * call. Draw times are summed across bands, so with bands they are worker time, not wall time.
 */
final class ReviewRenderProfile {

    static final boolean ENABLED = Boolean.getBoolean("snapshot.profile");
    private static final int EVERY = Integer.getInteger("snapshot.profile.every", 100);

    private static final Map<String, LongAdder> PHASES = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> DRAWS = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> DRAW_COUNTS = new ConcurrentHashMap<>();
    private static final AtomicLong FRAMES = new AtomicLong();

    static {
        if (ENABLED) Runtime.getRuntime().addShutdownHook(new Thread(ReviewRenderProfile::print));
    }

    private ReviewRenderProfile() { }

    static void phase(String name, long nanos) {
        PHASES.computeIfAbsent(name, key -> new LongAdder()).add(nanos);
    }

    static void draw(String key, long nanos) {
        DRAWS.computeIfAbsent(key, k -> new LongAdder()).add(nanos);
        DRAW_COUNTS.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    static void frameDone() {
        if (FRAMES.incrementAndGet() % EVERY == 0) print();
    }

    private static synchronized void print() {
        long frames = Math.max(1, FRAMES.get());
        StringBuilder out = new StringBuilder(String.format(Locale.ROOT, "[review-profile] %d frames%n", frames));
        PHASES.entrySet().stream().sorted(Map.Entry.<String, LongAdder>comparingByValue(
                Comparator.comparingLong(LongAdder::sum)).reversed()).forEach(e -> out.append(String.format(
                Locale.ROOT, "  phase %-28s %9.1f ms/frame%n", e.getKey(), e.getValue().sum() / 1e6 / frames)));
        DRAWS.entrySet().stream().sorted(Map.Entry.<String, LongAdder>comparingByValue(
                Comparator.comparingLong(LongAdder::sum)).reversed()).limit(25).forEach(e -> out.append(String.format(
                Locale.ROOT, "  draw  %-90s %9.1f ms/frame %8.1f /frame%n", e.getKey(),
                e.getValue().sum() / 1e6 / frames, DRAW_COUNTS.get(e.getKey()).sum() / (double) frames)));
        System.out.print(out);
    }
}
