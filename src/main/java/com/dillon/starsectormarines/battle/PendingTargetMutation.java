package com.dillon.starsectormarines.battle;

/**
 * Target-side mutation queued during a tick's UPDATE_UNITS phase and drained
 * serially by {@code BattleSimulation.flushPendingTargetMutations()} in the
 * APPLY_DAMAGE phase. Sibling to {@link PendingDamage}: the shooter's worker
 * runs the gating / chance roll, then enqueues here instead of writing the
 * target's fields directly — which previously raced with the target's own
 * worker reading {@code path} / {@code pathIdx} / {@code target} concurrently.
 *
 * <p>Mutable + pooled (see {@code BattleSimulation.pendingTargetMutationsPool})
 * so the steady-state allocation is zero.
 */
public final class PendingTargetMutation {
    public enum Kind { REPRIORITIZE, FALLBACK }

    public Unit target;
    public Kind kind;
    /** FALLBACK: target cell X computed by the shooter's worker. Unused for REPRIORITIZE. */
    public int fallbackCellX;
    /** FALLBACK: target cell Y computed by the shooter's worker. Unused for REPRIORITIZE. */
    public int fallbackCellY;
}
