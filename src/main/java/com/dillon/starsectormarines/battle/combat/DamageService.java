package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.sim.PendingOccupancyDelta;
import com.dillon.starsectormarines.battle.sim.PendingTargetMutation;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.LongFunction;

/**
 * Mailbox for damage + parallel-dispatch safety queues. Owns three queues
 * used during the UPDATE_UNITS phase (and, for damage/reprio/fallback, the
 * later serial {@code FIRING} phase too — see {@link #deferCombatEffects}):
 * <ul>
 *   <li>SoA damage queue (parallel arrays — see below)</li>
 *   <li>{@link PendingTargetMutation} shooter-driven writes to the target's
 *       path/target/fallback fields</li>
 *   <li>{@link PendingOccupancyDelta} occupancy + destIndex mutations</li>
 * </ul>
 *
 * <p>Pattern: callers invoke one of the {@code applyXxx} methods, and the
 * service either resolves inline (serial / off-tick path) or pools-and-queues
 * for the matching flush (parallel UPDATE_UNITS path, or the serial-but-
 * deferred {@code FIRING} path for damage/reprio/fallback only). Damage
 * flushes feed back into the injected {@link DamageApplier} so inline and
 * queued paths use identical semantics — the applier is always the same
 * method ref ({@code DamageResolver::resolve}).
 *
 * <p><b>Damage uses SoA, not AoS.</b> Four parallel arrays
 * ({@code Entity[] pendingTargets}, three {@code float[]}s) + an {@code int}
 * count is enough state — no {@code DamageEvent} record. The inline path
 * never allocates; the queued path grows the arrays by doubling when full,
 * which steady-state means zero allocation after the first overflow tick.
 * The other two queues (target-mutation, occupancy) keep their AoS pooled
 * records — they have non-primitive payloads (an enum, a "kind" tag) where
 * SoA wouldn't pay off.
 *
 * <p>Sibling slice to {@link com.dillon.starsectormarines.battle.combat.fx.EffectsService},
 * {@link FogOfWarService},
 * {@link com.dillon.starsectormarines.battle.combat.ShotService},
 * {@link com.dillon.starsectormarines.battle.command.CommanderService},
 * {@link com.dillon.starsectormarines.battle.command.objective.ObjectivesService}.
 *
 * <p>Appliers are bound method refs supplied once at construction so they're
 * shared between the inline branch and the flush branch — one allocation for
 * the lifetime of the sim, identical semantics across both paths.
 */
public final class DamageService {

    @FunctionalInterface public interface DamageApplier {
        void apply(Entity target, float damage, float vsTurretMult, float moraleImpact);
    }
    @FunctionalInterface public interface ReprioApplier {
        /**
         * Clears {@code target}'s target field, but only if it still equals
         * {@code expectedTargetId} — the compare-and-clear lives registry-side
         * (the applier holds the registry; this service holds only the id
         * resolver). On the serial inline path the guard is trivially true
         * (nothing mutates between snapshot and apply); on the queued flush it
         * preserves a concurrent self-retarget done during the parallel phase.
         */
        void apply(Entity target, long expectedTargetId);
    }
    @FunctionalInterface public interface FallbackApplier {
        void apply(Entity target, int fbX, int fbY);
    }
    @FunctionalInterface public interface OccupancyApplier {
        /** {@code Integer.MIN_VALUE} for an old / new dest coordinate is the "no-op" sentinel — that half of the delta is skipped. */
        void apply(Entity u, int oldDestX, int oldDestY, int newDestX, int newDestY);
    }

    private final DamageApplier damageApplier;
    private final ReprioApplier reprioApplier;
    private final FallbackApplier fallbackApplier;
    private final OccupancyApplier occupancyApplier;
    /**
     * Entity-id → live {@code Entity} (null if released or never registered) —
     * the registry's {@code getOrNull}. Used only by the two flush drains to
     * resolve a queued {@code targetId}/{@code unitId} back to its unit; a null
     * resolve means the entity was released between enqueue and drain (a target
     * the queued damage just killed), which replaces the old dangling-ref
     * {@code isAlive()} check.
     */
    private final LongFunction<Entity> resolver;

    // ---- SoA damage queue ----
    //
    // Four parallel arrays, grown by doubling when full. Lock granularity is
    // the service instance itself — the parallel UPDATE_UNITS workers all
    // contend on one monitor, but the contention window is just a couple of
    // array writes so it's not measurable in practice.
    private static final int INITIAL_DAMAGE_CAPACITY = 64;
    private Entity[] dmgTarget = new Entity[INITIAL_DAMAGE_CAPACITY];
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

    /**
     * True while {@code TickProfile.Phase.FIRING} is executing — a second,
     * narrower deferral gate alongside {@link #insideParallel}. FIRING runs
     * serially (no parallel-write hazard to guard against), but it sits
     * <em>between</em> two of this tick's drain points, so the two combat-
     * effect families it can trigger need opposite treatment:
     * <ul>
     *   <li>Damage / reprio / fallback (via {@link #applyDamage},
     *       {@link #applyReprio}, {@link #applyFallback}) — FIRING runs
     *       <em>before</em> {@link #flushPendingDamage()} /
     *       {@link #flushPendingTargetMutations()} this tick (they drain in
     *       {@code APPLY_DAMAGE}, after DETONATIONS), so these three still
     *       need to queue. This restores the exact semantics fires had when
     *       they ran inside the parallel UPDATE_UNITS dispatch: a doomed
     *       unit's own burst continuation gets one final action before the
     *       drain kills it, two shooters converging on one fragile target
     *       both land their shot (overkill absorbed by the drain's dead-
     *       target guard, not a first-shooter-wins race), and a
     *       REPRIORITIZE from a hit rolled during FIRING is subject to the
     *       same queued expectedTargetId guard a parallel-phase hit got.</li>
     *   <li>Occupancy (via {@link #applyOccupancyDelta}) — FIRING runs
     *       <em>after</em> {@link #flushPendingOccupancyDeltas()} already
     *       drained this tick ({@code APPLY_OCCUPANCY}, right after
     *       UPDATE_UNITS). A reposition queued from FIRING wouldn't drain
     *       until next tick's APPLY_OCCUPANCY, leaking a stale delta across
     *       the tick boundary — the exact bug {@link #insideParallel}'s
     *       doc above warns about. So occupancy deliberately does NOT
     *       check this flag and stays gated on {@link #insideParallel}
     *       alone, applying inline from FIRING.</li>
     * </ul>
     */
    private volatile boolean deferCombatEffects = false;

    public DamageService(DamageApplier damageApplier,
                         ReprioApplier reprioApplier,
                         FallbackApplier fallbackApplier,
                         OccupancyApplier occupancyApplier,
                         LongFunction<Entity> resolver) {
        this.damageApplier = damageApplier;
        this.reprioApplier = reprioApplier;
        this.fallbackApplier = fallbackApplier;
        this.occupancyApplier = occupancyApplier;
        this.resolver = resolver;
    }

    public void enterParallel() { insideParallel = true; }
    public void exitParallel()  { insideParallel = false; }
    /** Mostly an internal predicate — exposed so {@code BattleSimulation.queueSpawn} can branch on the same flag for its own (unit-spawn) queue. */
    public boolean isParallel() { return insideParallel; }

    /** Opens the {@link #deferCombatEffects} window — bracket around {@code FiringSystem.tick} in {@code BattleSimulation.tick}. See the field doc for why occupancy is deliberately excluded. */
    public void enterCombatEffectDeferral() { deferCombatEffects = true; }
    public void exitCombatEffectDeferral()  { deferCombatEffects = false; }

    /**
     * Damage entry point — the mailbox front door. Serial callers (off-tick,
     * post-{@code APPLY_DAMAGE} tick phases, AoE detonation drain, external
     * strafing reroute) resolve inline through the injected applier; parallel
     * UPDATE_UNITS workers AND the serial {@code FIRING} phase (see
     * {@link #deferCombatEffects}) write into the SoA queue for
     * {@link #flushPendingDamage()}. No per-call object allocation in either
     * path.
     */
    public void applyDamage(Entity target, float damage, float vsTurretMult, float moraleImpact) {
        if (!insideParallel && !deferCombatEffects) {
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

    /** Target-reprioritize write. Inline writes unconditionally; queued path (parallel UPDATE_UNITS or the deferred {@code FIRING} phase — see {@link #deferCombatEffects}) snapshots {@code expectedTargetId} so the flush can detect a concurrent self-retarget and preserve the newer choice. */
    public void applyReprio(Entity target, long expectedTargetId) {
        if (!insideParallel && !deferCombatEffects) {
            reprioApplier.apply(target, expectedTargetId);
            return;
        }
        synchronized (pendingTargetMutations) {
            PendingTargetMutation m = pendingTargetMutationsPool.isEmpty()
                    ? new PendingTargetMutation()
                    : pendingTargetMutationsPool.remove(pendingTargetMutationsPool.size() - 1);
            m.targetId = target.entityId;
            m.kind = PendingTargetMutation.Kind.REPRIORITIZE;
            m.expectedTargetId = expectedTargetId;
            pendingTargetMutations.add(m);
        }
    }

    /** Fallback-cell write. Inline applies the 3 field writes + path-clear; queued path (parallel UPDATE_UNITS or the deferred {@code FIRING} phase — see {@link #deferCombatEffects}) drains in {@link #flushPendingTargetMutations()}. */
    public void applyFallback(Entity target, int fbX, int fbY) {
        if (!insideParallel && !deferCombatEffects) {
            fallbackApplier.apply(target, fbX, fbY);
            return;
        }
        synchronized (pendingTargetMutations) {
            PendingTargetMutation m = pendingTargetMutationsPool.isEmpty()
                    ? new PendingTargetMutation()
                    : pendingTargetMutationsPool.remove(pendingTargetMutationsPool.size() - 1);
            m.targetId = target.entityId;
            m.kind = PendingTargetMutation.Kind.FALLBACK;
            m.fallbackCellX = fbX;
            m.fallbackCellY = fbY;
            pendingTargetMutations.add(m);
        }
    }

    /**
     * Occupancy + destIndex delta. Callers pass {@code Integer.MIN_VALUE} for
     * an old / new coord to mark that half as a no-op. Serial callers apply
     * inline; parallel UPDATE_UNITS callers queue for
     * {@link #flushPendingOccupancyDeltas()}. Deliberately gated on
     * {@link #insideParallel} ONLY — not {@link #deferCombatEffects} — because
     * this drain already ran for the tick by the time {@code FIRING} (the
     * deferral window) executes; see the field doc for the leak this avoids.
     */
    public void applyOccupancyDelta(Entity u, int oldDestX, int oldDestY, int newDestX, int newDestY) {
        if (!insideParallel) {
            occupancyApplier.apply(u, oldDestX, oldDestY, newDestX, newDestY);
            return;
        }
        synchronized (pendingOccupancy) {
            PendingOccupancyDelta d = pendingOccupancyPool.isEmpty()
                    ? new PendingOccupancyDelta()
                    : pendingOccupancyPool.remove(pendingOccupancyPool.size() - 1);
            d.unitId = u.entityId;
            d.oldDestX = oldDestX;
            d.oldDestY = oldDestY;
            d.newDestX = newDestX;
            d.newDestY = newDestY;
            pendingOccupancy.add(d);
        }
    }

    /**
     * Drains the SoA damage queue FIFO through the registered applier. Runs
     * serially in {@code TickProfile.Phase.APPLY_DAMAGE}, after both the
     * parallel UPDATE_UNITS dispatch and the serial {@code FIRING} phase have
     * queued this tick's hits (see {@link #deferCombatEffects}), and before
     * the subsystem ticks that read HP. Behavioral note preserved from the
     * pre-deferral inline-apply era: a doomed target gets one tick of action
     * before the drain kills it (vs the pre-deferral inline path where a
     * doomed later-in-iteration target was already dead when its own update
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
            // Resolve the id rather than holding the unit: null means the target
            // was released between enqueue and this drain (the preceding
            // flushPendingDamage killed it), so we skip it — no isAlive() on a
            // dangling ref.
            Entity target = resolver.apply(m.targetId);
            if (target != null) {
                switch (m.kind) {
                    case REPRIORITIZE:
                        // The expectedTargetId race-check moved registry-side
                        // (writeReprioInline) so this drain no longer reads
                        // target.getTargetId() through the no-arg denseIdx accessor.
                        reprioApplier.apply(target, m.expectedTargetId);
                        break;
                    case FALLBACK:
                        fallbackApplier.apply(target, m.fallbackCellX, m.fallbackCellY);
                        break;
                }
            }
            m.targetId = 0L;
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
            // Resolve the id — this drain runs in APPLY_OCCUPANCY (before any
            // death/release this tick), so a non-null resolve is expected, but
            // guard anyway rather than deref a held ref.
            Entity u = resolver.apply(d.unitId);
            if (u != null) occupancyApplier.apply(u, d.oldDestX, d.oldDestY, d.newDestX, d.newDestY);
            d.unitId = 0L;
            pendingOccupancyPool.add(d);
        }
        pendingOccupancy.clear();
    }
}
