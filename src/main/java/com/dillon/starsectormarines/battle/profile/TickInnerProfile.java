package com.dillon.starsectormarines.battle.profile;

import java.util.ArrayList;
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
 *   <li><b>Commander and GOAP buckets</b> — main-thread wall time for stages.
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
 * <p>Ordinary bucket recording is array-based; flat A* searches additionally
 * update a fixed-size slowest-search sample. Frozen copies allocate only for
 * spike snapshots or manual dumps, not once per search.
 */
public final class TickInnerProfile {

    /** Bounded per-tick diagnostic sample, ranked by individual search duration. */
    public static final int SLOW_PATH_SEARCH_LIMIT = 8;

    public record PathSearch(long nanos, int startX, int startY,
                             int goalX, int goalY, boolean usesOccupancy,
                             boolean found, int pathCells, int expandedNodes,
                             long memberId, int squadId, String action,
                             String routeReason, int goalOccupancy,
                             String fallbackReason) {
        public PathSearch(long nanos, int startX, int startY,
                          int goalX, int goalY, boolean usesOccupancy,
                          boolean found, int pathCells, int expandedNodes,
                          long memberId, int squadId, String action,
                          String routeReason) {
            this(nanos, startX, startY, goalX, goalY, usesOccupancy,
                    found, pathCells, expandedNodes, memberId, squadId,
                    action, routeReason, -1, "");
        }
        public PathSearch(long nanos, int startX, int startY,
                          int goalX, int goalY, boolean usesOccupancy,
                          boolean found, int pathCells, int expandedNodes) {
            this(nanos, startX, startY, goalX, goalY, usesOccupancy,
                    found, pathCells, expandedNodes, 0L, -1, "", "");
        }
    }

    private static final class MutablePathSearch {
        long nanos;
        int startX, startY, goalX, goalY, pathCells, expandedNodes;
        boolean usesOccupancy, found;
        long memberId;
        int squadId;
        String action, routeReason;
        int goalOccupancy;
        String fallbackReason;

        void set(long nanos, int startX, int startY, int goalX, int goalY,
                 boolean usesOccupancy, int pathCells, int expandedNodes,
                 long memberId, int squadId, String action, String routeReason,
                 int goalOccupancy, String fallbackReason) {
            this.nanos = nanos;
            this.startX = startX;
            this.startY = startY;
            this.goalX = goalX;
            this.goalY = goalY;
            this.usesOccupancy = usesOccupancy;
            this.found = pathCells > 0;
            this.pathCells = pathCells;
            this.expandedNodes = expandedNodes;
            this.memberId = memberId;
            this.squadId = squadId;
            this.action = action;
            this.routeReason = routeReason;
            this.goalOccupancy = goalOccupancy;
            this.fallbackReason = fallbackReason;
        }

        PathSearch freeze() {
            return new PathSearch(nanos, startX, startY, goalX, goalY,
                    usesOccupancy, found, pathCells, expandedNodes,
                    memberId, squadId, action, routeReason,
                    goalOccupancy, fallbackReason);
        }
    }

    public enum Bucket {
        SQUAD_ROUTE_BUILD_NEW,
        SQUAD_ROUTE_BUILD_GOAL,
        SQUAD_ROUTE_BUILD_TOPOLOGY,
        SQUAD_ROUTE_BUILD_COVERAGE,
        SQUAD_ROUTE_BUILD_COST,
        SQUAD_ROUTE_COST_CHECK,
        SQUAD_ROUTE_COST_REUSE,
        SQUAD_ROUTE_SEED_SEARCH,
        SQUAD_ROUTE_SEED_EXPANDED,
        SQUAD_ROUTE_SEED_PATH_CELL,
        SQUAD_ROUTE_UNCOVERED_FALLBACK,
        SQUAD_ROUTE_UNCOVERED_EXPANDED,
        SQUAD_TRAFFIC_PREPARE,
        SQUAD_TRAFFIC_MOVE,
        SQUAD_TRAFFIC_RETURN,
        SQUAD_TRAFFIC_SQUAD,
        SQUAD_TRAFFIC_CANDIDATE,
        // ---- Serial squad alert stages; disjoint within SQUAD_ALERT. ----
        /** Entry-point noise mailbox drain, copy, and stable sort. */
        ALERT_NOISE_DRAIN,
        /** Service lookup, transient reset, and belief expiry/decay. */
        ALERT_BELIEF_RESET,
        /** Member aggregates, spatial awareness gathering, LOS, and observations. */
        ALERT_AWARENESS,
        /** Per-squad noise detection and audible/contact observation. */
        ALERT_NOISE,
        /** Shot endpoint gathering, LOS, and incoming-fire publication. */
        ALERT_INCOMING_FIRE,
        /** Belief snapshots, centroids, alert transitions, and target clearing. */
        ALERT_FINALIZE,
        /** Count-only visited gathered candidates, including subsequently rejected entries. */
        ALERT_AWARENESS_CANDIDATE,
        /** Count-only actual canSeePair calls, not cache misses or ray steps. */
        ALERT_AWARENESS_LOS,
        /** Count-only squad/event pairs before faction filtering, and successful detections. */
        ALERT_NOISE_CONSIDERED,
        ALERT_NOISE_DETECTED,
        /** Count-only gathered endpoint candidates and actual grid LOS calls. */
        ALERT_INCOMING_CANDIDATE,
        ALERT_INCOMING_LOS,
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
        /** Disjoint freeze stages nested within COMMANDER_TOPOLOGY_REBUILD. */
        COMMANDER_TOPOLOGY_GRID_COPY,
        COMMANDER_TOPOLOGY_ZONE_COPY,
        COMMANDER_TOPOLOGY_CELL_COMPONENTS,
        COMMANDER_TOPOLOGY_ZONE_COMPONENTS,
        COMMANDER_TOPOLOGY_PUBLICATION,
        /** Opt-in current-thread CPU, overlapping all freeze wall stages. */
        COMMANDER_TOPOLOGY_CPU,
        /** Count-only input map area, copied zone-cell entries and zones. */
        COMMANDER_TOPOLOGY_MAP_CELLS,
        COMMANDER_TOPOLOGY_ZONE_CELLS,
        COMMANDER_TOPOLOGY_ZONES,
        COMMANDER_FRAME,
        COMMANDER_FRAME_ASSIGNMENTS,
        COMMANDER_FRAME_SQUADS,
        COMMANDER_FRAME_INFLUENCE,
        COMMANDER_FRAME_FACTS,
        /** Optional host CPU for the complete frame stage, not additive to its wall time. */
        COMMANDER_FRAME_CPU,
        COMMANDER_PLAN,
        COMMANDER_COMMIT,
        INFLUENCE_TOPOLOGY_LOOKUP,
        INFLUENCE_TOPOLOGY_REBUILD,
        INFLUENCE_SOURCES,
        INFLUENCE_PROPAGATE,
        // ---- GOAP phase stages — synchronous main-thread wall time. ----
        GOAP_SQUAD_REPLAN,
        GOAP_ROUTE_COLLECTION,
        GOAP_ROUTE_PREPARATION,
        /** Capture-post selection, nested inside custom squad planning. */
        HOLD_POSITION,
        /** Count-only inspected cells and exceptional nonlocal fallback calls. */
        HOLD_POSITION_CELL,
        HOLD_POSITION_FALLBACK,
        /** One bounded minimum-step flood shared by a flank candidate search. */
        FLANK_STEP_FIELD,
        /** Count-only flood expansions and candidate A* proofs avoided. */
        FLANK_STEP_EXPANDED,
        FLANK_STEP_REJECT,
        /** Count-only selections, including both plan-time and worker-side callers. */
        FLANK_SELECTION,
        /** Count-only total A* plus step-gate expansions, and selections consuming their allowance. */
        FLANK_SELECTION_EXPANDED,
        FLANK_SELECTION_LIMIT,
        /** Allowance-consuming selections that returned the origin refusal rather than an incumbent. */
        FLANK_SELECTION_LIMIT_REFUSAL,
        /** Count-only retained/fresh ReinforceContact waypoint decisions. */
        FLANK_PLAN_REUSE,
        FLANK_PLAN_SELECT,
        // Nested FrontageDefense relevance stages; selection still owns the total.
        FRONTAGE_SCOPE,
        FRONTAGE_CLAIMS,
        FRONTAGE_ZONES,
        FRONTAGE_BREACH,
        FRONTAGE_APERTURES,
        FRONTAGE_THREAT,
        FRONTAGE_POSTS,
        // ---- Per-primitive buckets — heavy ops counted wherever they fire. ----
        PATHFIND,
        SWARM_PATHFIND,
        SHARED_PATH_FIELD_BUILD,
        SHARED_PATH_FIELD_EXTRACT,
        SQUAD_PATH_FIELD_BUILD,
        // Nested stages of the serial squad-field build, for rare single-build spikes.
        SQUAD_PATH_FIELD_SEED,
        SQUAD_PATH_FIELD_CORRIDOR,
        SQUAD_PATH_FIELD_REVERSE,
        /** Storage-only conversion of an already proved singleton seed; no reverse expansion. */
        SQUAD_PATH_FIELD_DIRECT,
        SQUAD_PATH_FIELD_EXTRACT,
        SQUAD_PATH_FIELD_FALLBACK,
        /** Count-only admission work: requests are per-tick observations, not unique squads. */
        SQUAD_ROUTE_PENDING_REQUEST,
        /** Member callbacks that defer travel without a synchronous fallback. */
        SQUAD_ROUTE_PENDING_CALL,
        /** Work units consumed this tick; never cumulative request work. */
        SQUAD_ROUTE_WORK,
        /** Work slices yielding unfinished; not a failure or unreachable result. */
        SQUAD_ROUTE_YIELD,
        /** Requests reaching their lifetime work ceiling. */
        SQUAD_ROUTE_LIMIT,
        /** Pending route requests retired before completion. */
        SQUAD_ROUTE_CANCEL,
        /** Build attempts admitted this tick, including failed builds. */
        SQUAD_ROUTE_ADMITTED,
        /** Previously pending exact intents that now have a successful field; not motion. */
        SQUAD_ROUTE_RESUMED,
        CONVOY_CLEARANCE_BUILD,
        CONVOY_CLEARANCE_CATCHUP,
        CONVOY_COMPONENT_BUILD,
        CONVOY_COMPONENT_CATCHUP,
        CONVOY_TERRAIN_COST_BUILD,
        CONVOY_PROGRESSIVE_SNAPSHOT,
        /** New proof admissions, including admission-time refusal. */
        CONVOY_PROOF_ADMITTED,
        /** Queue/prepared observations per advance; sums are request-ticks. */
        CONVOY_PROOF_QUEUED,
        CONVOY_PROOF_PREPARED,
        /** Oldest unconsumed request age per advance; use maximum, not sum. */
        CONVOY_PROOF_OLDEST_PENDING_AGE,
        CONVOY_PROOF_READY,
        CONVOY_PROOF_FAILED,
        CONVOY_PROOF_TIMED_OUT,
        CONVOY_ROUTE_PROOF_STEP,
        // Ground-system stages are nested wall times, not additive to the phase.
        GROUND_PENDING_ORDERS,
        GROUND_ORDER_EXECUTION,
        GROUND_DELIVERY_MOTION,
        GROUND_DEBOARD,
        GROUND_TURRETS,
        VEHICLE_LOCAL_PLAN,
        VEHICLE_LOCAL_HEURISTIC,
        VEHICLE_LOCAL_LATTICE,
        VEHICLE_DOCKING_PROBE,
        VEHICLE_TURNAROUND_PROBE,
        /** Entire controller recovery call: endpoint snapping, proof, and route publication. */
        VEHICLE_RECOVERY_SEARCH,
        VEHICLE_RECOVERY_SETUP,
        VEHICLE_RECOVERY_PENDING,
        VEHICLE_RECOVERY_CANCEL,
        VEHICLE_RECOVERY_CLEARANCE_CELL,
        VEHICLE_RECOVERY_COST_CELL,
        // Count-only work; heuristic storage can exceed its bounded flood.
        VEHICLE_HEURISTIC_STORAGE_CELL,
        VEHICLE_LOCAL_EXPANDED,
        VEHICLE_LOCAL_NO_TRAJECTORY,
        /** Padded start footprint invalid; counted once per local search in both controls. */
        VEHICLE_LOCAL_INVALID_START,
        /** Physical chassis is already invalid (subset of INVALID_START). */
        VEHICLE_LOCAL_INVALID_CHASSIS_START,
        /** Physical chassis clears; only the planner's tracking pad is invalid. */
        VEHICLE_LOCAL_PADDING_ONLY_START,
        /** Padded start extends outside map bounds; independent of chassis/padding partition. */
        VEHICLE_LOCAL_START_OUT_OF_BOUNDS,
        /** Padded start is inside bounds but fails terrain or reciprocal closed-edge checks. */
        VEHICLE_LOCAL_START_TERRAIN,
        VEHICLE_RECOVERY_EXPANDED,
        VEHICLE_RECOVERY_ATTEMPT,
        /** A prior failed recovery remains applicable, avoiding another identical search. */
        VEHICLE_RECOVERY_FAILED_REUSE,
        /** A fresh recovery search returned no route. */
        VEHICLE_RECOVERY_FAILED_RESULT,
        TARGET_PICK,
        RALLY_REQUEST_PREPARE,
        RALLY_REQUEST_COMMIT,
        PROJECTILE_PUBLICATION_PREPARE,
        PROJECTILE_PUBLICATION_COMMIT,
        TARGET_SCAN_VISIT,
        TARGET_SCAN_RING,
        TARGET_SCAN_RAY,
        CLEAR_ZONE_TARGET_SELECT,
        CLEAR_ZONE_TARGET_VISIT,
        CLEAR_ZONE_TARGET_RAY,
        /** Count-only target decision and unsuccessful-search retry reuse. */
        CLEAR_ZONE_DECISION_REUSE,
        CLEAR_ZONE_NEGATIVE_REUSE,
        FIRING_POSITION,
        FIRING_RETAIN_VALIDATE,
        FIRING_RETAIN_HIT,
        FIRING_RETAIN_SEARCH,
        /** Count-only spread neighborhood query pairs, candidates, and gathered points. */
        FIRING_SPREAD_QUERY,
        FIRING_SPREAD_CANDIDATE,
        FIRING_SPREAD_VISIT,
        /** Experimental shared geometry builds; time is nested within FIRING_POSITION. */
        FIRING_POOL_BUILD,
        FIRING_POOL_HIT,
        FIRING_POOL_DEFERRED,
        FIRING_POOL_ASSIGN,
        /** Count-only work counters, not timed stages. */
        FIRING_POOL_CELL,
        FIRING_POOL_RAY,
        FIRING_POOL_VALIDATION,
        FIRING_POOL_NEGATIVE,
        FIRING_POOL_REFRESH_COLD,
        FIRING_POOL_REFRESH_TTL,
        FIRING_POOL_REFRESH_TARGET,
        FIRING_POOL_REFRESH_SQUAD,
        FIRING_POOL_REFRESH_EPOCH,
        FIRING_POOL_REFRESH_TOPOLOGY,
        FIRING_POOL_REFRESH_KEY,
        FIRING_INDIVIDUAL_CELL,
        FIRING_INDIVIDUAL_RAY,
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
        /** Known path-only hazard: consume the tick without replacing the mission route. */
        GRENADE_PATH_HOLD,
        /** Local bounded BFS wall time and call count; not a flat A* search. */
        GRENADE_ESCAPE_SEARCH,
        GRENADE_ESCAPE_EXPANDED,
        GRENADE_ESCAPE_REUSE,
        GRENADE_ESCAPE_NO_ROUTE,
        /** Optional quiet guard travel only; search is elapsed, remaining buckets count work/decisions. */
        GUARD_PATROL_SEARCH,
        GUARD_PATROL_EXPANDED,
        GUARD_PATROL_REFUSAL,
        GUARD_PATROL_BACKOFF,
        /** Nested execution wall time by effective mech doctrine; excludes shared planning and reflexes. */
        MECH_DOCTRINE_ASSAULT,
        MECH_DOCTRINE_ARMORED_SUPPORT,
        MECH_DOCTRINE_LR_SUPPORT,
        MECH_DOCTRINE_BALANCED,
        REFLEX_REJOIN,
        REFLEX_OPPORTUNITY_SPECIAL,
        REFLEX_HARDENED_OPPORTUNITY,
        REFLEX_ONSET_SCREEN,
        REFLEX_BROKEN_FIRE_TEAM,
        REFLEX_LANE_SIDESTEP,
        REFLEX_OTHER,
        /** The assigned GOAP step's {@code execute}; per profiling identity under {@link #actions()}. */
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
    private long squadRouteCorridorCells;
    private long squadRouteSettledCells;
    private long pathfindExpandedNodes;
    private long convoyClearanceEvaluations;
    private long convoyCostEvaluations;
    private long convoyExpandedNodes;
    private long convoySearchesStarted;
    private final MutablePathSearch[] slowPathSearches = new MutablePathSearch[SLOW_PATH_SEARCH_LIMIT];
    private int slowPathSearchCount;
    private final long[][] squadBuildValues = new long[8][10];
    private final String[] squadBuildReasons = new String[8];
    private int squadBuildCount;
    private final long[][] squadWorkValues = new long[8][14];
    private final String[] squadWorkActions = new String[8];
    private final String[] squadWorkStages = new String[8];
    private final String[] squadWorkStatuses = new String[8];
    private int squadWorkCount;
    private Bucket activeBehavior;
    private long activeMemberId;
    private int activeSquadId = -1;
    private String activeAction = "";
    private String lastUnitAction = "";

    /** Optional callback diagnostics; empty means no GOAP action was entered. */
    public void clearUnitAction() { lastUnitAction = ""; }
    public String lastUnitAction() { return lastUnitAction; }
    private String activeRouteReason = "";
    /** Per profiling identity {@code {nanos, count}} behind {@link Bucket#ACTION_EXECUTE}. */
    private final Map<String, long[]> actions = new HashMap<>();

    public TickInnerProfile() {
        for (int i = 0; i < slowPathSearches.length; i++) {
            slowPathSearches[i] = new MutablePathSearch();
        }
    }

    /** Zeros all counters. Call once per tick. */
    public void reset() {
        Arrays.fill(nanos, 0L);
        Arrays.fill(counts, 0);
        pathfindRequestCount = 0;
        occupancyPathfindRequestCount = 0;
        squadRouteCorridorCells = 0L;
        squadRouteSettledCells = 0L;
        pathfindExpandedNodes = 0L;
        convoyClearanceEvaluations = 0L;
        convoyCostEvaluations = 0L;
        convoyExpandedNodes = 0L;
        convoySearchesStarted = 0L;
        slowPathSearchCount = 0;
        squadBuildCount = 0;
        Arrays.fill(squadBuildReasons, null);
        squadWorkCount = 0;
        Arrays.fill(squadWorkActions, null);
        Arrays.fill(squadWorkStages, null);
        Arrays.fill(squadWorkStatuses, null);
        activeBehavior = null;
        clearUnitAction();
        exitAction();
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
     * Attributes searches to one action execution without allocating a scope
     * object. Calls are not nested; the dispatcher must exit in a finally block.
     * Names are stable profiling identities supplied by the action, not derived
     * from a shared implementation class. A new action clears the route reason.
     */
    public void enterAction(long memberId, int squadId, String action) {
        activeMemberId = memberId;
        activeSquadId = squadId;
        activeAction = action != null ? action : "";
        lastUnitAction = activeAction;
        activeRouteReason = "";
    }

    /** Clears all action attribution, including a pending route reason. */
    public void exitAction() {
        activeMemberId = 0L;
        activeSquadId = -1;
        activeAction = "";
        activeRouteReason = "";
    }

    /** Labels subsequent searches in this action until replaced or the scope ends. */
    public void routeReason(String reason) {
        activeRouteReason = reason != null ? reason : "";
    }

    /** Current route label, for nested diagnostic scopes that restore their caller. */
    public String routeReason() { return activeRouteReason; }

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

    /** Aggregate work counts without a clock read or a method call per candidate. */
    public void recordCount(Bucket bucket, int count) {
        if (count < 0) throw new IllegalArgumentException("negative work count");
        counts[bucket.ordinal()] += count;
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

    /** One timed public A* search. Storage is fixed and reused across ticks. */
    public void recordPathSearch(long durationNanos,
                                 int startX, int startY, int goalX, int goalY,
                                 boolean usesOccupancy, int pathCells,
                                 int expandedNodes) {
        recordPathSearch(durationNanos, startX, startY, goalX, goalY,
                usesOccupancy, pathCells, expandedNodes, -1, "");
    }

    /** Actual goal reservation count and shared-field miss cause, when known. */
    public void recordPathSearch(long durationNanos,
                                 int startX, int startY, int goalX, int goalY,
                                 boolean usesOccupancy, int pathCells,
                                 int expandedNodes, int goalOccupancy,
                                 String fallbackReason) {
        pathfindExpandedNodes += expandedNodes;
        retainSlowPathSearch(durationNanos, startX, startY, goalX, goalY,
                usesOccupancy, pathCells, expandedNodes,
                activeMemberId, activeSquadId, activeAction, activeRouteReason,
                goalOccupancy, fallbackReason);
    }

    private void retainSlowPathSearch(long durationNanos,
                                      int startX, int startY, int goalX, int goalY,
                                      boolean usesOccupancy, int pathCells,
                                      int expandedNodes, long memberId,
                                      int squadId, String action, String routeReason,
                                      int goalOccupancy, String fallbackReason) {
        int index = 0;
        while (index < slowPathSearchCount
                && slowPathSearches[index].nanos >= durationNanos) index++;
        if (index == SLOW_PATH_SEARCH_LIMIT) return;
        int moveFrom = Math.min(slowPathSearchCount, SLOW_PATH_SEARCH_LIMIT - 1);
        MutablePathSearch slot = slowPathSearches[moveFrom];
        for (int i = moveFrom; i > index; i--) {
            slowPathSearches[i] = slowPathSearches[i - 1];
        }
        slowPathSearches[index] = slot;
        slot.set(durationNanos, startX, startY, goalX, goalY,
                usesOccupancy, pathCells, expandedNodes,
                memberId, squadId, action, routeReason,
                goalOccupancy, fallbackReason);
        if (slowPathSearchCount < SLOW_PATH_SEARCH_LIMIT) slowPathSearchCount++;
    }

    /** Aggregate build causes and seed work, with allocation-free top-eight retention. */
    public void recordSquadRouteBuild(long durationNanos, int squadId, String reason,
                                     int startCount, int maxStartGoalManhattan,
                                     int seedSearches, int seedExpanded, int seedPathCells,
                                     int unpaddedCells, int corridorCells, int settledCells) {
        Bucket cause = switch (reason) {
            case "NEW" -> Bucket.SQUAD_ROUTE_BUILD_NEW;
            case "GOAL" -> Bucket.SQUAD_ROUTE_BUILD_GOAL;
            case "TOPOLOGY" -> Bucket.SQUAD_ROUTE_BUILD_TOPOLOGY;
            case "COVERAGE" -> Bucket.SQUAD_ROUTE_BUILD_COVERAGE;
            case "COST" -> Bucket.SQUAD_ROUTE_BUILD_COST;
            default -> throw new IllegalArgumentException("Unknown squad route build reason: " + reason);
        };
        record(cause, durationNanos);
        recordCount(Bucket.SQUAD_ROUTE_SEED_SEARCH, seedSearches);
        recordCount(Bucket.SQUAD_ROUTE_SEED_EXPANDED, seedExpanded);
        recordCount(Bucket.SQUAD_ROUTE_SEED_PATH_CELL, seedPathCells);
        retainSquadRouteBuild(durationNanos, squadId, reason, startCount,
                maxStartGoalManhattan, seedSearches, seedExpanded, seedPathCells,
                unpaddedCells, corridorCells, settledCells);
    }

    private void retainSquadRouteBuild(long durationNanos, int squadId, String reason,
                                      int startCount, int maxStartGoalManhattan,
                                      int seedSearches, int seedExpanded, int seedPathCells,
                                      int unpaddedCells, int corridorCells, int settledCells) {
        int index = 0;
        while (index < squadBuildCount && squadBuildValues[index][0] >= durationNanos) index++;
        if (index == squadBuildValues.length) return;
        int last = Math.min(squadBuildCount, squadBuildValues.length - 1);
        long[] slot = squadBuildValues[last];
        for (int i = last; i > index; i--) {
            squadBuildValues[i] = squadBuildValues[i - 1];
            squadBuildReasons[i] = squadBuildReasons[i - 1];
        }
        squadBuildValues[index] = slot;
        squadBuildReasons[index] = reason;
        slot[0] = durationNanos;
        slot[1] = squadId;
        slot[2] = startCount;
        slot[3] = maxStartGoalManhattan;
        slot[4] = seedSearches;
        slot[5] = seedExpanded;
        slot[6] = seedPathCells;
        slot[7] = unpaddedCells;
        slot[8] = corridorCells;
        slot[9] = settledCells;
        if (squadBuildCount < squadBuildValues.length) squadBuildCount++;
    }

    /** NEW means no prior entry (including after a flush); COST also includes age refresh. */
    public record SquadRouteBuild(long nanos, int squadId, String reason,
                                  int startCount, int maxStartGoalManhattan,
                                  int seedSearches, int seedExpanded, int seedPathCells,
                                  int unpaddedCells, int corridorCells, int settledCells) {}

    public List<SquadRouteBuild> slowSquadRouteBuilds() {
        List<SquadRouteBuild> samples = new ArrayList<>(squadBuildCount);
        for (int i = 0; i < squadBuildCount; i++) {
            long[] v = squadBuildValues[i];
            samples.add(new SquadRouteBuild(v[0], (int) v[1], squadBuildReasons[i],
                    (int) v[2], (int) v[3], (int) v[4], (int) v[5], (int) v[6],
                    (int) v[7], (int) v[8], (int) v[9]));
        }
        return List.copyOf(samples);
    }

    /**
     * Retains the eight slowest work slices this tick, without allocating on the
     * recording path. Records samples only: the caller owns work/yield/limit
     * counters, so merging samples cannot double-count work. Expansion values
     * prefixed with slice describe this slice; lifetime work, age, and per-seed
     * cumulative counts describe the request at its end and are not additive.
     */
    public void recordSquadRouteWork(long durationNanos, int squadId, String action,
                                     int goalX, int goalY, String stage, String status,
                                     int workUnits, int seedExpanded, int reverseExpanded,
                                     long lifetimeWorkUnits, int ageTicks, int startCount,
                                     int maxStartGoalManhattan, int lastSeedStart,
                                     int lastSeedExpanded, int maxSeedExpanded) {
        int index = 0;
        while (index < squadWorkCount && squadWorkValues[index][0] >= durationNanos) index++;
        if (index == squadWorkValues.length) return;
        int last = Math.min(squadWorkCount, squadWorkValues.length - 1);
        long[] slot = squadWorkValues[last];
        for (int i = last; i > index; i--) {
            squadWorkValues[i] = squadWorkValues[i - 1];
            squadWorkActions[i] = squadWorkActions[i - 1];
            squadWorkStages[i] = squadWorkStages[i - 1];
            squadWorkStatuses[i] = squadWorkStatuses[i - 1];
        }
        squadWorkValues[index] = slot;
        squadWorkActions[index] = action;
        squadWorkStages[index] = stage;
        squadWorkStatuses[index] = status;
        slot[0] = durationNanos;
        slot[1] = squadId;
        slot[2] = goalX;
        slot[3] = goalY;
        slot[4] = workUnits;
        slot[5] = seedExpanded;
        slot[6] = reverseExpanded;
        slot[7] = lifetimeWorkUnits;
        slot[8] = ageTicks;
        slot[9] = startCount;
        slot[10] = maxStartGoalManhattan;
        slot[11] = lastSeedStart;
        slot[12] = lastSeedExpanded;
        slot[13] = maxSeedExpanded;
        if (squadWorkCount < squadWorkValues.length) squadWorkCount++;
    }

    /** Samples are not additive totals: a request may appear in several ticks. */
    public record SquadRouteWork(long nanos, int squadId, String action, int goalX, int goalY,
                                 String stage, String status, int sliceWorkUnits,
                                 int sliceSeedExpanded, int sliceReverseExpanded,
                                 long lifetimeWorkUnits, int ageTicks, int startCount,
                                 int maxStartGoalManhattan, int lastSeedStart,
                                 int lastSeedExpanded, int maxSeedExpanded) {}

    public List<SquadRouteWork> slowSquadRouteWork() {
        List<SquadRouteWork> samples = new ArrayList<>(squadWorkCount);
        for (int i = 0; i < squadWorkCount; i++) {
            long[] v = squadWorkValues[i];
            samples.add(new SquadRouteWork(v[0], (int) v[1], squadWorkActions[i],
                    (int) v[2], (int) v[3], squadWorkStages[i], squadWorkStatuses[i],
                    (int) v[4], (int) v[5], (int) v[6], v[7], (int) v[8],
                    (int) v[9], (int) v[10], (int) v[11], (int) v[12], (int) v[13]));
        }
        return List.copyOf(samples);
    }

    /** Cell-volume evidence for each compact squad field built this tick. */
    public void recordSquadRouteFieldShape(int corridorCells,
                                           int settledCells) {
        squadRouteCorridorCells += corridorCells;
        squadRouteSettledCells += settledCells;
    }

    /** Progressive convoy work performed in one proof slice, not full-map totals. */
    public void recordConvoyRouteWork(int clearanceCells, int costCells,
                                      int expandedNodes, int searchesStarted) {
        convoyClearanceEvaluations += clearanceCells;
        convoyCostEvaluations += costCells;
        convoyExpandedNodes += expandedNodes;
        convoySearchesStarted += searchesStarted;
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
        squadRouteCorridorCells += other.squadRouteCorridorCells;
        squadRouteSettledCells += other.squadRouteSettledCells;
        pathfindExpandedNodes += other.pathfindExpandedNodes;
        convoyClearanceEvaluations += other.convoyClearanceEvaluations;
        convoyCostEvaluations += other.convoyCostEvaluations;
        convoyExpandedNodes += other.convoyExpandedNodes;
        convoySearchesStarted += other.convoySearchesStarted;
        for (int i = 0; i < other.squadBuildCount; i++) {
            long[] v = other.squadBuildValues[i];
            retainSquadRouteBuild(v[0], (int) v[1], other.squadBuildReasons[i],
                    (int) v[2], (int) v[3], (int) v[4], (int) v[5], (int) v[6],
                    (int) v[7], (int) v[8], (int) v[9]);
        }
        for (int i = 0; i < other.squadWorkCount; i++) {
            long[] v = other.squadWorkValues[i];
            recordSquadRouteWork(v[0], (int) v[1], other.squadWorkActions[i],
                    (int) v[2], (int) v[3], other.squadWorkStages[i], other.squadWorkStatuses[i],
                    (int) v[4], (int) v[5], (int) v[6], v[7], (int) v[8],
                    (int) v[9], (int) v[10], (int) v[11], (int) v[12], (int) v[13]);
        }
        for (int i = 0; i < other.slowPathSearchCount; i++) {
            MutablePathSearch sample = other.slowPathSearches[i];
            retainSlowPathSearch(sample.nanos, sample.startX, sample.startY,
                    sample.goalX, sample.goalY, sample.usesOccupancy,
                    sample.pathCells, sample.expandedNodes, sample.memberId,
                    sample.squadId, sample.action, sample.routeReason,
                    sample.goalOccupancy, sample.fallbackReason);
        }
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

    /** Per profiling identity {@code {nanos, count}} recorded through {@link #recordAction}; a copy. */
    public Map<String, long[]> actions() { return copyActions(actions); }

    /** Adds this tick to a caller-owned evidence accumulator, reusing existing entries. */
    public void accumulateActionTotals(Map<String, long[]> totals) {
        for (Map.Entry<String, long[]> entry : actions.entrySet()) {
            long[] total = totals.get(entry.getKey());
            if (total == null) {
                total = new long[2];
                totals.put(entry.getKey(), total);
            }
            total[0] += entry.getValue()[0];
            total[1] += entry.getValue()[1];
        }
    }

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

    public long squadRouteCorridorCells() { return squadRouteCorridorCells; }
    public long squadRouteSettledCells() { return squadRouteSettledCells; }
    public long pathfindExpandedNodes() { return pathfindExpandedNodes; }
    public long convoyClearanceEvaluations() { return convoyClearanceEvaluations; }
    public long convoyCostEvaluations() { return convoyCostEvaluations; }
    public long convoyExpandedNodes() { return convoyExpandedNodes; }
    public long convoySearchesStarted() { return convoySearchesStarted; }

    /** Frozen samples for manual dumps; the spike latch freezes these at endTick. */
    public List<PathSearch> slowPathSearches() {
        List<PathSearch> result = new ArrayList<>(slowPathSearchCount);
        for (int i = 0; i < slowPathSearchCount; i++) {
            result.add(slowPathSearches[i].freeze());
        }
        return List.copyOf(result);
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
        return new Snapshot(nanos.clone(), counts.clone(), copyActions(actions),
                squadRouteCorridorCells, squadRouteSettledCells,
                pathfindExpandedNodes, slowPathSearches(),
                convoyClearanceEvaluations, convoyCostEvaluations,
                convoyExpandedNodes, convoySearchesStarted, slowSquadRouteBuilds(), slowSquadRouteWork());
    }

    /** Immutable frozen bucket state — what spike dumps carry forward past the next tick's reset. */
    public static final class Snapshot {
        public final long[] nanos;
        public final int[] counts;
        /** Per profiling identity {@code {nanos, count}}, as {@link TickInnerProfile#actions()}. */
        public final Map<String, long[]> actions;
        public final long squadRouteCorridorCells;
        public final long squadRouteSettledCells;
        public final long pathfindExpandedNodes;
        public final long convoyClearanceEvaluations;
        public final long convoyCostEvaluations;
        public final long convoyExpandedNodes;
        public final long convoySearchesStarted;
        public final List<PathSearch> slowPathSearches;
        public final List<SquadRouteBuild> slowSquadRouteBuilds;
        public final List<SquadRouteWork> slowSquadRouteWork;
        public Snapshot(long[] nanos, int[] counts) {
            this(nanos, counts, Collections.emptyMap(), 0L, 0L);
        }
        public Snapshot(long[] nanos, int[] counts, Map<String, long[]> actions) {
            this(nanos, counts, actions, 0L, 0L);
        }
        public Snapshot(long[] nanos, int[] counts, Map<String, long[]> actions,
                        long squadRouteCorridorCells,
                        long squadRouteSettledCells) {
            this(nanos, counts, actions, squadRouteCorridorCells,
                    squadRouteSettledCells, 0L, List.of(), 0L, 0L, 0L, 0L);
        }
        public Snapshot(long[] nanos, int[] counts, Map<String, long[]> actions,
                        long squadRouteCorridorCells,
                        long squadRouteSettledCells, long pathfindExpandedNodes,
                        List<PathSearch> slowPathSearches,
                        long convoyClearanceEvaluations,
                        long convoyCostEvaluations,
                        long convoyExpandedNodes,
                        long convoySearchesStarted) {
            this(nanos, counts, actions, squadRouteCorridorCells, squadRouteSettledCells,
                    pathfindExpandedNodes, slowPathSearches, convoyClearanceEvaluations,
                    convoyCostEvaluations, convoyExpandedNodes, convoySearchesStarted, List.of());
        }
        public Snapshot(long[] nanos, int[] counts, Map<String, long[]> actions,
                        long squadRouteCorridorCells, long squadRouteSettledCells,
                        long pathfindExpandedNodes, List<PathSearch> slowPathSearches,
                        long convoyClearanceEvaluations, long convoyCostEvaluations,
                        long convoyExpandedNodes, long convoySearchesStarted,
                        List<SquadRouteBuild> slowSquadRouteBuilds) {
            this(nanos, counts, actions, squadRouteCorridorCells, squadRouteSettledCells,
                    pathfindExpandedNodes, slowPathSearches, convoyClearanceEvaluations,
                    convoyCostEvaluations, convoyExpandedNodes, convoySearchesStarted,
                    slowSquadRouteBuilds, List.of());
        }
        public Snapshot(long[] nanos, int[] counts, Map<String, long[]> actions,
                        long squadRouteCorridorCells, long squadRouteSettledCells,
                        long pathfindExpandedNodes, List<PathSearch> slowPathSearches,
                        long convoyClearanceEvaluations, long convoyCostEvaluations,
                        long convoyExpandedNodes, long convoySearchesStarted,
                        List<SquadRouteBuild> slowSquadRouteBuilds,
                        List<SquadRouteWork> slowSquadRouteWork) {
            this.nanos = nanos;
            this.counts = counts;
            this.actions = actions;
            this.squadRouteCorridorCells = squadRouteCorridorCells;
            this.squadRouteSettledCells = squadRouteSettledCells;
            this.pathfindExpandedNodes = pathfindExpandedNodes;
            this.slowPathSearches = List.copyOf(slowPathSearches);
            this.slowSquadRouteBuilds = List.copyOf(slowSquadRouteBuilds);
            this.slowSquadRouteWork = List.copyOf(slowSquadRouteWork);
            this.convoyClearanceEvaluations = convoyClearanceEvaluations;
            this.convoyCostEvaluations = convoyCostEvaluations;
            this.convoyExpandedNodes = convoyExpandedNodes;
            this.convoySearchesStarted = convoySearchesStarted;
        }
        public long nanosOf(Bucket b) { return nanos[b.ordinal()]; }
        public int countOf(Bucket b)  { return counts[b.ordinal()]; }
    }
}
