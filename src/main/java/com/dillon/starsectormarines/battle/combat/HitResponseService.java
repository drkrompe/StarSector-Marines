package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.component.ComponentStore;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Entity;
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
 *       {@link Entity#lastReprioTickIndex} gates to one roll per (target, tick);
 *       writes go through {@link DamageService#applyReprio}.</li>
 * </ul>
 */
public final class HitResponseService {

    private static final float FALLBACK_CHANCE = 0.25f;
    /** Sim seconds a unit stays in fall-back state once entered. */
    public static final float FALLBACK_DURATION = 3.5f;

    private static final float REPRIORITIZE_BASE_CHANCE = 0.35f;
    private static final float REPRIORITIZE_NO_LOS_CHANCE = 0.85f;

    private static final AtomicIntegerFieldUpdater<Entity> LAST_REPRIO_TICK =
            AtomicIntegerFieldUpdater.newUpdater(Entity.class, "lastReprioTickIndex");

    private final NavigationGrid grid;
    private final UnitRegistry registry;
    private final TacticalScoring tacticalScoring;
    private final DamageService damageService;
    private final IntSupplier tickIndexSupplier;
    private final ComponentStore<MechLoadoutComponent> mechLoadouts;

    public HitResponseService(NavigationGrid grid, UnitRegistry registry,
                              TacticalScoring tacticalScoring,
                              DamageService damageService,
                              IntSupplier tickIndexSupplier,
                              ComponentStore<MechLoadoutComponent> mechLoadouts) {
        this.grid = grid;
        this.registry = registry;
        this.tacticalScoring = tacticalScoring;
        this.damageService = damageService;
        this.tickIndexSupplier = tickIndexSupplier;
        this.mechLoadouts = mechLoadouts;
    }

    public void rollFallbackOnHit(Entity target) {
        if (!registry.isAliveById(target.entityId)) return;
        // Static emplacements (turrets, drone hubs) have no AI_STATE — they don't
        // fall back. This presence gate replaces the old `instanceof MapTurret`
        // check and also (correctly) covers drone hubs, which previously could roll
        // a fall-back they had no behavior to execute. It must precede the
        // fallbackTimer read below, which is fail-loud without AI_STATE.
        if (!registry.hasAiState(target.entityId)) return;
        if (registry.fallbackTimerById(target.entityId) > 0f) return;
        if (target.squadId != Entity.NO_SQUAD) return;
        if (target.rng.nextFloat() >= FALLBACK_CHANCE) return;
        int[] fallback = tacticalScoring.findFallbackPosition(target);
        if (fallback[0] == registry.cellXById(target.entityId) && fallback[1] == registry.cellYById(target.entityId)) return;
        damageService.applyFallback(target, fallback[0], fallback[1]);
    }

    public void rollReprioritizeOnHit(Entity target, Entity shooter) {
        if (!registry.isAliveById(target.entityId)) return;
        boolean qualifies = mechLoadouts.has(target.entityId) || target instanceof MapTurret;
        if (!qualifies) return;
        int simTickIndex = tickIndexSupplier.getAsInt();
        int prev = LAST_REPRIO_TICK.get(target);
        if (prev == simTickIndex) return;
        if (!LAST_REPRIO_TICK.compareAndSet(target, prev, simTickIndex)) return;
        long expectedTargetId = registry.targetIdById(target.entityId);
        Entity expectedTarget = registry.getOrNull(expectedTargetId);
        if (expectedTarget == null) return;
        if (shooter != null && expectedTarget == shooter) return;
        boolean hasLosToCurrentTarget = TacticalScoring.canSeePair(grid,
                registry.cellXById(target.entityId), registry.cellYById(target.entityId),
                registry.cellXById(expectedTarget.entityId), registry.cellYById(expectedTarget.entityId),
                target.airLosRadius, expectedTarget.airLosRadius);
        float chance = hasLosToCurrentTarget ? REPRIORITIZE_BASE_CHANCE : REPRIORITIZE_NO_LOS_CHANCE;
        if (target.rng.nextFloat() >= chance) return;
        damageService.applyReprio(target, expectedTargetId);
    }
}
