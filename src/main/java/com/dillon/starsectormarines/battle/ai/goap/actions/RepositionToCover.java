package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.goap.Action;
import com.dillon.starsectormarines.battle.ai.goap.ActionStatus;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

/**
 * Story G — short-range reposition to refresh cover or open a better firing
 * angle. Called inline by {@link EngagePosture} after firing, not as a
 * standalone planner step: at the per-squad 2-second replan cadence, a real
 * planner step couldn't fire between shots, and a sub-action lets the
 * inter-burst micro-movement happen tick-by-tick the way the story wants
 * ("members shift one or two cells between bursts").
 *
 * <p>Cooldown-gated via {@link Unit#repositionCooldown}: when the per-unit
 * timer is ready, look for a cover cell that's strictly better than current
 * (cover-preferred picker won't pick a downgrade), and only path there if
 * one exists. A setup machine gunner in heavy cover whose current cell
 * already wins the cover-preferred search no-ops out — they stay put, the
 * cooldown doesn't restart, the next shot tries again. That's the
 * "cozy in heavy cover" behavior: directional cover plus the cover-preserve
 * filter together mean the gunner stays parked while exposed marines do
 * the moving.
 *
 * <p>Implements {@link Action} so it's testable in isolation, but is
 * <em>not</em> registered in {@code GoapInfantryBehavior.INFANTRY_ACTIONS} —
 * the planner never sees it; {@link EngagePosture} calls
 * {@link #tryReposition} directly.
 */
public final class RepositionToCover implements Action {

    public static final RepositionToCover INSTANCE = new RepositionToCover();

    /**
     * Sim-seconds between successful repositions per unit. Set on
     * {@link Unit#repositionCooldown} when a move actually fires; gates the
     * next attempt. Decorrelated across squadmates because each member's
     * cooldown only resets when <em>their</em> shot lands the move — so the
     * squad's repositions naturally stagger.
     */
    public static final float COOLDOWN_SECONDS = 1.5f;

    private RepositionToCover() {}

    @Override public String name() { return "RepositionToCover"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY.with(Predicate.CAN_REPOSITION, true); }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    /**
     * Inline-from-EngagePosture entry point. Returns true when the member
     * actually starts repositioning (and the cooldown gets stamped on them);
     * false when ineligible (cooldown not ready, no target, no better cover
     * available, or already in best cover). The boolean is informational —
     * callers don't have to act on it, but a debug overlay can use it to
     * read "this tick: shifted vs. held."
     */
    public static boolean tryReposition(Unit member, BattleSimulation sim) {
        if (member.repositionCooldown > 0f) return false;
        if (member.target == null || !member.target.isAlive()) return false;
        int[] dest = TacticalScoring.findFiringPositionCoverPreferred(
                member, member.target, sim, member.cellX, member.cellY);
        if (dest == null) return false;
        if (dest[0] == member.cellX && dest[1] == member.cellY) return false;
        sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                member.cellX, member.cellY, dest[0], dest[1], sim.getOccupancyMap()));
        member.repositionCooldown = COOLDOWN_SECONDS;
        return true;
    }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        // Standalone-action path — exists for testability and for future
        // callers that might want to schedule reposition as a planner step.
        // Inline path through tryReposition is the production hot path.
        tryReposition(member, sim);
        return ActionStatus.SUCCESS;
    }
}
