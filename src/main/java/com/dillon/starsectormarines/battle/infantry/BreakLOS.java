package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;

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
 * <p>Destination caching reuses the AI_STATE fall-back cell
 * ({@code world.fallbackCellX(id)}/{@code world.fallbackCellY(id)}) — the same
 * columns {@link BreakContact} uses. Re-rolls via the shared
 * {@link TacticalScoring#fallbackDestinationNeedsRefresh} when the cached
 * cell is unset or has become visible to an enemy; otherwise holds the cell
 * and walks toward it. Because the plan step is squad-shared, one member's
 * arrival is not enough to finish it: the action returns
 * {@link ActionStatus#SUCCESS} only after every living squadmate has reached
 * its individually selected hidden or least-exposed fallback cell. This
 * prevents an already-covered member from advancing the plan while exposed
 * squadmates are still crossing the firing lane, while still terminating on
 * open terrain where no fully hidden cell exists.
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
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return COST; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        if (sim.getTacticalScoring().fallbackDestinationNeedsRefresh(member)) {
            int[] dest = sim.getTacticalScoring().findFallbackPosition(member);
            sim.world().setFallbackCell(member, dest[0], dest[1]);
        }

        boolean atDest = sim.movement().atCell(member, sim.world().fallbackCellX(member), sim.world().fallbackCellY(member));
        if (atDest) {
            if (!Paths.isEmpty(sim.world().path(member))) sim.clearPath(member);
            // Arrived at the member's best available fallback (hidden when the
            // map provides one, least-exposed otherwise). Do not advance the
            // shared step until the rest of the squad reaches its picks too.
            return allSquadmatesAtFallback(squad, sim)
                    ? ActionStatus.SUCCESS
                    : ActionStatus.RUNNING;
        }
        if (sim.movement().mayRepath(member)) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member), sim.world().cellY(member),
                    sim.world().fallbackCellX(member), sim.world().fallbackCellY(member),
                    sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
        return ActionStatus.RUNNING;
    }

    /** True when every living member has a valid fallback and has reached it. */
    private static boolean allSquadmatesAtFallback(Squad squad, BattleControl sim) {
        boolean foundMember = false;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long other = sim.liveUnitAt(i);
            if (!sim.squad().hasSquad(other) || sim.squad().squadId(other) != squad.id) continue;
            foundMember = true;
            int fx = sim.world().fallbackCellX(other);
            int fy = sim.world().fallbackCellY(other);
            if (fx < 0 || fy < 0 || !sim.movement().atCell(other, fx, fy)) return false;
        }
        return foundMember;
    }
}
