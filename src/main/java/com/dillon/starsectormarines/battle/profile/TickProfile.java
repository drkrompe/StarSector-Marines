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

    /** Multiplier above the windowed average tick time that latches a spike for auto-dump. 3× catches genuine outliers (rebuild flushes, JIT compiles, GC pauses) without firing on routine jitter. */
    public static final double SPIKE_MULTIPLIER = 3.0;
    /** Hard floor on absolute tick time for spike detection — below this the sim is so quiet that "3× the average" is meaningless noise. 1ms = 30Hz tick using 3% of its 33.3ms budget. */
    public static final long SPIKE_FLOOR_NS = 1_000_000L;

    // Running accumulators for the in-progress window.
    private final long[] accumNanos = new long[Phase.VALUES.length];
    private final long[] currentMaxNanos = new long[Phase.VALUES.length];
    private int currentSampleCount;

    /** Per-phase nanos spent in the most recent tick — reset by {@link #begin()}, populated by {@link #lap(Phase)}, consumed by {@link #endTick()} for spike detection and exposed via {@link #lastTickNanos(Phase)} so the panel could show per-tick (not per-window) numbers if it wanted to. */
    private final long[] currentTickNanos = new long[Phase.VALUES.length];

    // Display buffer — frozen averages + per-phase max from the last completed window.
    private final long[] displayAvgNanos = new long[Phase.VALUES.length];
    private final long[] displayMaxNanos = new long[Phase.VALUES.length];
    private int displaySampleCount;

    // Spike latch. endTick() writes when a spike fires; consumers (the debug
    // panel's auto-dump path) read via consumeSpike() which atomically clears.
    private boolean spikePending;
    private int spikeTickIndex;
    private long spikeTotalNanos;
    private long spikeBaselineNanos;

    private long lapStart;

    /** Latches the lap timer at the top of {@code tick()}. Pair with {@link #lap(Phase)} for every phase, then {@link #endTick(int)} once at the end. */
    public void begin() {
        // Wipe the per-tick scratch so spike detection at endTick() sees only
        // this tick's deltas. Window accumulators persist across ticks.
        for (int i = 0; i < currentTickNanos.length; i++) {
            currentTickNanos[i] = 0L;
        }
        lapStart = System.nanoTime();
    }

    /** Records {@code nanoTime() - lastLap} into the phase's accumulator and resets the lap timer. Safe to call for conditional phases — if the body was a single boolean check, the recorded value is just that, which is the truthful answer. */
    public void lap(Phase p) {
        long now = System.nanoTime();
        long delta = now - lapStart;
        int idx = p.ordinal();
        accumNanos[idx] += delta;
        currentTickNanos[idx] += delta;
        if (delta > currentMaxNanos[idx]) currentMaxNanos[idx] = delta;
        lapStart = now;
    }

    /**
     * Counts one tick. Two responsibilities:
     * <ol>
     *   <li>Spike detection — sum the per-phase nanos for this tick, compare
     *       against the windowed total average; latch a pending spike if the
     *       tick blew past {@link #SPIKE_MULTIPLIER}× baseline and the absolute
     *       time was above {@link #SPIKE_FLOOR_NS}. Only fires once a complete
     *       window has populated the baseline; the first window is warmup.</li>
     *   <li>Window snapshot — when {@link #WINDOW_TICKS} have accumulated,
     *       freeze the averages + per-phase max into the display buffer and
     *       reset the accumulators. The display buffer holds steady between
     *       snapshots so the panel reads stable values even if the sim pauses
     *       mid-window.</li>
     * </ol>
     *
     * @param simTickIndex the sim's monotonic tick counter; recorded on a
     *                     pending spike so consumers can tag the dump with the
     *                     tick that fired it.
     */
    public void endTick(int simTickIndex) {
        // --- Spike detection ---
        // Skip while baseline is uninitialized (first window) — without a
        // reference there's no "spike", just first-tick wall-clock noise.
        if (displaySampleCount > 0 && !spikePending) {
            long baselineTotal = totalAvgNanos();
            long thisTickTotal = 0L;
            for (long v : currentTickNanos) thisTickTotal += v;
            if (thisTickTotal >= SPIKE_FLOOR_NS
                    && baselineTotal > 0L
                    && thisTickTotal >= (long) (baselineTotal * SPIKE_MULTIPLIER)) {
                spikePending = true;
                spikeTickIndex = simTickIndex;
                spikeTotalNanos = thisTickTotal;
                spikeBaselineNanos = baselineTotal;
            }
        }

        // --- Window snapshot ---
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

    /** Nanos spent in {@code p} during the most recent {@link #endTick(int)}. Useful when consumers want per-tick (not per-window-average) numbers. */
    public long lastTickNanos(Phase p) { return currentTickNanos[p.ordinal()]; }

    /**
     * Returns the latched spike (if any) and clears the pending flag, so the
     * next call returns null until {@link #endTick(int)} latches a fresh one.
     * Returns {@code null} when no spike is pending — the common case.
     *
     * <p>Consumer (the debug panel) is expected to drive auto-dump from this:
     * poll each frame, dump when non-null, throttle / cap on its own side.
     */
    public Spike consumeSpike() {
        if (!spikePending) return null;
        spikePending = false;
        return new Spike(spikeTickIndex, spikeTotalNanos, spikeBaselineNanos);
    }

    /** Snapshot of one latched spike. {@code totalNanos / baselineNanos} gives the ratio above normal. */
    public static final class Spike {
        public final int tickIndex;
        public final long totalNanos;
        public final long baselineNanos;
        public Spike(int tickIndex, long totalNanos, long baselineNanos) {
            this.tickIndex = tickIndex;
            this.totalNanos = totalNanos;
            this.baselineNanos = baselineNanos;
        }
        public double ratio() {
            return baselineNanos > 0L ? (double) totalNanos / baselineNanos : 0.0;
        }
    }
}
