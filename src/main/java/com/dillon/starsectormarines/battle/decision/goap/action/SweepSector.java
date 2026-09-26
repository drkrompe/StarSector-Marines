package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;
import com.dillon.starsectormarines.battle.infantry.PatrolMotion;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.mech.MechRouteIntent;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.AudibleBearing;
import com.dillon.starsectormarines.battle.squad.Squad;

import java.util.List;

/** Walks an Assault squad through its assigned search cell until contact. */
public final class SweepSector implements Action {

    private final int targetX;
    private final int targetY;

    public SweepSector(int targetX, int targetY) {
        this.targetX = targetX;
        this.targetY = targetY;
    }

    @Override public String name() { return "SweepSector"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState state, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null || assignment.kind() != AssignmentKind.SWEEP_SECTOR
                || assignment.targetCellX() != targetX || assignment.targetCellY() != targetY) {
            clearSquadPaths(squad, sim);
            return ActionStatus.FAILURE;
        }
        if (WorldStateBuilder.hasActionableContact(squad, sim)) {
            clearSquadPaths(squad, sim);
            return ActionStatus.FAILURE;
        }

        int moveX = targetX;
        int moveY = targetY;
        AudibleBearing bearing = squad.audibleBearing();
        if (bearing != null) {
            moveX = bearing.cellX();
            moveY = bearing.cellY();
        }

        if (sim.world().hasMechLoadout(member)) {
            MechRouteIntent.forMember(member, SweepSector.class,
                    MechRouteIntent.cellKey(moveX, moveY), sim).moveToward(member, moveX, moveY, sim);
            return ActionStatus.RUNNING;
        }
        int[] path = sim.world().path(member);
        int pathIdx = sim.world().pathIdx(member);
        boolean stale = !Paths.isEmpty(path)
                && (Paths.destX(path) != moveX || Paths.destY(path) != moveY);
        if (stale) {
            sim.clearPath(member);
            path = sim.world().path(member);
            pathIdx = sim.world().pathIdx(member);
        }
        if (sim.movement().mayRepath(member) && pathIdx >= Paths.cellCount(path)) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member), sim.world().cellY(member),
                    moveX, moveY, sim.getOccupancyMap()));
            path = sim.world().path(member);
            pathIdx = sim.world().pathIdx(member);
        }
        if (pathIdx < Paths.cellCount(path)) sim.advanceMovement(member);
        else PatrolMotion.hold(member, sim);
        return ActionStatus.RUNNING;
    }

    private static void clearSquadPaths(Squad squad, BattleControl sim) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.squad().hasSquad(unit)
                    && sim.squad().squadId(unit) == squad.id) {
                sim.clearPath(unit);
            }
        }
    }

    @Override
    public List<int[]> highlightCells(Squad squad, BattleView sim) {
        return List.of(new int[]{targetX, targetY});
    }

    public int targetX() { return targetX; }
    public int targetY() { return targetY; }
}
