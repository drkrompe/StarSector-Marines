package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.VisionService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitRole;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

/**
 * Per-hit response logic — fallback rolls and target-reprioritization rolls
 * triggered when a unit takes damage. Extracted from BattleSimulation so the
 * sim doesn't own gameplay decision logic.
 *
 * <p>A stateless per-hit <b>System</b> — it owns no state (every field is an
 * injected collaborator), reading unit/world state and routing writes through
 * {@link DamageService}. Named {@code *System}, not {@code *Service}, under the
 * Service(data-owner)/System(processor) convention — see
 * {@code roadmap/ecs-migration/stories/entity-field-migration.md}.
 *
 * <p>Both methods are called from the parallel UPDATE_UNITS dispatch (via
 * InfantryWeapons / HeavyWeapons / TurretFireSystem for the postures/tracks
 * that still fire inline) AND from the serial phases that fire off the
 * deferred/continuation paths — {@code battle.combat.FiringSystem}'s FIRING
 * phase (fire-intent postures, deferred alongside damage — see
 * {@link DamageService}) and the InfantryWeapons/HeavyWeapons per-tick burst
 * continuations (INFANTRY_TICK / HEAVY_TICK). Thread safety:
 * <ul>
 *   <li>{@link #rollFallbackOnHit} — reads immutable service refs + per-unit
 *       fields; writes go through {@link DamageService#applyFallback} which
 *       handles serial/parallel/deferred routing.</li>
 *   <li>{@link #rollReprioritizeOnHit} — an atomic per-target claim in
 *       {@link #lastReprioTickByTarget} gates to one roll per (target, tick);
 *       writes go through {@link DamageService#applyReprio}.</li>
 * </ul>
 *
 * <p>The reprio gate is <b>transient per-tick coordination</b>, not durable unit
 * state — its only test is "has this target already been rolled <em>this</em> tick?",
 * so it lives here on the System (keyed by target entity id) rather than as an
 * {@code Entity} field. This closed the last per-unit heap field on the "entity = id"
 * migration (slice 7b).
 */
public final class HitResponseSystem {

    private static final float FALLBACK_CHANCE = 0.25f;
    /** Sim seconds a unit stays in fall-back state once entered. */
    public static final float FALLBACK_DURATION = 3.5f;

    private static final float REPRIORITIZE_BASE_CHANCE = 0.35f;
    private static final float REPRIORITIZE_NO_LOS_CHANCE = 0.85f;

    /**
     * Per-target last-reprio-roll tick — the off-entity home of the reprio gate. Maps a
     * target's entity id to the sim-tick index it was last reprio-rolled on, so the
     * parallel workers can atomically claim "the one roll for this target this tick"
     * ({@link #rollReprioritizeOnHit}). Grows to one entry per distinct mech/turret ever
     * hit (a handful — mechs + turrets are the only qualifying targets); the map is
     * per-battle and dies with the ephemeral sim, so no reset is needed.
     */
    private final ConcurrentHashMap<Long, Integer> lastReprioTickByTarget = new ConcurrentHashMap<>();

    private final NavigationGrid grid;
    private final UnitRosterService roster;
    private final TacticalScoring tacticalScoring;
    private final DamageService damageService;
    private final IntSupplier tickIndexSupplier;

    public HitResponseSystem(NavigationGrid grid, UnitRosterService roster,
                             TacticalScoring tacticalScoring,
                             DamageService damageService,
                             IntSupplier tickIndexSupplier) {
        this.grid = grid;
        this.roster = roster;
        this.tacticalScoring = tacticalScoring;
        this.damageService = damageService;
        this.tickIndexSupplier = tickIndexSupplier;
    }

    public void rollFallbackOnHit(long target) {
        World world = roster.world();
        if (!roster.isAliveById(target)) return;
        // Static emplacements (turrets, drone hubs) have no AI_STATE — they don't
        // fall back. This presence gate replaces the old per-subclass instanceof
        // check and also (correctly) covers drone hubs, which previously could roll
        // a fall-back they had no behavior to execute. It must precede the
        // fallbackTimer read below, which is fail-loud without AI_STATE.
        if (!world.hasAiState(target)) return;
        // Swarm pressure is intentionally implacable: incoming fire must not
        // replace its contact pursuit with the generic fear/fallback state.
        if (roster.role().role(target) == UnitRole.SWARM_PRESSURE) return;
        if (world.fallbackTimer(target) > 0f) return;
        if (roster.squad().hasSquad(target)) return;
        if (ThreadLocalRandom.current().nextFloat() >= FALLBACK_CHANCE) return;
        int[] fallback = tacticalScoring.findFallbackPosition(target);
        if (roster.movement().atCell(target, fallback[0], fallback[1])) return;
        damageService.applyFallback(target, fallback[0], fallback[1]);
    }

    public void rollReprioritizeOnHit(long target, long shooter) {
        World world = roster.world();
        if (!roster.isAliveById(target)) return;
        boolean qualifies = world.hasMechLoadout(target) || roster.identity().type(target).isTurret();
        if (!qualifies) return;
        int simTickIndex = tickIndexSupplier.getAsInt();
        // One reprio roll per (target, tick) across the parallel workers. Fast-path the
        // common "already rolled this tick" case with a plain get, then atomically claim
        // the roll via compute() — it wins iff it transitions this target's last-rolled
        // tick to the current tick (the off-entity replacement for the old
        // AtomicIntegerFieldUpdater CAS on Entity.lastReprioTickIndex).
        Integer prevTick = lastReprioTickByTarget.get(target);
        if (prevTick != null && prevTick == simTickIndex) return;
        boolean[] claimed = {false};
        lastReprioTickByTarget.compute(target, (k, prev) -> {
            if (prev != null && prev == simTickIndex) return prev;   // another worker already claimed it
            claimed[0] = true;
            return simTickIndex;
        });
        if (!claimed[0]) return;
        long expectedTargetId = world.targetId(target);
        if (!roster.isLive(expectedTargetId)) return;
        if (shooter != 0L && expectedTargetId == shooter) return;
        VisionService vision = roster.vision();
        boolean hasLosToCurrentTarget = TacticalScoring.canSeePair(grid,
                world.cellX(target), world.cellY(target),
                world.cellX(expectedTargetId), world.cellY(expectedTargetId),
                vision.airLosRadius(target), vision.airLosRadius(expectedTargetId));
        float chance = hasLosToCurrentTarget ? REPRIORITIZE_BASE_CHANCE : REPRIORITIZE_NO_LOS_CHANCE;
        if (ThreadLocalRandom.current().nextFloat() >= chance) return;
        damageService.applyReprio(target, expectedTargetId);
    }
}
