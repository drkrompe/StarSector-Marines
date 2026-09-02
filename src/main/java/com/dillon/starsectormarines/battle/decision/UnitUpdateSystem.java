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
import com.dillon.starsectormarines.battle.nav.LosCaches;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
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
        damageService.enterParallel();
        try {
            if (shouldDispatchInParallel(
                    liveCount, minimumParallelUnits, pool.getParallelism())) {
                dispatchParallel(snapshot, liveCount, sim);
            } else {
                for (int i = 0; i < liveCount; i++) {
                    updateUnit(snapshot[i], sim);
                }
            }
        } finally {
            damageService.exitParallel();
        }
        TickInnerProfile.mergeAllInto(tickInnerProfile);
    }

    private void dispatchParallel(
            long[] snapshot, int liveCount, BattleSimulation sim) {
        try {
            pool.submit(() -> IntStream.range(0, liveCount).parallel()
                            .forEach(i -> updateUnit(snapshot[i], sim)))
                    .get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("UPDATE_UNITS dispatch interrupted", ie);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("UPDATE_UNITS dispatch failed", cause);
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
        return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
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
    private void updateUnit(long u, BattleSimulation sim) {
        // Ambient work is exclusive while active. The battle-owned service
        // releases interrupted actors before this phase, so their existing
        // role resumes here without a role swap or a second actor model.
        if (sim.ambientTasks().isControlling(u)) return;
        // A passenger is inside a vehicle: it has no position to act from and
        // nothing to decide until it is set down again. Checked explicitly
        // rather than inferred from a missing POSITION, so a unit that lost its
        // position by accident still fails loudly instead of going quiet.
        if (sim.transport().isRiding(u)) return;
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
            profile.record(bucket, System.nanoTime() - t0);
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
