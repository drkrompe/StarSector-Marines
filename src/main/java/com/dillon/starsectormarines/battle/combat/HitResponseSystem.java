package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.VisionService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
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
 * InfantryWeapons / HeavyWeapons / TurretFireSystem). Thread safety:
 * <ul>
 *   <li>{@link #rollFallbackOnHit} — reads immutable service refs + per-unit
 *       fields; writes go through {@link DamageService#applyFallback} which
 *       handles serial/parallel routing.</li>
 *   <li>{@link #rollReprioritizeOnHit} — CAS on
 *       {@link Entity#lastReprioTickIndex} gates to one roll per (target, tick);
 *       writes go through {@link DamageService#applyReprio}.</li>
 * </ul>
 */
public final class HitResponseSystem {

    private static final float FALLBACK_CHANCE = 0.25f;
    /** Sim seconds a unit stays in fall-back state once entered. */
    public static final float FALLBACK_DURATION = 3.5f;

    private static final float REPRIORITIZE_BASE_CHANCE = 0.35f;
    private static final float REPRIORITIZE_NO_LOS_CHANCE = 0.85f;

    private static final AtomicIntegerFieldUpdater<Entity> LAST_REPRIO_TICK =
            AtomicIntegerFieldUpdater.newUpdater(Entity.class, "lastReprioTickIndex");

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

    public void rollFallbackOnHit(Entity target) {
        World world = roster.world();
        if (!roster.isAliveById(target.entityId)) return;
        // Static emplacements (turrets, drone hubs) have no AI_STATE — they don't
        // fall back. This presence gate replaces the old `instanceof MapTurret`
        // check and also (correctly) covers drone hubs, which previously could roll
        // a fall-back they had no behavior to execute. It must precede the
        // fallbackTimer read below, which is fail-loud without AI_STATE.
        if (!world.hasAiState(target.entityId)) return;
        if (world.fallbackTimer(target.entityId) > 0f) return;
        if (target.squadId != Entity.NO_SQUAD) return;
        if (target.rng.nextFloat() >= FALLBACK_CHANCE) return;
        int[] fallback = tacticalScoring.findFallbackPosition(target);
        if (fallback[0] == world.cellX(target.entityId) && fallback[1] == world.cellY(target.entityId)) return;
        damageService.applyFallback(target, fallback[0], fallback[1]);
    }

    public void rollReprioritizeOnHit(Entity target, Entity shooter) {
        World world = roster.world();
        if (!roster.isAliveById(target.entityId)) return;
        boolean qualifies = world.hasMechLoadout(target.entityId) || target instanceof MapTurret;
        if (!qualifies) return;
        int simTickIndex = tickIndexSupplier.getAsInt();
        int prev = LAST_REPRIO_TICK.get(target);
        if (prev == simTickIndex) return;
        if (!LAST_REPRIO_TICK.compareAndSet(target, prev, simTickIndex)) return;
        long expectedTargetId = world.targetId(target.entityId);
        Entity expectedTarget = roster.getOrNull(expectedTargetId);
        if (expectedTarget == null) return;
        if (shooter != null && expectedTarget == shooter) return;
        VisionService vision = roster.vision();
        boolean hasLosToCurrentTarget = TacticalScoring.canSeePair(grid,
                world.cellX(target.entityId), world.cellY(target.entityId),
                world.cellX(expectedTarget.entityId), world.cellY(expectedTarget.entityId),
                vision.airLosRadius(target.entityId), vision.airLosRadius(expectedTarget.entityId));
        float chance = hasLosToCurrentTarget ? REPRIORITIZE_BASE_CHANCE : REPRIORITIZE_NO_LOS_CHANCE;
        if (target.rng.nextFloat() >= chance) return;
        damageService.applyReprio(target, expectedTargetId);
    }
}
