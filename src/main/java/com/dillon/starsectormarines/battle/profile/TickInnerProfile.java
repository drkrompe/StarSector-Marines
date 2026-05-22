package com.dillon.starsectormarines.battle.profile;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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

    /**
     * Per-thread current slot. The sim sets the main thread's slot at the top
     * of each tick to point at its canonical {@link TickInnerProfile}; worker
     * threads in the parallel UPDATE_UNITS dispatch auto-create their own
     * per-thread instances on first {@link #current()} access. After the
     * parallel section, {@link #mergeAllInto(TickInnerProfile)} sums every
     * auto-created worker profile into the canonical one and resets them, so
     * the dumper / panel reads the aggregate from the sim's instance.
     *
     * <p>{@link #ALL_INSTANCES} tracks only the auto-created worker profiles
     * (not the sim's canonical instance — that one is set via
     * {@link #setCurrent} which deliberately doesn't register). This keeps
     * the merge sweep from double-counting the destination.
     */
    private static final List<TickInnerProfile> ALL_INSTANCES = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<TickInnerProfile> CURRENT = new ThreadLocal<>();

    public static TickInnerProfile current() {
        TickInnerProfile p = CURRENT.get();
        if (p == null) {
            p = new TickInnerProfile();
            CURRENT.set(p);
            ALL_INSTANCES.add(p);
        }
        return p;
    }

    public static void setCurrent(TickInnerProfile p) {
        if (p == null) CURRENT.remove();
        else CURRENT.set(p);
    }

    /**
     * Sums every auto-created worker profile's per-bucket nanos and counts
     * into {@code dest}, then resets each worker profile so the next tick's
     * recordings accumulate fresh. Call at the end of the parallel UPDATE_UNITS
     * dispatch. The sim's canonical instance is the {@code dest} argument —
     * skipped in the loop because it was registered via {@link #setCurrent},
     * not via auto-init.
     */
    public static void mergeAllInto(TickInnerProfile dest) {
        for (TickInnerProfile src : ALL_INSTANCES) {
            if (src == dest) continue;
            dest.addFrom(src);
            src.reset();
        }
    }

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

    /** Per-bucket sum of another profile's nanos + counts. Used by {@link #mergeAllInto(TickInnerProfile)} to fold per-worker recordings into the sim's canonical instance after a parallel dispatch phase. */
    public void addFrom(TickInnerProfile other) {
        for (int i = 0; i < nanos.length; i++) {
            nanos[i] += other.nanos[i];
            counts[i] += other.counts[i];
        }
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
