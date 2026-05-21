package com.dillon.starsectormarines.battle.profile;

import java.util.Arrays;

/**
 * Per-tick scratch profiler measuring sub-step cost inside one
 * {@code BattleSimulation.tick()} call. Two lenses, both filled by the same
 * counters:
 *
 * <ul>
 *   <li><b>Behavior buckets</b> — per-class wall time spent inside
 *       {@code updateUnit}'s dispatch. Tells us "is the spike coming from
 *       infantry GOAP behaviors, turret behaviors, drone behaviors, or
 *       something else?"</li>
 *   <li><b>Primitive buckets</b> — heavy primitives (pathfind, target picking,
 *       firing-position scoring, fallback-position scoring) timed wherever
 *       they're called from. Overlaps with the behavior buckets — a pathfind
 *       fired from inside a GOAP infantry behavior counts toward both
 *       {@code BEHAVIOR_COMBATANT} and {@code PATHFIND}. Different lenses,
 *       different questions.</li>
 * </ul>
 *
 * <p>Reset at the top of every tick via {@link #reset()}. Snapshotted by
 * {@link TickProfile#endTick(int, TickInnerProfile)} whenever a spike fires;
 * the snapshot lives inside {@link TickProfile.Spike} so the dumper can
 * persist sub-step nanos for the spike tick even after the live counters
 * have been reset by the next tick.
 *
 * <p>Access pattern: callers reach the live profile through
 * {@link #current()}, which the sim sets at the top of each tick. Static
 * access (rather than passing through every signature) is justified by the
 * 6+ call sites in {@code GridPathfinder} / {@code TacticalScoring} that
 * don't otherwise carry a {@link com.dillon.starsectormarines.battle.BattleSimulation}
 * reference and shouldn't have to. The sim is single-threaded, so the
 * static slot races on nothing.
 *
 * <p>Cost: each {@link #record} call is one nanoTime delta plus a long+int
 * array increment — ~5ns. At ~5 record sites per unit × ~400 units = ~10µs
 * overhead per tick, well under 1% of the steady-state 4-7ms tick budget.
 */
public final class TickInnerProfile {

    public enum Bucket {
        // ---- Per-behavior buckets — what updateUnit's dispatch went into. ----
        BEHAVIOR_FALLBACK,
        BEHAVIOR_COMBATANT,
        BEHAVIOR_TURRET,
        BEHAVIOR_FLEE,
        BEHAVIOR_KIT_RETRIEVER,
        BEHAVIOR_STRUCTURE,
        BEHAVIOR_DRONE_HUB,
        BEHAVIOR_GOAP_DRONE,
        // ---- Per-primitive buckets — heavy ops counted wherever they fire. ----
        PATHFIND,
        TARGET_PICK,
        FIRING_POSITION,
        FALLBACK_POSITION;

        public static final Bucket[] VALUES = values();
    }

    /** Sim sets this at the top of {@code tick()} so the recording sites reach the live counters without a sim reference. Single-threaded — no synchronization needed. */
    private static TickInnerProfile current;

    public static TickInnerProfile current() { return current; }
    public static void setCurrent(TickInnerProfile p) { current = p; }

    private final long[] nanos = new long[Bucket.VALUES.length];
    private final int[] counts = new int[Bucket.VALUES.length];

    /** Zeros all counters. Call once per tick. */
    public void reset() {
        Arrays.fill(nanos, 0L);
        Arrays.fill(counts, 0);
    }

    /** Adds {@code deltaNanos} to {@code bucket}'s nanos sum and increments its count. */
    public void record(Bucket bucket, long deltaNanos) {
        int idx = bucket.ordinal();
        nanos[idx] += deltaNanos;
        counts[idx]++;
    }

    public long nanosOf(Bucket b)  { return nanos[b.ordinal()]; }
    public int countOf(Bucket b)   { return counts[b.ordinal()]; }

    /** Returns a frozen copy of the current bucket state. The caller owns the arrays — mutating them won't affect this profile or vice-versa. */
    public Snapshot snapshot() {
        return new Snapshot(nanos.clone(), counts.clone());
    }

    /** Immutable frozen bucket state — what spike dumps carry forward past the next tick's reset. */
    public static final class Snapshot {
        public final long[] nanos;
        public final int[] counts;
        public Snapshot(long[] nanos, int[] counts) {
            this.nanos = nanos;
            this.counts = counts;
        }
        public long nanosOf(Bucket b) { return nanos[b.ordinal()]; }
        public int countOf(Bucket b)  { return counts[b.ordinal()]; }
    }
}
