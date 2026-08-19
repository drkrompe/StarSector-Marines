package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;

/**
 * Perpetual last-stand posture. Returns a displaced survivor to its authored
 * post, then clears movement. It never pursues; the infantry dispatcher's
 * opportunity-fire pass supplies legal fire without replacing this movement
 * objective.
 */
public final class LastStandHold implements Action {

    public static final LastStandHold INSTANCE = new LastStandHold();

    private LastStandHold() {}

    @Override public String name() { return "LastStandHold"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState state, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        if (!sim.home().hasHome(member)) {
            sim.clearPath(member);
            return ActionStatus.RUNNING;
        }

        int homeX = sim.home().homeCellX(member);
        int homeY = sim.home().homeCellY(member);
        if (sim.movement().atCell(member, homeX, homeY)) {
            sim.clearPath(member);
            return ActionStatus.RUNNING;
        }

        int[] path = sim.world().path(member);
        int pathIdx = sim.world().pathIdx(member);
        boolean staleDestination = Paths.destX(path) != homeX || Paths.destY(path) != homeY;
        if (staleDestination) {
            sim.clearPath(member);
            path = sim.world().path(member);
            pathIdx = sim.world().pathIdx(member);
        }
        if (sim.movement().mayRepath(member) && pathIdx >= Paths.cellCount(path)) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member), sim.world().cellY(member),
                    homeX, homeY, sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
        return ActionStatus.RUNNING;
    }
}
