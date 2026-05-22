package com.dillon.starsectormarines.battle.damage;

import com.dillon.starsectormarines.battle.PendingDamage;
import com.dillon.starsectormarines.battle.PendingOccupancyDelta;
import com.dillon.starsectormarines.battle.PendingTargetMutation;
import com.dillon.starsectormarines.battle.Unit;

import java.util.ArrayList;

/**
 * Owns the three parallel-dispatch safety queues used during the
 * UPDATE_UNITS phase ({@link PendingDamage} writes, {@link PendingTargetMutation}
 * shooter-driven writes to the target's path/target/fallback fields, and
 * {@link PendingOccupancyDelta} occupancy + destIndex mutations), the matching
 * recycle pools, and the {@code insideParallel} flag itself. The "queue owns
 * the inline-vs-defer decision" pattern: callers invoke one of the
 * {@code applyXxx} methods, and the service either calls the supplied
 * inline applier directly (serial / off-tick path) or pools-and-queues the
 * record (parallel UPDATE_UNITS path). Flush is then a straight drain that
 * goes through the same applier.
 *
 * <p>Sibling slice to {@link com.dillon.starsectormarines.battle.fx.EffectsService},
 * {@link com.dillon.starsectormarines.battle.vision.VisionService},
 * {@link com.dillon.starsectormarines.battle.shots.ShotService},
 * {@link com.dillon.starsectormarines.battle.command.CommanderService},
 * {@link com.dillon.starsectormarines.battle.objective.ObjectivesService}.
 *
 * <p>The four appliers are bound method refs supplied once at construction
 * by {@code BattleSimulation} — one allocation for the lifetime of the sim,
 * shared between the inline branch and the flush branch so semantics stay
 * identical across both paths.
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

    private final ArrayList<PendingDamage> pendingDamage = new ArrayList<>();
    private final ArrayList<PendingDamage> pendingDamagePool = new ArrayList<>();
    private final ArrayList<PendingTargetMutation> pendingTargetMutations = new ArrayList<>();
    private final ArrayList<PendingTargetMutation> pendingTargetMutationsPool = new ArrayList<>();
    private final ArrayList<PendingOccupancyDelta> pendingOccupancy = new ArrayList<>();
    private final ArrayList<PendingOccupancyDelta> pendingOccupancyPool = new ArrayList<>();

    /**
     * True only while the parallel UPDATE_UNITS dispatch is in flight — read
     * by every {@code applyXxx} method to choose between queue (parallel
     * callers) and inline apply (serial tick phases, off-tick test paths,
     * mid-frame UI hooks). Inline outside the parallel section is faster
     * (no queue allocation) and sidesteps the bug where serial phases past
     * the queue's drain point would enqueue mutations that leak into the
     * next tick.
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
     * Damage entry point. Serial callers (off-tick, post-UPDATE_UNITS tick
     * phases) apply inline via {@link DamageApplier}; parallel callers
     * (UPDATE_UNITS workers) pool-allocate and queue for {@link #flushPendingDamage()}.
     */
    public void applyDamage(Unit target, float damage, float vsTurretMult, float moraleImpact) {
        if (!insideParallel) {
            damageApplier.apply(target, damage, vsTurretMult, moraleImpact);
            return;
        }
        synchronized (pendingDamage) {
            PendingDamage e = pendingDamagePool.isEmpty()
                    ? new PendingDamage()
                    : pendingDamagePool.remove(pendingDamagePool.size() - 1);
            e.target = target;
            e.damage = damage;
            e.vsTurretMult = vsTurretMult;
            e.moraleImpact = moraleImpact;
            pendingDamage.add(e);
        }
    }

    /** Target-reprioritize write. Inline writes unconditionally; queued path snapshots {@code expectedTarget} so the flush can detect a concurrent self-retarget and preserve the newer choice. */
    public void applyReprio(Unit target, Unit expectedTarget) {
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
            m.expectedTarget = expectedTarget;
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
     * Drains queued damage FIFO. Runs serially in
     * {@code TickProfile.Phase.APPLY_DAMAGE}, between UPDATE_UNITS and the
     * subsystem ticks that read HP. Behavioral note preserved from the
     * pre-extraction body: a doomed target gets one tick of action before
     * the drain kills it (vs the pre-deferral inline-apply where a doomed
     * later-in-iteration target was already dead when its own update ran).
     */
    public void flushPendingDamage() {
        if (pendingDamage.isEmpty()) return;
        for (int i = 0, n = pendingDamage.size(); i < n; i++) {
            PendingDamage e = pendingDamage.get(i);
            damageApplier.apply(e.target, e.damage, e.vsTurretMult, e.moraleImpact);
            e.target = null;
            pendingDamagePool.add(e);
        }
        pendingDamage.clear();
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
                        if (target.target == m.expectedTarget) reprioApplier.apply(target);
                        break;
                    case FALLBACK:
                        fallbackApplier.apply(target, m.fallbackCellX, m.fallbackCellY);
                        break;
                }
            }
            m.target = null;
            m.expectedTarget = null;
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
