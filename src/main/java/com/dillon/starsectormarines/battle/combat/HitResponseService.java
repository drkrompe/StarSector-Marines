package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.IntSupplier;

/**
 * Per-hit response logic — fallback rolls and target-reprioritization rolls
 * triggered when a unit takes damage. Extracted from BattleSimulation so the
 * sim doesn't own gameplay decision logic.
 *
 * <p>Both methods are called from the parallel UPDATE_UNITS dispatch (via
 * InfantryWeapons / HeavyWeapons / TurretFireService). Thread safety:
 * <ul>
 *   <li>{@link #rollFallbackOnHit} — reads immutable service refs + per-unit
 *       fields; writes go through {@link DamageService#applyFallback} which
 *       handles serial/parallel routing.</li>
 *   <li>{@link #rollReprioritizeOnHit} — CAS on
 *       {@link Unit#lastReprioTickIndex} gates to one roll per (target, tick);
 *       writes go through {@link DamageService#applyReprio}.</li>
 * </ul>
 */
public final class HitResponseService {

    private static final float FALLBACK_CHANCE = 0.25f;
    /** Sim seconds a unit stays in fall-back state once entered. */
    public static final float FALLBACK_DURATION = 3.5f;

    private static final float REPRIORITIZE_BASE_CHANCE = 0.35f;
    private static final float REPRIORITIZE_NO_LOS_CHANCE = 0.85f;

    private static final AtomicIntegerFieldUpdater<Unit> LAST_REPRIO_TICK =
            AtomicIntegerFieldUpdater.newUpdater(Unit.class, "lastReprioTickIndex");

    private final NavigationGrid grid;
    private final UnitRegistry registry;
    private final TacticalScoring tacticalScoring;
    private final DamageService damageService;
    private final IntSupplier tickIndexSupplier;

    public HitResponseService(NavigationGrid grid, UnitRegistry registry,
                              TacticalScoring tacticalScoring,
                              DamageService damageService,
                              IntSupplier tickIndexSupplier) {
        this.grid = grid;
        this.registry = registry;
        this.tacticalScoring = tacticalScoring;
        this.damageService = damageService;
        this.tickIndexSupplier = tickIndexSupplier;
    }

    public void rollFallbackOnHit(Unit target) {
        if (!target.isAlive()) return;
        if (target.getFallbackTimer() > 0f) return;
        if (target instanceof MapTurret) return;
        if (target.squadId != Unit.NO_SQUAD) return;
        if (target.rng.nextFloat() >= FALLBACK_CHANCE) return;
        int[] fallback = tacticalScoring.findFallbackPosition(target);
        if (fallback[0] == target.getCellX() && fallback[1] == target.getCellY()) return;
        damageService.applyFallback(target, fallback[0], fallback[1]);
    }

    public void rollReprioritizeOnHit(Unit target, Unit shooter) {
        if (!target.isAlive()) return;
        boolean qualifies = target.mech != null || target instanceof MapTurret;
        if (!qualifies) return;
        int simTickIndex = tickIndexSupplier.getAsInt();
        int prev = LAST_REPRIO_TICK.get(target);
        if (prev == simTickIndex) return;
        if (!LAST_REPRIO_TICK.compareAndSet(target, prev, simTickIndex)) return;
        long expectedTargetId = target.getTargetId();
        Unit expectedTarget = registry.getOrNull(expectedTargetId);
        if (expectedTarget == null) return;
        if (shooter != null && expectedTarget == shooter) return;
        boolean hasLosToCurrentTarget = TacticalScoring.canSeePair(grid,
                target.getCellX(), target.getCellY(),
                expectedTarget.getCellX(), expectedTarget.getCellY(),
                target.airLosRadius, expectedTarget.airLosRadius);
        float chance = hasLosToCurrentTarget ? REPRIORITIZE_BASE_CHANCE : REPRIORITIZE_NO_LOS_CHANCE;
        if (target.rng.nextFloat() >= chance) return;
        damageService.applyReprio(target, expectedTargetId);
    }
}
