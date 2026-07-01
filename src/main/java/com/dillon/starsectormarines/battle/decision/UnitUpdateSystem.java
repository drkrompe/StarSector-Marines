package com.dillon.starsectormarines.battle.decision;
import com.dillon.starsectormarines.battle.infantry.CombatantBehavior;
import com.dillon.starsectormarines.battle.drone.DroneHubBehavior;
import com.dillon.starsectormarines.battle.infantry.KitRetrieverBehavior;
import com.dillon.starsectormarines.battle.turret.StructureBehavior;
import com.dillon.starsectormarines.battle.turret.TurretBehavior;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.drone.GoapDroneBehavior;
import com.dillon.starsectormarines.battle.combat.DamageService;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.stream.IntStream;

/**
 * Parallel per-unit dispatch — owns the {@code UPDATE_UNITS} phase that
 * routes each alive {@link Entity} to its role-specific {@link UnitBehavior}.
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
public final class UnitUpdateSystem {

    private final ForkJoinPool pool;
    private final DamageService damageService;
    private final TickInnerProfile tickInnerProfile;
    private final UnitRosterService roster;

    public UnitUpdateSystem(UnitRosterService roster,
                            DamageService damageService,
                            TickInnerProfile tickInnerProfile) {
        this.pool = new ForkJoinPool(
                Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                p -> {
                    ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(p);
                    t.setDaemon(true);
                    t.setName("BattleSim-Update-" + t.getPoolIndex());
                    return t;
                },
                null, false);
        this.roster = roster;
        this.damageService = damageService;
        this.tickInnerProfile = tickInnerProfile;
    }

    /**
     * Dispatch one tick of per-unit updates across the alive roster. Reads
     * the registry's dense array fresh each tick (the array reference can
     * be replaced by {@code allocate()} growth between ticks — see
     * {@link UnitRosterService#denseArray()}). Workers iterate
     * {@code [0, liveCount)} indices in parallel via {@link IntStream}; the
     * submission to {@link #pool} pins the stream to our worker pool rather
     * than the common one.
     */
    public void tick(BattleSimulation sim) {
        Entity[] snapshot = roster.denseArray();
        int liveCount = roster.liveCount();
        damageService.enterParallel();
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
        } finally {
            damageService.exitParallel();
        }
        TickInnerProfile.mergeAllInto(tickInnerProfile);
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
    private void updateUnit(Entity u, BattleSimulation sim) {
        long t0 = System.nanoTime();
        TickInnerProfile.Bucket bucket;
        if (sim.world().hasAiState(u.entityId) && sim.world().fallbackTimer(u.entityId) > 0f) {
            FallbackBehavior.INSTANCE.update(u, sim);
            bucket = TickInnerProfile.Bucket.BEHAVIOR_FALLBACK;
        } else {
            UnitRole role = sim.role().role(u.entityId);
            behaviorFor(role).update(u, sim);
            bucket = innerBucketForRole(role);
        }
        // Route through TickInnerProfile.current() so workers in the parallel
        // dispatch write to their per-thread profile (ThreadLocal auto-init),
        // not directly to the canonical sim instance. mergeAllInto folds the
        // per-worker recordings into the canonical at the end of the tick.
        TickInnerProfile.current().record(bucket, System.nanoTime() - t0);
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
    private static TickInnerProfile.Bucket innerBucketForRole(UnitRole role) {
        switch (role) {
            case KIT_RETRIEVER: return TickInnerProfile.Bucket.BEHAVIOR_KIT_RETRIEVER;
            case FLEE:          return TickInnerProfile.Bucket.BEHAVIOR_FLEE;
            case TURRET:        return TickInnerProfile.Bucket.BEHAVIOR_TURRET;
            case STRUCTURE:     return TickInnerProfile.Bucket.BEHAVIOR_STRUCTURE;
            case DRONE_HUB:     return TickInnerProfile.Bucket.BEHAVIOR_DRONE_HUB;
            case DRONE_PATROL:  return TickInnerProfile.Bucket.BEHAVIOR_GOAP_DRONE;
            default:            return TickInnerProfile.Bucket.BEHAVIOR_COMBATANT;
        }
    }
}
