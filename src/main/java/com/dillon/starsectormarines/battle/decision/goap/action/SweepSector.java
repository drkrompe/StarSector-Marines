package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.SquadRouteGoalProvider;
import com.dillon.starsectormarines.battle.squad.FireTeamGroups;
import java.util.Map;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.mech.MechRouteIntent;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.AudibleBearing;
import com.dillon.starsectormarines.battle.squad.Squad;

import java.util.List;

/** Searches an assigned Assault sector while retaining its destination through contact. */
public final class SweepSector extends AbstractZoneAction implements SquadRouteGoalProvider {

    private final int targetX;
    private final int targetY;

    public SweepSector(int targetX, int targetY) {
        super(-1);
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
        // Search must cross an occluding perimeter to find a firing angle.
        // A remembered contact alone is not a usable firing line.
        boolean canFight = squad.contactPicture.primaryEngageableMembers() > 0
                || squad.isUnderFireAtLosThisTick();
        advanceIntoZone(member, squad, sim, moveX, moveY, canFight);
        return ActionStatus.RUNNING;
    }

    @Override public boolean permitsOpportunityFire() { return false; }

    @Override
    public Map<String, List<Long>> assignRoles(Squad squad, BattleView sim, List<Long> candidates) {
        return FireTeamGroups.assignments(FIRE_TEAM, candidates, sim.squad());
    }

    @Override
    public Goal squadRouteGoal(Squad squad, BattleView sim) {
        AudibleBearing bearing = squad.audibleBearing();
        return bearing == null ? new Goal(targetX, targetY) : new Goal(bearing.cellX(), bearing.cellY());
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
