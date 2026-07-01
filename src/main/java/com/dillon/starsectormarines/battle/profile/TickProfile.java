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
        APPLY_OCCUPANCY,
        APPLY_SPAWNS,
        FIRING,
        INFANTRY_TICK,
        HEAVY_TICK,
        PROJECTILES,
        DETONATIONS,
        APPLY_DAMAGE,
        DEMOLISH,
        DRONE_CRASHES,
        WRECKS,
        PLUMES,
        AIR_SYSTEM,
        GROUND_SYSTEM,
        SHOTS,
        EQUIPMENT_DROPS,
        OBJECTIVES,
        ZONE_GRAPH,
        WIN_CHECK,
        APPEARANCE;

        /** Cached array — avoids the {@code values()} clone on every panel render. */
        public static final Phase[] VALUES = values();
    }

    /** Ticks per averaging window. 30 = 1 sim-second at 30Hz, slow enough for the eye to read stable numbers. */
    public static final int WINDOW_TICKS = 30;

    /**
     * Sim ticks at the start of a battle during which spike detection and
     * baseline accumulation are both suppressed. The first few seconds of a
     * battle are JIT-warmup, lazy texture loads, vanilla {@code .ship} JSON
     * parsing — all one-off costs that would otherwise blow through the
     * auto-dump budget capturing noise instead of genuine sim spikes. At
     * 30Hz, 300 ticks = 10 sim-seconds, comfortably past the loading flurry
     * we observed in the early ticks (63, 105, 121, 186, 201, 220, 249).
     */
    public static final int WARMUP_TICKS = 300;

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

    /** Sim-tick index at the start of the current tick — latched in {@link #begin(int)} so {@link #lap(Phase)} and {@link #endTick(int)} can gate on warmup without re-passing it. */
    private int currentTickIndex;
    /** True while {@link #currentTickIndex} {@code <} {@link #WARMUP_TICKS}. Set in {@link #begin(int)} so the gate is consistent across the lap+endTick passes within one tick. */
    private boolean inWarmup = true;

    private long lapStart;

    /**
     * Latches the lap timer at the top of {@code tick()} and updates the
     * warmup gate. Pair with {@link #lap(Phase)} for every phase, then
     * {@link #endTick(int)} once at the end.
     *
     * <p>The first {@link #WARMUP_TICKS} ticks are JIT/load-time noise — during
     * that span {@link #lap(Phase)} skips accumulator updates and
     * {@link #endTick(int)} skips both window-sample counting and spike
     * detection. Past the gate the profile behaves normally; the first
     * post-warmup tick is sample #1 of a fresh baseline.
     */
    public void begin(int simTickIndex) {
        currentTickIndex = simTickIndex;
        inWarmup = simTickIndex < WARMUP_TICKS;
        // Per-tick scratch is only relevant for spike detection (post-warmup).
        // Still clear it during warmup so a warmup → post-warmup transition
        // doesn't leave stale per-phase nanos sitting in the array.
        for (int i = 0; i < currentTickNanos.length; i++) {
            currentTickNanos[i] = 0L;
        }
        lapStart = System.nanoTime();
    }

    /** Records {@code nanoTime() - lastLap} into the phase's accumulator and resets the lap timer. Safe to call for conditional phases — if the body was a single boolean check, the recorded value is just that, which is the truthful answer. Warmup ticks advance the lap timer but skip accumulator updates so JIT/load-time noise doesn't pollute the steady-state baseline. */
    public void lap(Phase p) {
        long now = System.nanoTime();
        long delta = now - lapStart;
        lapStart = now;
        if (inWarmup) return;
        int idx = p.ordinal();
        accumNanos[idx] += delta;
        currentTickNanos[idx] += delta;
        if (delta > currentMaxNanos[idx]) currentMaxNanos[idx] = delta;
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
     * @param innerProfile the per-tick inner profile; snapshotted (deep-copied)
     *                     when a spike fires so the dump retains the sub-step
     *                     numbers past the next tick's reset. May be
     *                     {@code null} — in which case the spike record lands
     *                     with no inner snapshot.
     */
    public void endTick(int simTickIndex, TickInnerProfile innerProfile) {
        // Warmup gate — JIT/lazy-load period contributes nothing to baseline
        // and can't latch a spike. Past this point everything runs normally.
        if (inWarmup) return;
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
                spikeInnerSnapshot = (innerProfile != null) ? innerProfile.snapshot() : null;
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

    /** True until the sim has crossed {@link #WARMUP_TICKS} ticks. While true, spike detection is suppressed and baselines aren't accumulating. */
    public boolean isWarmingUp() { return inWarmup; }

    /** Sim ticks remaining until warmup ends — clamped at 0 once past. Used by the panel to render a countdown so the user knows when steady-state begins. */
    public int warmupTicksRemaining() {
        int remaining = WARMUP_TICKS - currentTickIndex;
        return Math.max(0, remaining);
    }

    /**
     * Returns the latched spike (if any) and clears the pending flag, so the
     * next call returns null until {@link #endTick(int, TickInnerProfile)}
     * latches a fresh one. Returns {@code null} when no spike is pending —
     * the common case.
     *
     * <p>Consumer (the debug panel) is expected to drive auto-dump from this:
     * poll each frame, dump when non-null, throttle / cap on its own side.
     */
    public Spike consumeSpike() {
        if (!spikePending) return null;
        spikePending = false;
        Spike s = new Spike(spikeTickIndex, spikeTotalNanos, spikeBaselineNanos, spikeInnerSnapshot);
        spikeInnerSnapshot = null;
        return s;
    }

    /** Snapshot of one latched spike. {@code totalNanos / baselineNanos} gives the ratio above normal. {@code innerSnapshot} carries the per-behavior and per-primitive sub-step nanos for diagnostic dumps; may be {@code null} if no inner profile was attached when the spike fired. */
    public static final class Spike {
        public final int tickIndex;
        public final long totalNanos;
        public final long baselineNanos;
        public final TickInnerProfile.Snapshot innerSnapshot;
        public Spike(int tickIndex, long totalNanos, long baselineNanos,
                     TickInnerProfile.Snapshot innerSnapshot) {
            this.tickIndex = tickIndex;
            this.totalNanos = totalNanos;
            this.baselineNanos = baselineNanos;
            this.innerSnapshot = innerSnapshot;
        }
        public double ratio() {
            return baselineNanos > 0L ? (double) totalNanos / baselineNanos : 0.0;
        }
    }

    /** Latched copy of the inner profile at spike time. Cleared once consumed via {@link #consumeSpike()}. */
    private TickInnerProfile.Snapshot spikeInnerSnapshot;
}
