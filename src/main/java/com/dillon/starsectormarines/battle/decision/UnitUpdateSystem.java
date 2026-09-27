package com.dillon.starsectormarines.battle.decision;
import com.dillon.starsectormarines.battle.infantry.CombatantBehavior;
import com.dillon.starsectormarines.battle.drone.DroneHubBehavior;
import com.dillon.starsectormarines.battle.infantry.KitRetrieverBehavior;
import com.dillon.starsectormarines.battle.turret.StructureBehavior;
import com.dillon.starsectormarines.battle.turret.TurretBehavior;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.drone.GoapDroneBehavior;
import com.dillon.starsectormarines.battle.evacuation.SwarmPressureBehavior;
import com.dillon.starsectormarines.battle.combat.DamageService;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.nav.LosCaches;
import com.dillon.starsectormarines.battle.nav.AsyncDefendTrackRoutes;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Timespan;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Adaptive per-unit dispatch — owns the {@code UPDATE_UNITS} phase that
 * routes each alive {@code Entity} to its role-specific {@link UnitBehavior}.
 * This is the entity for-loop: the hot path that ticks every combatant on
 * the battlefield.
 *
 * <h2>ECS / SoA seam</h2>
 * <p>This class is the load-bearing seam for the eventual move to SoA over
 * {@code Entity}. The current loop body — {@code behaviorFor(u.role).update(u, sim)}
 * — is the unit of work that, once promoted, becomes a per-System parallel
 * sweep across flat primitive arrays. When that lift happens, each per-role
 * behavior class becomes a System with explicit read/write field decls and
 * this dispatcher disappears in favor of System-shaped passes registered on
 * the sim. Until then, this is the single named for-loop a future ECS
 * refactor needs to find.
 *
 * <h2>Parallelism</h2>
 * <p>Pre-Phase-A this loop was serial and mutated shared sim state inline.
 * Phase A refactored every shared mutation (damage, occupancy, spawns, shots,
 * projectiles, detonations) into thread-safe queue / synchronized enqueue
 * paths, so workers can dispatch in parallel without corrupting sim state.
 * The {@link DamageService#enterParallel()} / {@link DamageService#exitParallel()}
 * bracket flips the queue-vs-inline gate on the damage / target-mutation /
 * occupancy services. Per-worker {@link TickInnerProfile} recordings
 * (PATHFIND / TARGET_PICK / FIRING_POSITION / behavior buckets) are merged
 * into the canonical sim profile at the end of the dispatch.
 *
 * <h2>Snapshot semantics</h2>
 * <p>{@code UnitRosterService.queueSpawn} defers drone-hub additions to the
 * APPLY_SPAWNS phase, so the registry's dense array is stable during this
 * dispatch — no CME risk on the parallel iterator. The
 * {@code (snapshot, liveCount)} pair captured at the top of {@link #tick}
 * locks in the dispatch view; {@code allocate()} grows the backing array
 * but APPLY_SPAWNS doesn't overlap with UPDATE_UNITS, so a same-tick growth
 * can't strand the snapshot mid-dispatch.
 *
 * <h2>Registry-driven dispatch</h2>
 * <p>Dispatch source is the {@link UnitRosterService} dense array. The legacy
 * {@code List<Entity>} is no longer iterated here. Every production death
 * path now releases from the registry: {@code DamageResolver} on damage
 * kills, and {@code HubDemolitionSystem} on the drone cascade. The
 * dispatch trusts the registry's notion of liveness — there's no
 * {@code .filter(Entity::isAlive)} fallback. If a new direct-hp-write death
 * path lands without registry release, dead units will pass through this
 * loop until the next tick's roster pass catches them.
 *
 * <h2>sim-as-context</h2>
 * <p>Behaviors still take {@link BattleSimulation} as a context handle —
 * this system threads {@code sim} through to them unchanged. That coupling
 * goes away on the {@code *SimContext} deprecation path; the dispatcher
 * itself doesn't reach into the sim.
 */
public final class UnitUpdateSystem implements AutoCloseable {

    public static final String MINIMUM_PARALLEL_UNITS_PROPERTY =
            "battle.unitUpdate.minimumParallelUnits";
    public static final String PARALLELISM_PROPERTY = "battle.unitUpdate.parallelism";
    private static final ThreadMXBean THREAD_CPU = ManagementFactory.getThreadMXBean();
    private static final EventType DISPATCH_EVENT_TYPE =
            EventType.getEventType(UnitDispatchEvent.class);

    /**
     * Worker submission through join, excluding mutation gates and profile merging.
     * Serial ticks have no worker dispatch and emit no event. The separate nanoTime
     * duration permits wall-clock calibration on runtimes whose JFR clock differs.
     */
    @Name("com.dillon.marines.UnitDispatch")
    @Label("Unit Worker Dispatch")
    @Category("Starsector Marines")
    @Enabled(false)
    @StackTrace(false)
    public static final class UnitDispatchEvent extends Event {
        public int tick;
        public int parallelism;
        public int liveUnits;
        @Timespan(Timespan.NANOSECONDS)
        public long wallNanos;
    }
    /**
     * Profiled crossover on the fixed-slice battle-fixture matrix. The tuning
     * property accepts {@code 0} to force parallel and {@link Integer#MAX_VALUE}
     * to force serial for repeatable A/B runs.
     */
    static final int DEFAULT_MINIMUM_PARALLEL_UNITS = 48;

    private final ForkJoinPool pool;
    private final DamageService damageService;
    private final TickInnerProfile tickInnerProfile;
    private final UnitRosterService roster;
    private final int minimumParallelUnits;
    /** The navigation grid's line-of-sight caches, so a terminating worker drops its own slot on the grid it ticked rather than on whichever grid happens to be current. */
    private final LosCaches losCaches;
    private final ConcurrentLinkedQueue<WorkerDiagnostics> diagnosticThreads =
            new ConcurrentLinkedQueue<>();
    private final ThreadLocal<WorkerDiagnostics> currentDiagnostics =
            ThreadLocal.withInitial(() -> {
                WorkerDiagnostics diagnostics = new WorkerDiagnostics();
                diagnosticThreads.add(diagnostics);
                return diagnostics;
            });
    private volatile boolean diagnosticsEnabled;
    private volatile TickDiagnostics lastTickDiagnostics;

    /** Individual unit wall time is the existing behavior-bucket timer, including role dispatch. */
    public record UnitSample(long entityId, UnitRole role, long durationNanos) {}

    /**
     * Optional UPDATE_UNITS evidence for the most recently completed tick.
     * Await time includes worker execution and is not additive with sampled
     * unit time. Sampled unit time is summed across threads, so it can exceed
     * dispatch wall time when work overlaps. Skipped riders/ambient actors do
     * not enter the behavior timer.
     * Worker CPU is sampled twice per participating thread across the dispatch,
     * not per unit; it includes scheduler overhead but excludes off-CPU time.
     * cpuMeasuredThreads distinguishes unavailable counters from zero CPU work.
     */
    public record TickDiagnostics(boolean parallel, int liveCount, int poolParallelism,
                                  long dispatchNanos, long awaitWorkersNanos,
                                  int activeThreads, int sampledUnitCount,
                                  long sampledUnitNanos, long maxThreadUnitNanos,
                                  int cpuMeasuredThreads, long workerCpuNanos,
                                  long maxThreadCpuNanos,
                                  List<UnitSample> slowestUnits) {}

    /** Enable only for dedicated profiling runs; the normal dispatch allocates no samples. */
    public void setDiagnosticsEnabled(boolean enabled) {
        diagnosticsEnabled = enabled;
        lastTickDiagnostics = null;
    }

    public TickDiagnostics lastTickDiagnostics() {
        return lastTickDiagnostics;
    }

    public UnitUpdateSystem(UnitRosterService roster,
                            DamageService damageService,
                            TickInnerProfile tickInnerProfile,
                            LosCaches losCaches) {
        this.losCaches = losCaches;
        this.pool = new ForkJoinPool(
                configuredPoolParallelism(),
                worker -> new BattleUpdateWorker(worker, losCaches),
                null, false);
        this.roster = roster;
        this.damageService = damageService;
        this.tickInnerProfile = tickInnerProfile;
        this.minimumParallelUnits = configuredMinimumParallelUnits();
        if (minimumParallelUnits < 0) {
            throw new IllegalArgumentException(
                    MINIMUM_PARALLEL_UNITS_PROPERTY + " must be non-negative");
        }
    }

    /**
     * Dispatch one tick of per-unit updates across the alive roster. Reads
     * the registry's dense array fresh each tick (the array reference can
     * be replaced by {@code allocate()} growth between ticks — see
     * {@link UnitRosterService#denseArray()}). Small snapshots walk ascending
     * dense indices on the sim thread; snapshots at the profiled crossover use
     * {@link IntStream#parallel()} pinned to this system's worker pool rather
     * than the common one. Both routes stay inside the same mutation-deferral
     * bracket so damage, occupancy, target changes, and spawns retain their
     * established end-of-phase semantics.
     */
    public void tick(BattleSimulation sim) {
        long[] snapshot = roster.denseArray();
        int liveCount = roster.liveCount();
        boolean captureDiagnostics = diagnosticsEnabled;
        if (captureDiagnostics) {
            for (WorkerDiagnostics thread : diagnosticThreads) thread.reset();
        }
        boolean parallel = shouldDispatchInParallel(
                liveCount, minimumParallelUnits, pool.getParallelism());
        AsyncDefendTrackRoutes routes = sim.asyncDefendTrackRoutes();
        long prepareStart = System.nanoTime();
        boolean memberRoutePhase = routes != null && routes.beginMemberUpdates(snapshot, liveCount);
        if (memberRoutePhase) tickInnerProfile.record(
                TickInnerProfile.Bucket.RALLY_REQUEST_PREPARE, System.nanoTime() - prepareStart);
        ShotService shots = sim.getShots();
        prepareStart = System.nanoTime();
        boolean projectilePhase = shots.beginMemberUpdates();
        if (projectilePhase) tickInnerProfile.record(
                TickInnerProfile.Bucket.PROJECTILE_PUBLICATION_PREPARE, System.nanoTime() - prepareStart);
        long dispatchStart = captureDiagnostics ? System.nanoTime() : 0L;
        long awaitWorkersNanos = 0L;
        damageService.enterParallel();
        try {
            if (parallel) {
                awaitWorkersNanos = dispatchParallel(
                        snapshot, liveCount, sim, captureDiagnostics);
            } else {
                for (int i = 0; i < liveCount; i++) {
                    updateUnit(snapshot[i], sim, captureDiagnostics);
                }
            }
        } catch (RuntimeException | Error failure) {
            if (projectilePhase) shots.abandonMemberUpdates();
            throw failure;
        } finally {
            damageService.exitParallel();
        }
        long dispatchNanos = captureDiagnostics ? System.nanoTime() - dispatchStart : 0L;
        // Only normal return establishes the worker join. Never drain mutable
        // member inboxes from finally: failed/interrupted dispatch can leave
        // child work running. A failed phase remains abandoned until close.
        if (memberRoutePhase) {
            long commitStart = System.nanoTime();
            routes.finishMemberUpdates(sim.getGrid(), sim.getOccupancyMap());
            tickInnerProfile.record(TickInnerProfile.Bucket.RALLY_REQUEST_COMMIT,
                    System.nanoTime() - commitStart);
        }
        if (projectilePhase) {
            long commitStart = System.nanoTime();
            shots.finishMemberUpdates();
            tickInnerProfile.record(TickInnerProfile.Bucket.PROJECTILE_PUBLICATION_COMMIT,
                    System.nanoTime() - commitStart);
        }
        TickInnerProfile.mergeAllInto(tickInnerProfile);
        if (captureDiagnostics) {
            lastTickDiagnostics = collectDiagnostics(
                    parallel, liveCount, dispatchNanos, awaitWorkersNanos);
        }
    }

    private long dispatchParallel(long[] snapshot, int liveCount, BattleSimulation sim,
                                  boolean captureDiagnostics) {
        UnitDispatchEvent event = null;
        long eventStart = 0L;
        if (DISPATCH_EVENT_TYPE.isEnabled()) {
            event = new UnitDispatchEvent();
            event.tick = sim.getSimTickIndex();
            event.parallelism = pool.getParallelism();
            event.liveUnits = liveCount;
            eventStart = System.nanoTime();
            event.begin();
        }
        try {
            ForkJoinTask<?> task = pool.submit(() -> IntStream.range(0, liveCount).parallel()
                    .forEach(i -> updateUnit(snapshot[i], sim, captureDiagnostics)));
            long awaitStart = captureDiagnostics ? System.nanoTime() : 0L;
            task.get();
            return captureDiagnostics ? System.nanoTime() - awaitStart : 0L;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("UPDATE_UNITS dispatch interrupted", ie);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("UPDATE_UNITS dispatch failed", cause);
        } finally {
            if (event != null) {
                event.end();
                event.wallNanos = System.nanoTime() - eventStart;
                event.commit();
            }
        }
    }

    private TickDiagnostics collectDiagnostics(boolean parallel, int liveCount,
                                               long dispatchNanos, long awaitWorkersNanos) {
        SlowUnitCollector slowest = new SlowUnitCollector();
        int activeThreads = 0;
        int sampledUnitCount = 0;
        long sampledUnitNanos = 0L;
        long maxThreadUnitNanos = 0L;
        int cpuMeasuredThreads = 0;
        long workerCpuNanos = 0L;
        long maxThreadCpuNanos = 0L;
        for (WorkerDiagnostics thread : diagnosticThreads) {
            if (thread.sampledUnitCount == 0) continue;
            activeThreads++;
            sampledUnitCount += thread.sampledUnitCount;
            sampledUnitNanos += thread.sampledUnitNanos;
            maxThreadUnitNanos = Math.max(maxThreadUnitNanos, thread.sampledUnitNanos);
            long cpu = cpuDeltaNanos(thread.cpuStartNanos, thread.cpuNow());
            if (cpu >= 0L) {
                cpuMeasuredThreads++;
                workerCpuNanos += cpu;
                maxThreadCpuNanos = Math.max(maxThreadCpuNanos, cpu);
            }
            for (UnitSample sample : thread.slowest.samples()) slowest.offer(sample);
        }
        return new TickDiagnostics(parallel, liveCount, pool.getParallelism(),
                dispatchNanos, awaitWorkersNanos, activeThreads, sampledUnitCount,
                sampledUnitNanos, maxThreadUnitNanos, cpuMeasuredThreads,
                workerCpuNanos, maxThreadCpuNanos, slowest.samples());
    }

    private final class WorkerDiagnostics {
        private final long threadId = Thread.currentThread().getId();
        private long cpuStartNanos = cpuNow();
        private final SlowUnitCollector slowest = new SlowUnitCollector();
        private int sampledUnitCount;
        private long sampledUnitNanos;

        private void reset() {
            cpuStartNanos = cpuNow();
            sampledUnitCount = 0;
            sampledUnitNanos = 0L;
            slowest.clear();
        }

        private long cpuNow() {
            return THREAD_CPU.isThreadCpuTimeSupported() && THREAD_CPU.isThreadCpuTimeEnabled()
                    ? THREAD_CPU.getThreadCpuTime(threadId) : -1L;
        }

        private void record(long entityId, long durationNanos, BattleSimulation sim) {
            sampledUnitCount++;
            sampledUnitNanos += durationNanos;
            if (slowest.accepts(entityId, durationNanos)) {
                slowest.offer(new UnitSample(
                        entityId, sim.role().role(entityId), durationNanos));
            }
        }
    }

    /** Per-thread bounded collector; the dispatch never contends on its hot path. */
    static final class SlowUnitCollector {
        static final int LIMIT = 8;
        private static final Comparator<UnitSample> ASCENDING =
                Comparator.comparingLong(UnitSample::durationNanos)
                        .thenComparingLong(UnitSample::entityId);
        private final PriorityQueue<UnitSample> samples = new PriorityQueue<>(ASCENDING);

        boolean accepts(long entityId, long durationNanos) {
            if (samples.size() < LIMIT) return true;
            UnitSample smallest = samples.peek();
            return durationNanos > smallest.durationNanos()
                    || (durationNanos == smallest.durationNanos()
                    && entityId > smallest.entityId());
        }

        void offer(UnitSample sample) {
            if (!accepts(sample.entityId(), sample.durationNanos())) return;
            if (samples.size() == LIMIT) samples.remove();
            samples.add(sample);
        }

        List<UnitSample> samples() {
            ArrayList<UnitSample> result = new ArrayList<>(samples);
            result.sort(ASCENDING.reversed());
            return List.copyOf(result);
        }

        void clear() {
            samples.clear();
        }
    }

    static boolean shouldDispatchInParallel(
            int liveCount, int minimumParallelUnits, int poolParallelism) {
        return liveCount > 0
                && poolParallelism > 1
                && liveCount >= minimumParallelUnits;
    }

    public static int configuredMinimumParallelUnits() {
        return Integer.getInteger(
                MINIMUM_PARALLEL_UNITS_PROPERTY,
                DEFAULT_MINIMUM_PARALLEL_UNITS);
    }

    public static int configuredPoolParallelism() {
        return resolvePoolParallelism(Runtime.getRuntime().availableProcessors(),
                System.getProperty(PARALLELISM_PROPERTY));
    }

    static int resolvePoolParallelism(int processors, String override) {
        if (override == null) return Math.max(1, processors - 1);
        int requested = Integer.parseInt(override);
        if (requested < 1 || requested > 32767) {
            throw new IllegalArgumentException(PARALLELISM_PROPERTY + " must be in [1, 32767]");
        }
        return requested;
    }

    static long cpuDeltaNanos(long start, long end) {
        return start < 0L || end < start ? -1L : end - start;
    }

    /** Releases this battle's owned worker pool and worker-local registries. */
    @Override
    public void close() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                pool.awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            currentDiagnostics.remove();
        }
    }

    /** Worker teardown is the ownership boundary for registered thread-local scratch. */
    private static final class BattleUpdateWorker extends ForkJoinWorkerThread {

        /** The grid this pool ticks for. A pool outlives no simulation, but a worker thread can be reused across grids, so the release names the one it belongs to. */
        private final LosCaches losCaches;

        private BattleUpdateWorker(ForkJoinPool pool, LosCaches losCaches) {
            super(pool);
            this.losCaches = losCaches;
            setDaemon(true);
        }

        @Override
        protected void onStart() {
            super.onStart();
            // Pool indices are assigned after construction; naming there made
            // every worker appear as Update-0 in JFR.
            setName("BattleSim-Update-" + getPoolIndex());
        }

        @Override
        protected void onTermination(Throwable failure) {
            try {
                TickInnerProfile.releaseCurrentThread();
                losCaches.releaseCurrentThread();
            } finally {
                super.onTermination(failure);
            }
        }
    }

    /**
     * Routes the per-tick update for one unit. Fall-back is a pre-dispatch
     * override that applies to any <em>thinking</em> unit (one carrying
     * {@code AI_STATE}) regardless of its role; static emplacements (turrets,
     * hubs) have no AI_STATE and never fall back, so they skip the check and
     * route straight to their per-role behavior. The {@code hasAiState} guard
     * short-circuits before the fail-loud {@code fallbackTimer} read. Behavior
     * classes hold no per-system instance state — they're invoked through their
     * static {@code INSTANCE} singletons.
     */
    private void updateUnit(long u, BattleSimulation sim, boolean captureDiagnostics) {
        // Ambient work is exclusive while active. The battle-owned service
        // releases interrupted actors before this phase, so their existing
        // role resumes here without a role swap or a second actor model.
        if (sim.ambientTasks().isControlling(u)) return;
        if (sim.directControl().isControlling(u)) return;
        // A passenger is inside a vehicle: it has no position to act from and
        // nothing to decide until it is set down again. Checked explicitly
        // rather than inferred from a missing POSITION, so a unit that lost its
        // position by accident still fails loudly instead of going quiet.
        if (sim.transport().isRiding(u)) return;
        // First participation must capture CPU before the first behavior, not
        // in its finally block; existing workers were sampled by tick's reset.
        WorkerDiagnostics diagnostics = captureDiagnostics ? currentDiagnostics.get() : null;
        long t0 = System.nanoTime();
        UnitBehavior behavior;
        TickInnerProfile.Bucket bucket;
        if (sim.world().hasAiState(u) && sim.world().fallbackTimer(u) > 0f) {
            behavior = FallbackBehavior.INSTANCE;
            bucket = TickInnerProfile.Bucket.BEHAVIOR_FALLBACK;
        } else {
            UnitRole role = sim.role().role(u);
            behavior = behaviorFor(role);
            bucket = innerBucketForRole(role);
        }
        TickInnerProfile profile = TickInnerProfile.current();
        profile.enterBehavior(bucket);
        try {
            behavior.update(u, sim);
        } finally {
            profile.exitBehavior();
            long elapsed = System.nanoTime() - t0;
            profile.record(bucket, elapsed);
            if (diagnostics != null) diagnostics.record(u, elapsed, sim);
        }
        // Route through TickInnerProfile.current() so workers in the parallel
        // dispatch write to their per-thread profile (ThreadLocal auto-init),
        // not directly to the canonical sim instance. mergeAllInto folds the
        // per-worker recordings into the canonical at the end of the tick.
    }

    /**
     * Maps a {@link UnitRole} to its per-role {@link UnitBehavior} singleton.
     * {@code PLANTER} intentionally routes through {@link CombatantBehavior}
     * → {@link com.dillon.starsectormarines.battle.infantry.GoapInfantryBehavior} — the plant action lives in the squad
     * plan, not a per-unit dispatch.
     */
    private static UnitBehavior behaviorFor(UnitRole role) {
        switch (role) {
            case KIT_RETRIEVER:  return KitRetrieverBehavior.INSTANCE;
            case FLEE:           return FleeBehavior.INSTANCE;
            case TURRET:         return TurretBehavior.INSTANCE;
            case GARRISON:       return CombatantBehavior.INSTANCE;
            case PATROL:         return CombatantBehavior.INSTANCE;
            case STRUCTURE:      return StructureBehavior.INSTANCE;
            case DRONE_HUB:      return DroneHubBehavior.INSTANCE;
            case DRONE_PATROL:   return GoapDroneBehavior.INSTANCE;
            case SWARM_PRESSURE: return SwarmPressureBehavior.INSTANCE;
            case OBJECTIVE_CAMPER:
            case VIP:
            case COMBATANT:
            default:             return CombatantBehavior.INSTANCE;
        }
    }

    /**
     * Mirrors {@link #behaviorFor(UnitRole)} — every role that returns the
     * same behavior instance there should map to the same bucket here so the
     * inner profile partitions {@code updateUnit} time correctly across
     * behavior classes. Default falls into {@code BEHAVIOR_COMBATANT}
     * because {@code behaviorFor} also defaults to {@link CombatantBehavior}.
     */
    static TickInnerProfile.Bucket innerBucketForRole(UnitRole role) {
        switch (role) {
            case KIT_RETRIEVER: return TickInnerProfile.Bucket.BEHAVIOR_KIT_RETRIEVER;
            case FLEE:          return TickInnerProfile.Bucket.BEHAVIOR_FLEE;
            case TURRET:        return TickInnerProfile.Bucket.BEHAVIOR_TURRET;
            case STRUCTURE:     return TickInnerProfile.Bucket.BEHAVIOR_STRUCTURE;
            case DRONE_HUB:     return TickInnerProfile.Bucket.BEHAVIOR_DRONE_HUB;
            case DRONE_PATROL:  return TickInnerProfile.Bucket.BEHAVIOR_GOAP_DRONE;
            case SWARM_PRESSURE:return TickInnerProfile.Bucket.BEHAVIOR_SWARM_PRESSURE;
            default:            return TickInnerProfile.Bucket.BEHAVIOR_COMBATANT;
        }
    }
}
