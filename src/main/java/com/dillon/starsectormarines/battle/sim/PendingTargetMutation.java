package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.unit.Unit;

/**
 * Target-side mutation queued during a tick's UPDATE_UNITS phase and drained
 * serially by {@code BattleSimulation.flushPendingTargetMutations()} in the
 * APPLY_DAMAGE phase. Sibling to the SoA damage queue on
 * {@code DamageService}: the shooter's worker runs the gating / chance roll,
 * then enqueues here instead of writing the target's fields directly — which
 * previously raced with the target's own worker reading {@code path} /
 * {@code pathIdx} / {@code target} concurrently.
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
    /**
     * REPRIORITIZE: snapshot of {@code target.getTargetId()} captured by the
     * shooter's worker at enqueue time. The drain only clears
     * {@code target.getTargetId()} if it still equals this snapshot — protects
     * against the target's own worker having re-targeted (e.g. picked a closer
     * flanker) during the same parallel UPDATE_UNITS phase. Without this, the
     * drain clobbers a valid post-hit re-target with null. {@code 0L} encodes
     * the "had no target at enqueue" case. Unused for FALLBACK.
     */
    public long expectedTargetId;
}
