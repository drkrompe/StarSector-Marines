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
 * <b>Squad posture: duck around a corner.</b> Story A re-trigger — when a
 * squadmate is taking fire from an enemy with LOS back to the shot's origin
 * ({@link Predicate#UNDER_FIRE_AT_LOS}), the squad breaks the firing lane by
 * pathing each exposed member to a hidden cell via {@link TacticalScoring#findFallbackPosition}.
 *
 * <p>Cost {@code 2.0} — cheaper than {@link EngagePosture}'s 1.0 in the
 * planner's regression math when the goal predicate is {@link Predicate#UNDER_FIRE_AT_LOS},
 * since this action's effect ({@code UNDER_FIRE_AT_LOS=false}) directly
 * satisfies the desired-state slot and Engage doesn't. Keeping it modest
 * stops the planner from prepending BreakLOS in unrelated plans.
 *
 * <p>Destination caching reuses {@link Unit#fallbackCellX}/{@link Unit#fallbackCellY}
 * — same fields {@link BreakContact} uses. Each call only repicks when the
 * cell is unset; once arrived the member holds (returns {@link ActionStatus#SUCCESS}
 * so the plan advances and the next replan can choose Overwatch / Engage from
 * the now-safe position).
 *
 * <p>Emitted from the planner only — uses backward-chaining preconditions/effects
 * (not a customPlan action like BreakContact).
 */
public final class BreakLOS implements Action {

    public static final BreakLOS INSTANCE = new BreakLOS();

    private static final float COST = 2.0f;

    private static final WorldState PRE = WorldState.EMPTY
            .with(Predicate.UNDER_FIRE_AT_LOS, true);
    private static final WorldState EFF = WorldState.EMPTY
            .with(Predicate.UNDER_FIRE_AT_LOS, false);

    private BreakLOS() {}

    @Override public String name() { return "BreakLOS"; }
    @Override public WorldState preconditions() { return PRE; }
    @Override public WorldState effects() { return EFF; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return COST; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        if (member.fallbackCellX < 0 || member.fallbackCellY < 0) {
            int[] dest = TacticalScoring.findFallbackPosition(member, sim);
            member.fallbackCellX = dest[0];
            member.fallbackCellY = dest[1];
        }

        boolean atDest = member.cellX == member.fallbackCellX
                      && member.cellY == member.fallbackCellY;
        if (atDest) {
            if (!member.pathEmpty()) sim.clearPath(member);
            member.moveProgress = 0f;
            member.renderX = member.cellX;
            member.renderY = member.cellY;
            // Arrived at a hidden cell. The predicate-flip ("no longer in LoS
            // of the threat") is implicit: findFallbackPosition only picks
            // cells hidden from every enemy, so by definition no enemy still
            // has LOS to the member's current cell. Plan advances; the next
            // replan picks Overwatch or Engage from this fresh position.
            return ActionStatus.SUCCESS;
        }
        if (member.moveProgress == 0f) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    member.cellX, member.cellY,
                    member.fallbackCellX, member.fallbackCellY,
                    sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
        return ActionStatus.RUNNING;
    }
}
