package com.dillon.starsectormarines.battle.profile;

/**
 * Per-phase wall-clock profile of one {@code BattleSimulation.tick()} call.
 * The sim calls {@link #begin()} at the top of the tick, then {@link #lap(Phase)}
 * after each top-level phase (rebuilds, GOAP replan, updateUnit loop,
 * weapon-subsystem ticks, demolish passes, FX ticks, etc.). At
 * {@link #endTick()} the per-phase accumulators advance toward a windowed
 * snapshot — every {@link #WINDOW_TICKS} ticks the current accumulator is
 * frozen into the "display" buffer that the debug panel reads, so the on-
 * screen numbers are stable averages rather than per-tick jitter.
 *
 * <p>Always-on cost: ~24 {@code System.nanoTime()} calls per tick (~1-2 µs
 * total) plus a few longs of accumulation. The point of this widget is to
 * inform the data-oriented refactor — we want the numbers live, not gated
 * behind a debug toggle that ships off by default. Display / dump are
 * panel-gated; the sim-side instrumentation is unconditional.
 *
 * <p>Display semantics: {@link #avgNanos(Phase)} returns the average nanos
 * spent in that phase over the last completed {@link #WINDOW_TICKS}-tick
 * window. {@link #maxNanos(Phase)} returns the worst single-tick value seen
 * in that window — the spike, not the average. {@link #totalAvgNanos()} is
 * the sum of the per-phase averages; it approximates the average total tick
 * time, modulo any work outside the lap brackets.
 */
public final class TickProfile {

    /**
     * One entry per top-level phase inside {@code BattleSimulation.tick()}.
     * Order matches execution order so the panel's row order mirrors what
     * actually happens each tick — easier to reason about than alphabetic
     * or by-cost sorting (which would change frame-to-frame).
     */
    public enum Phase {
        VISION,
        REBUILD_OCCUPANCY,
        REBUILD_UNIT_INDEX,
        REBUILD_ATTACKERS,
        SQUAD_ALERT,
        SQUAD_MORALE,
        SQUAD_FALLBACK,
        COMMANDER,
        GOAP_REPLAN,
        UPDATE_UNITS,
        INFANTRY_TICK,
        HEAVY_TICK,
        PROJECTILES,
        DETONATIONS,
        DEMOLISH_TURRETS,
        DEMOLISH_HUBS,
        DRONE_CRASHES,
        WRECKS,
        PLUMES,
        AIR_SYSTEM,
        GROUND_SYSTEM,
        SHOTS,
        EQUIPMENT_DROPS,
        OBJECTIVES,
        ZONE_GRAPH,
        WIN_CHECK;

        /** Cached array — avoids the {@code values()} clone on every panel render. */
        public static final Phase[] VALUES = values();
    }

    /** Ticks per averaging window. 30 = 1 sim-second at 30Hz, slow enough for the eye to read stable numbers. */
    public static final int WINDOW_TICKS = 30;

    // Running accumulators for the in-progress window.
    private final long[] accumNanos = new long[Phase.VALUES.length];
    private final long[] currentMaxNanos = new long[Phase.VALUES.length];
    private int currentSampleCount;

    // Display buffer — frozen averages + per-phase max from the last completed window.
    private final long[] displayAvgNanos = new long[Phase.VALUES.length];
    private final long[] displayMaxNanos = new long[Phase.VALUES.length];
    private int displaySampleCount;

    private long lapStart;

    /** Latches the lap timer at the top of {@code tick()}. Pair with {@link #lap(Phase)} for every phase, then {@link #endTick()} once at the end. */
    public void begin() {
        lapStart = System.nanoTime();
    }

    /** Records {@code nanoTime() - lastLap} into the phase's accumulator and resets the lap timer. Safe to call for conditional phases — if the body was a single boolean check, the recorded value is just that, which is the truthful answer. */
    public void lap(Phase p) {
        long now = System.nanoTime();
        long delta = now - lapStart;
        int idx = p.ordinal();
        accumNanos[idx] += delta;
        if (delta > currentMaxNanos[idx]) currentMaxNanos[idx] = delta;
        lapStart = now;
    }

    /**
     * Counts one tick. When the window fills, freeze the averages + per-phase
     * max into the display buffer and reset the accumulators. The display
     * buffer holds steady between snapshots so the panel reads stable values
     * even if the sim pauses mid-window.
     */
    public void endTick() {
        currentSampleCount++;
        if (currentSampleCount < WINDOW_TICKS) return;
        int n = Phase.VALUES.length;
        for (int i = 0; i < n; i++) {
            displayAvgNanos[i] = accumNanos[i] / currentSampleCount;
            displayMaxNanos[i] = currentMaxNanos[i];
            accumNanos[i] = 0L;
            currentMaxNanos[i] = 0L;
        }
        displaySampleCount = currentSampleCount;
        currentSampleCount = 0;
    }

    /** Average nanos spent in {@code p} during the last completed window. */
    public long avgNanos(Phase p) { return displayAvgNanos[p.ordinal()]; }

    /** Worst single-tick nanos seen for {@code p} during the last completed window. */
    public long maxNanos(Phase p) { return displayMaxNanos[p.ordinal()]; }

    /** Number of ticks summarized by the current display buffer. {@code 0} until the first window completes. */
    public int sampleCount() { return displaySampleCount; }

    /** Sum of per-phase averages — approximates the average wall time of one tick. */
    public long totalAvgNanos() {
        long sum = 0L;
        for (long v : displayAvgNanos) sum += v;
        return sum;
    }
}
