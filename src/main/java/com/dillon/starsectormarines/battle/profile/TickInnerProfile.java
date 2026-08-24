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
 *   <li><b>Behavior buckets</b> — aggregate worker time spent inside
 *       {@code updateUnit}'s dispatch. Per-unit durations are summed across
 *       parallel workers, so this value can exceed UPDATE_UNITS or whole-tick
 *       wall time. Tells us "is the spike coming from
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
 * don't otherwise carry a {@link com.dillon.starsectormarines.battle.sim.BattleSimulation}
 * reference and shouldn't have to. The thread-local slot keeps parallel unit
 * workers isolated until their counters are merged after dispatch.
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
        BEHAVIOR_SWARM_PRESSURE,
        // ---- Per-primitive buckets — heavy ops counted wherever they fire. ----
        PATHFIND,
        SWARM_PATHFIND,
        SHARED_PATH_FIELD_BUILD,
        SHARED_PATH_FIELD_EXTRACT,
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
    private static final ThreadLocal<Boolean> AUTO_CREATED = new ThreadLocal<>();

    public static TickInnerProfile current() {
        TickInnerProfile p = CURRENT.get();
        if (p == null) {
            p = new TickInnerProfile();
            CURRENT.set(p);
            AUTO_CREATED.set(true);
            ALL_INSTANCES.add(p);
        }
        return p;
    }

    public static void setCurrent(TickInnerProfile p) {
        releaseCurrentThread();
        if (p != null) CURRENT.set(p);
    }

    /**
     * Removes auto-created scratch owned by the current worker. Called from
     * the battle worker's termination hook so closed simulations do not leave
     * permanent entries in the process-wide merge/reset sweep.
     */
    public static void releaseCurrentThread() {
        TickInnerProfile current = CURRENT.get();
        if (current != null && Boolean.TRUE.equals(AUTO_CREATED.get())) {
            ALL_INSTANCES.remove(current);
        }
        CURRENT.remove();
        AUTO_CREATED.remove();
    }

    static int trackedWorkerCount() {
        return ALL_INSTANCES.size();
    }

    /**
     * Clears every auto-created worker scratch profile before a new simulation
     * tick starts. This prevents out-of-band/test calls that used
     * {@link #current()} from leaking counters into the next dispatch merge.
     */
    public static void resetAllWorkers() {
        for (TickInnerProfile profile : ALL_INSTANCES) profile.reset();
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
    private long[] pathfindRequests = new long[0];
    private int pathfindRequestCount;
    private int occupancyPathfindRequestCount;
    private Bucket activeBehavior;

    /** Zeros all counters. Call once per tick. */
    public void reset() {
        Arrays.fill(nanos, 0L);
        Arrays.fill(counts, 0);
        pathfindRequestCount = 0;
        occupancyPathfindRequestCount = 0;
        activeBehavior = null;
    }

    /**
     * Marks the behavior currently executing on this profile's thread. Heavy
     * primitive recordings can use this scope to retain caller attribution
     * without passing profiler metadata through navigation APIs.
     */
    public void enterBehavior(Bucket behaviorBucket) {
        activeBehavior = behaviorBucket;
    }

    /** Clears the caller-attribution scope established by {@link #enterBehavior}. */
    public void exitBehavior() {
        activeBehavior = null;
    }

    /**
     * Adds {@code deltaNanos} to {@code bucket}'s nanos sum and increments its
     * count. Pathfinding executed inside swarm-pressure dispatch is also
     * recorded in {@link Bucket#SWARM_PATHFIND}; {@link Bucket#PATHFIND}
     * remains the all-callers aggregate.
     */
    public void record(Bucket bucket, long deltaNanos) {
        add(bucket, deltaNanos);
        if (bucket == Bucket.PATHFIND
                && activeBehavior == Bucket.BEHAVIOR_SWARM_PRESSURE) {
            add(Bucket.SWARM_PATHFIND, deltaNanos);
        }
    }

    private void add(Bucket bucket, long deltaNanos) {
        int idx = bucket.ordinal();
        nanos[idx] += deltaNanos;
        counts[idx]++;
    }

    /**
     * Retains compact request keys only for opt-in fixture profiling. Ordinary
     * game runs never call this seam, so destination-clustering evidence does
     * not add allocation or hashing to production pathfinding.
     */
    public void recordPathfindRequest(int startX, int startY,
                                      int goalX, int goalY,
                                      boolean usesOccupancy) {
        ensurePathfindRequestCapacity(pathfindRequestCount + 1);
        pathfindRequests[pathfindRequestCount++] = packRequest(
                startX, startY, goalX, goalY);
        if (usesOccupancy) occupancyPathfindRequestCount++;
    }

    private void ensurePathfindRequestCapacity(int required) {
        if (pathfindRequests.length >= required) return;
        pathfindRequests = Arrays.copyOf(pathfindRequests,
                Math.max(required, Math.max(64, pathfindRequests.length * 2)));
    }

    private static long packRequest(int startX, int startY,
                                    int goalX, int goalY) {
        return ((long) (startX & 0xFFFF) << 48)
                | ((long) (startY & 0xFFFF) << 32)
                | ((long) (goalX & 0xFFFF) << 16)
                | (goalY & 0xFFFFL);
    }

    /** Per-bucket sum of another profile's nanos + counts. Used by {@link #mergeAllInto(TickInnerProfile)} to fold per-worker recordings into the sim's canonical instance after a parallel dispatch phase. */
    public void addFrom(TickInnerProfile other) {
        for (int i = 0; i < nanos.length; i++) {
            nanos[i] += other.nanos[i];
            counts[i] += other.counts[i];
        }
        ensurePathfindRequestCapacity(
                pathfindRequestCount + other.pathfindRequestCount);
        System.arraycopy(other.pathfindRequests, 0, pathfindRequests,
                pathfindRequestCount, other.pathfindRequestCount);
        pathfindRequestCount += other.pathfindRequestCount;
        occupancyPathfindRequestCount += other.occupancyPathfindRequestCount;
    }

    public long nanosOf(Bucket b)  { return nanos[b.ordinal()]; }
    public int countOf(Bucket b)   { return counts[b.ordinal()]; }

    public int pathfindRequestCount() { return pathfindRequestCount; }

    public int occupancyPathfindRequestCount() {
        return occupancyPathfindRequestCount;
    }

    /** Exact distinct start+goal pairs requested in this tick. */
    public int uniquePathfindRequestCount() {
        int unique = 0;
        for (int i = 0; i < pathfindRequestCount; i++) {
            long request = pathfindRequests[i];
            boolean seen = false;
            for (int j = 0; j < i; j++) {
                if (pathfindRequests[j] == request) {
                    seen = true;
                    break;
                }
            }
            if (!seen) unique++;
        }
        return unique;
    }

    /** Distinct destination cells requested in this tick. */
    public int uniquePathfindGoalCount() {
        int unique = 0;
        for (int i = 0; i < pathfindRequestCount; i++) {
            int goal = (int) pathfindRequests[i];
            boolean seen = false;
            for (int j = 0; j < i; j++) {
                if ((int) pathfindRequests[j] == goal) {
                    seen = true;
                    break;
                }
            }
            if (!seen) unique++;
        }
        return unique;
    }

    /** Largest number of requests sharing one destination in this tick. */
    public int maximumPathfindGoalFanIn() {
        int maximum = 0;
        for (int i = 0; i < pathfindRequestCount; i++) {
            int goal = (int) pathfindRequests[i];
            int count = 0;
            for (int j = 0; j < pathfindRequestCount; j++) {
                if ((int) pathfindRequests[j] == goal) count++;
            }
            maximum = Math.max(maximum, count);
        }
        return maximum;
    }

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
