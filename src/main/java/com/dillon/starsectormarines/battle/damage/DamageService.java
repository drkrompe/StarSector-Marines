package com.dillon.starsectormarines.battle.damage;

import com.dillon.starsectormarines.battle.sim.PendingOccupancyDelta;
import com.dillon.starsectormarines.battle.sim.PendingTargetMutation;
import com.dillon.starsectormarines.battle.unit.Unit;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Mailbox for damage + parallel-dispatch safety queues. Owns three queues
 * used during the UPDATE_UNITS phase:
 * <ul>
 *   <li>SoA damage queue (parallel arrays — see below)</li>
 *   <li>{@link PendingTargetMutation} shooter-driven writes to the target's
 *       path/target/fallback fields</li>
 *   <li>{@link PendingOccupancyDelta} occupancy + destIndex mutations</li>
 * </ul>
 *
 * <p>Pattern: callers invoke one of the {@code applyXxx} methods, and the
 * service either resolves inline (serial / off-tick path) or pools-and-queues
 * for the matching flush (parallel UPDATE_UNITS path). Damage flushes feed
 * back into the injected {@link DamageApplier} so inline and queued paths use
 * identical semantics — the applier is always the same method ref
 * ({@code DamageResolver::resolve}).
 *
 * <p><b>Damage uses SoA, not AoS.</b> Four parallel arrays
 * ({@code Unit[] pendingTargets}, three {@code float[]}s) + an {@code int}
 * count is enough state — no {@code DamageEvent} record. The inline path
 * never allocates; the queued path grows the arrays by doubling when full,
 * which steady-state means zero allocation after the first overflow tick.
 * The other two queues (target-mutation, occupancy) keep their AoS pooled
 * records — they have non-primitive payloads (an enum, a "kind" tag) where
 * SoA wouldn't pay off.
 *
 * <p>Sibling slice to {@link com.dillon.starsectormarines.battle.fx.EffectsService},
 * {@link com.dillon.starsectormarines.battle.vision.VisionService},
 * {@link com.dillon.starsectormarines.battle.shots.ShotService},
 * {@link com.dillon.starsectormarines.battle.command.CommanderService},
 * {@link com.dillon.starsectormarines.battle.objective.ObjectivesService}.
 *
 * <p>Appliers are bound method refs supplied once at construction so they're
 * shared between the inline branch and the flush branch — one allocation for
 * the lifetime of the sim, identical semantics across both paths.
 */
public final class DamageService {

    @FunctionalInterface public interface DamageApplier {
        void apply(Unit target, float damage, float vsTurretMult, float moraleImpact);
    }
    @FunctionalInterface public interface ReprioApplier {
        void apply(Unit target);
    }
    @FunctionalInterface public interface FallbackApplier {
        void apply(Unit target, int fbX, int fbY);
    }
    @FunctionalInterface public interface OccupancyApplier {
        /** {@code Integer.MIN_VALUE} for an old / new dest coordinate is the "no-op" sentinel — that half of the delta is skipped. */
        void apply(Unit u, int oldDestX, int oldDestY, int newDestX, int newDestY);
    }

    private final DamageApplier damageApplier;
    private final ReprioApplier reprioApplier;
    private final FallbackApplier fallbackApplier;
    private final OccupancyApplier occupancyApplier;

    // ---- SoA damage queue ----
    //
    // Four parallel arrays, grown by doubling when full. Lock granularity is
    // the service instance itself — the parallel UPDATE_UNITS workers all
    // contend on one monitor, but the contention window is just a couple of
    // array writes so it's not measurable in practice.
    private static final int INITIAL_DAMAGE_CAPACITY = 64;
    private Unit[] dmgTarget = new Unit[INITIAL_DAMAGE_CAPACITY];
    private float[] dmgDamage = new float[INITIAL_DAMAGE_CAPACITY];
    private float[] dmgVsTurretMult = new float[INITIAL_DAMAGE_CAPACITY];
    private float[] dmgMoraleImpact = new float[INITIAL_DAMAGE_CAPACITY];
    private int dmgCount = 0;
    private final Object dmgLock = new Object();

    private final ArrayList<PendingTargetMutation> pendingTargetMutations = new ArrayList<>();
    private final ArrayList<PendingTargetMutation> pendingTargetMutationsPool = new ArrayList<>();
    private final ArrayList<PendingOccupancyDelta> pendingOccupancy = new ArrayList<>();
    private final ArrayList<PendingOccupancyDelta> pendingOccupancyPool = new ArrayList<>();

    /**
     * True only while the parallel UPDATE_UNITS dispatch is in flight — read
     * by every {@code applyXxx} method to choose between queue (parallel
     * callers) and inline apply (serial tick phases, off-tick test paths,
     * mid-frame UI hooks). Inline outside the parallel section is faster
     * (no queue write) and sidesteps the bug where serial phases past the
     * queue's drain point would enqueue mutations that leak into the next
     * tick.
     */
    private volatile boolean insideParallel = false;

    public DamageService(DamageApplier damageApplier,
                         ReprioApplier reprioApplier,
                         FallbackApplier fallbackApplier,
                         OccupancyApplier occupancyApplier) {
        this.damageApplier = damageApplier;
        this.reprioApplier = reprioApplier;
        this.fallbackApplier = fallbackApplier;
        this.occupancyApplier = occupancyApplier;
    }

    public void enterParallel() { insideParallel = true; }
    public void exitParallel()  { insideParallel = false; }
    /** Mostly an internal predicate — exposed so {@code BattleSimulation.queueSpawn} can branch on the same flag for its own (unit-spawn) queue. */
    public boolean isParallel() { return insideParallel; }

    /**
     * Damage entry point — the mailbox front door. Serial callers (off-tick,
     * post-UPDATE_UNITS tick phases, AoE detonation drain, external strafing
     * reroute) resolve inline through the injected applier; parallel callers
     * (UPDATE_UNITS workers) write into the SoA queue for
     * {@link #flushPendingDamage()}. No per-call object allocation in either
     * path.
     */
    public void applyDamage(Unit target, float damage, float vsTurretMult, float moraleImpact) {
        if (!insideParallel) {
            damageApplier.apply(target, damage, vsTurretMult, moraleImpact);
            return;
        }
        synchronized (dmgLock) {
            int i = dmgCount;
            if (i == dmgTarget.length) growDamageArrays(i * 2);
            dmgTarget[i] = target;
            dmgDamage[i] = damage;
            dmgVsTurretMult[i] = vsTurretMult;
            dmgMoraleImpact[i] = moraleImpact;
            dmgCount = i + 1;
        }
    }

    private void growDamageArrays(int newCapacity) {
        dmgTarget = Arrays.copyOf(dmgTarget, newCapacity);
        dmgDamage = Arrays.copyOf(dmgDamage, newCapacity);
        dmgVsTurretMult = Arrays.copyOf(dmgVsTurretMult, newCapacity);
        dmgMoraleImpact = Arrays.copyOf(dmgMoraleImpact, newCapacity);
    }

    /** Target-reprioritize write. Inline writes unconditionally; queued path snapshots {@code expectedTargetId} so the flush can detect a concurrent self-retarget and preserve the newer choice. */
    public void applyReprio(Unit target, long expectedTargetId) {
        if (!insideParallel) {
            reprioApplier.apply(target);
            return;
        }
        synchronized (pendingTargetMutations) {
            PendingTargetMutation m = pendingTargetMutationsPool.isEmpty()
                    ? new PendingTargetMutation()
                    : pendingTargetMutationsPool.remove(pendingTargetMutationsPool.size() - 1);
            m.target = target;
            m.kind = PendingTargetMutation.Kind.REPRIORITIZE;
            m.expectedTargetId = expectedTargetId;
            pendingTargetMutations.add(m);
        }
    }

    /** Fallback-cell write. Inline applies the 3 field writes + path-clear; queued path drains in {@link #flushPendingTargetMutations()}. */
    public void applyFallback(Unit target, int fbX, int fbY) {
        if (!insideParallel) {
            fallbackApplier.apply(target, fbX, fbY);
            return;
        }
        synchronized (pendingTargetMutations) {
            PendingTargetMutation m = pendingTargetMutationsPool.isEmpty()
                    ? new PendingTargetMutation()
                    : pendingTargetMutationsPool.remove(pendingTargetMutationsPool.size() - 1);
            m.target = target;
            m.kind = PendingTargetMutation.Kind.FALLBACK;
            m.fallbackCellX = fbX;
            m.fallbackCellY = fbY;
            pendingTargetMutations.add(m);
        }
    }

    /** Occupancy + destIndex delta. Callers pass {@code Integer.MIN_VALUE} for an old / new coord to mark that half as a no-op. Serial callers apply inline; parallel callers queue for {@link #flushPendingOccupancyDeltas()}. */
    public void applyOccupancyDelta(Unit u, int oldDestX, int oldDestY, int newDestX, int newDestY) {
        if (!insideParallel) {
            occupancyApplier.apply(u, oldDestX, oldDestY, newDestX, newDestY);
            return;
        }
        synchronized (pendingOccupancy) {
            PendingOccupancyDelta d = pendingOccupancyPool.isEmpty()
                    ? new PendingOccupancyDelta()
                    : pendingOccupancyPool.remove(pendingOccupancyPool.size() - 1);
            d.u = u;
            d.oldDestX = oldDestX;
            d.oldDestY = oldDestY;
            d.newDestX = newDestX;
            d.newDestY = newDestY;
            pendingOccupancy.add(d);
        }
    }

    /**
     * Drains the SoA damage queue FIFO through the registered applier. Runs
     * serially in {@code TickProfile.Phase.APPLY_DAMAGE}, between UPDATE_UNITS
     * and the subsystem ticks that read HP. Behavioral note preserved from
     * the pre-deferral inline-apply era: a doomed target gets one tick of
     * action before the drain kills it (vs the pre-deferral inline path where
     * a doomed later-in-iteration target was already dead when its own update
     * ran).
     */
    public void flushPendingDamage() {
        int n = dmgCount;
        if (n == 0) return;
        for (int i = 0; i < n; i++) {
            damageApplier.apply(dmgTarget[i], dmgDamage[i], dmgVsTurretMult[i], dmgMoraleImpact[i]);
            dmgTarget[i] = null; // release ref so GC can collect dead Units
        }
        dmgCount = 0;
    }

    /**
     * Drains queued target-side mutations FIFO. Runs in
     * {@code TickProfile.Phase.APPLY_DAMAGE} immediately after
     * {@link #flushPendingDamage()} so REPRIORITIZE/FALLBACK skip targets that
     * the queued damage just killed. REPRIORITIZE only nulls the target field
     * if it still matches the shooter's snapshot — preserves a concurrent
     * self-retarget done during the parallel phase.
     */
    public void flushPendingTargetMutations() {
        if (pendingTargetMutations.isEmpty()) return;
        for (int i = 0, n = pendingTargetMutations.size(); i < n; i++) {
            PendingTargetMutation m = pendingTargetMutations.get(i);
            Unit target = m.target;
            if (target.isAlive()) {
                switch (m.kind) {
                    case REPRIORITIZE:
                        if (target.getTargetId() == m.expectedTargetId) reprioApplier.apply(target);
                        break;
                    case FALLBACK:
                        fallbackApplier.apply(target, m.fallbackCellX, m.fallbackCellY);
                        break;
                }
            }
            m.target = null;
            m.expectedTargetId = 0L;
            pendingTargetMutationsPool.add(m);
        }
        pendingTargetMutations.clear();
    }

    /**
     * Drains queued occupancy + destIndex deltas FIFO. Runs in
     * {@code TickProfile.Phase.APPLY_OCCUPANCY} at the end of UPDATE_UNITS,
     * before any subsequent phase reads the occupancy map.
     */
    public void flushPendingOccupancyDeltas() {
        if (pendingOccupancy.isEmpty()) return;
        for (int i = 0, n = pendingOccupancy.size(); i < n; i++) {
            PendingOccupancyDelta d = pendingOccupancy.get(i);
            occupancyApplier.apply(d.u, d.oldDestX, d.oldDestY, d.newDestX, d.newDestY);
            d.u = null;
            pendingOccupancyPool.add(d);
        }
        pendingOccupancy.clear();
    }
}
