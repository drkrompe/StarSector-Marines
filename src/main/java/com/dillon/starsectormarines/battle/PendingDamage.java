package com.dillon.starsectormarines.battle;

/**
 * Damage queued during a tick's UPDATE_UNITS phase and drained serially by
 * {@code BattleSimulation.flushPendingDamage()} in the APPLY_DAMAGE phase
 * that runs immediately after. The decoupling exists so the per-unit dispatch
 * has no shared sim mutation in its hot path — prerequisite for parallelizing
 * the dispatch loop. Mutable + pooled so steady-state allocation is zero;
 * see {@code BattleSimulation.pendingDamagePool} for the recycle convention.
 *
 * <p>Per-target counterpart to {@link PendingDetonation} (AoE). Together they
 * keep the "no synchronous damage in updateUnit" rule uniform across direct
 * fire and area effects.
 */
public final class PendingDamage {
    public Unit target;
    public float damage;
    public float vsTurretMult;
    /** Scales the morale drain applied to the target's squad on this hit (1.0 = baseline). See {@code BattleSimulation.applyDamage} for use. */
    public float moraleImpact;
}
