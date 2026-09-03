package com.dillon.starsectormarines.battle.profile;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-tick scratch profiler measuring sub-step cost inside one
 * {@code BattleSimulation.tick()} call. Three lenses, all filled by the same
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
 *   <li><b>Commander buckets</b> — main-thread wall time for pulse stages.
 *       Influence refresh buckets are cross-cutting: they may run inside
 *       commander frame freeze or tactical GOAP reads, and overlap whichever
 *       enclosing phase triggered them.</li>
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
 * overhead per tick, well under 1% of the steady-state 4-7ms tick budget;
 * commander records occur only on their slow cadences.
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
        // ---- Commander pulse stages — synchronous main-thread wall time. ----
        COMMANDER_PULSE,
        COMMANDER_SYNC,
        COMMANDER_TOPOLOGY_LOOKUP,
        COMMANDER_TOPOLOGY_REBUILD,
        COMMANDER_FRAME,
        COMMANDER_PLAN,
        COMMANDER_COMMIT,
        INFLUENCE_TOPOLOGY_LOOKUP,
        INFLUENCE_TOPOLOGY_REBUILD,
        INFLUENCE_SOURCES,
        INFLUENCE_PROPAGATE,
        // ---- Per-primitive buckets — heavy ops counted wherever they fire. ----
        PATHFIND,
        SWARM_PATHFIND,
        SHARED_PATH_FIELD_BUILD,
        SHARED_PATH_FIELD_EXTRACT,
        TARGET_PICK,
        FIRING_POSITION,
        FALLBACK_POSITION,
        /**
         * One bucket per entry of the infantry reflex chain, named after the
         * reflex ({@code REFLEX_} + {@code Reflex.name()}), plus
         * {@link #REFLEX_OTHER} for a reflex no bucket is named for. Recorded
         * by {@code ReflexChain.run} for every reflex consulted, fired or
         * not, so the chain's cost is attributable per link: a tick profile
         * that read 3 ms per combatant with pathfinding accounting for a
         * seventh of it could not say where the rest went.
         */
        REFLEX_COMMITTED_AIM,
        REFLEX_COOLDOWNS,
        REFLEX_FRIENDLY_CHARGE,
        REFLEX_KNOWN_GRENADE,
        REFLEX_REJOIN,
        REFLEX_OPPORTUNITY_SPECIAL,
        REFLEX_HARDENED_OPPORTUNITY,
        REFLEX_ONSET_SCREEN,
        REFLEX_BROKEN_FIRE_TEAM,
        REFLEX_LANE_SIDESTEP,
        REFLEX_OTHER,
        /** The assigned GOAP step's {@code execute}; per action class under {@link #actions()}. */
        ACTION_EXECUTE,
        /** {@code InfantryUnitPrep.tryOpportunityPrimary}, whichever site called it. */
        OPPORTUNITY_PRIMARY;

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

    private static final String REFLEX_BUCKET_PREFIX = "REFLEX_";
    private static final Map<String, Bucket> REFLEX_BUCKETS = new HashMap<>();
    static {
        for (Bucket bucket : Bucket.VALUES) {
            if (bucket.name().startsWith(REFLEX_BUCKET_PREFIX)) {
                REFLEX_BUCKETS.put(bucket.name().substring(REFLEX_BUCKET_PREFIX.length()), bucket);
            }
        }
    }

    /** The bucket a reflex of this {@code Reflex.name()} records into; {@link Bucket#REFLEX_OTHER} for one without its own. */
    public static Bucket reflexBucket(String reflexName) {
        Bucket bucket = REFLEX_BUCKETS.get(reflexName);
        return bucket != null ? bucket : Bucket.REFLEX_OTHER;
    }
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

    /** Current tick-owned profile, or {@code null} outside a simulation tick. */
    public static TickInnerProfile currentIfBound() {
        return CURRENT.get();
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
    /** Per action-class {@code {nanos, count}} behind {@link Bucket#ACTION_EXECUTE}; keyed by simple class name. */
    private final Map<String, long[]> actions = new HashMap<>();

    /** Zeros all counters. Call once per tick. */
    public void reset() {
        Arrays.fill(nanos, 0L);
        Arrays.fill(counts, 0);
        pathfindRequestCount = 0;
        occupancyPathfindRequestCount = 0;
        activeBehavior = null;
        actions.clear();
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

    /**
     * Records one execution of the assigned GOAP step: into
     * {@link Bucket#ACTION_EXECUTE} and under {@code actionName} so a profile
     * can say which action a behavior's time went to.
     */
    public void recordAction(String actionName, long deltaNanos) {
        add(Bucket.ACTION_EXECUTE, deltaNanos);
        long[] sample = actions.get(actionName);
        if (sample == null) {
            sample = new long[2];
            actions.put(actionName, sample);
        }
        sample[0] += deltaNanos;
        sample[1]++;
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
        for (Map.Entry<String, long[]> entry : other.actions.entrySet()) {
            long[] sample = actions.get(entry.getKey());
            if (sample == null) {
                sample = new long[2];
                actions.put(entry.getKey(), sample);
            }
            sample[0] += entry.getValue()[0];
            sample[1] += entry.getValue()[1];
        }
    }

    public long nanosOf(Bucket b)  { return nanos[b.ordinal()]; }
    public int countOf(Bucket b)   { return counts[b.ordinal()]; }

    /** Per action-class {@code {nanos, count}} recorded through {@link #recordAction}; a copy. */
    public Map<String, long[]> actions() { return copyActions(actions); }

    private static Map<String, long[]> copyActions(Map<String, long[]> source) {
        Map<String, long[]> copy = new HashMap<>(source.size() * 2);
        for (Map.Entry<String, long[]> entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().clone());
        }
        return copy;
    }

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
        return new Snapshot(nanos.clone(), counts.clone(), copyActions(actions));
    }

    /** Immutable frozen bucket state — what spike dumps carry forward past the next tick's reset. */
    public static final class Snapshot {
        public final long[] nanos;
        public final int[] counts;
        /** Per action-class {@code {nanos, count}}, as {@link TickInnerProfile#actions()}. */
        public final Map<String, long[]> actions;
        public Snapshot(long[] nanos, int[] counts) {
            this(nanos, counts, Collections.emptyMap());
        }
        public Snapshot(long[] nanos, int[] counts, Map<String, long[]> actions) {
            this.nanos = nanos;
            this.counts = counts;
            this.actions = actions;
        }
        public long nanosOf(Bucket b) { return nanos[b.ordinal()]; }
        public int countOf(Bucket b)  { return counts[b.ordinal()]; }
    }
}
