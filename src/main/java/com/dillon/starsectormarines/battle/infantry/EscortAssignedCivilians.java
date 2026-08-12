package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;

import java.util.List;

/** Moving rally posture used by the rescue commander before and during evacuation. */
public final class EscortAssignedCivilians implements Action {

    public static final EscortAssignedCivilians INSTANCE =
            new EscortAssignedCivilians();
    /** Marines spread around the payload rather than trying to occupy its cell. */
    static final int STANDOFF_RADIUS = 3;

    private EscortAssignedCivilians() {}

    @Override public String name() { return "EscortCivilians"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState state, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        if (assignment == null || assignment.kind() != AssignmentKind.ESCORT
                || assignment.targetCellX() < 0
                || assignment.targetCellY() < 0) {
            return ActionStatus.FAILURE;
        }

        int tx = assignment.targetCellX();
        int ty = assignment.targetCellY();
        int dx = sim.world().cellX(member) - tx;
        int dy = sim.world().cellY(member) - ty;
        if (dx * dx + dy * dy <= STANDOFF_RADIUS * STANDOFF_RADIUS) {
            PatrolMotion.hold(member, sim);
            PatrolMotion.fireIfAble(member, sim);
            return ActionStatus.RUNNING;
        }

        int[] rally = nearestOpenRallyCell(member, tx, ty, sim);
        if (rally == null) {
            PatrolMotion.hold(member, sim);
            PatrolMotion.fireIfAble(member, sim);
            return ActionStatus.RUNNING;
        }
        int[] path = sim.movement().path(member);
        if (Paths.destX(path) != rally[0] || Paths.destY(path) != rally[1]) {
            sim.clearPath(member);
        }
        PatrolMotion.moveToward(member, sim, rally[0], rally[1]);
        PatrolMotion.fireIfAble(member, sim);
        return ActionStatus.RUNNING;
    }

    @Override
    public List<int[]> highlightCells(Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        if (assignment == null || assignment.kind() != AssignmentKind.ESCORT) {
            return List.of();
        }
        return List.of(new int[]{assignment.targetCellX(), assignment.targetCellY()});
    }

    private static int[] nearestOpenRallyCell(long member, int tx, int ty,
                                               BattleView sim) {
        int mx = sim.world().cellX(member);
        int my = sim.world().cellY(member);
        byte[] occupied = sim.getOccupancyMap();
        int bestX = -1;
        int bestY = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = ty - STANDOFF_RADIUS; y <= ty + STANDOFF_RADIUS; y++) {
            for (int x = tx - STANDOFF_RADIUS; x <= tx + STANDOFF_RADIUS; x++) {
                int ex = x - tx;
                int ey = y - ty;
                if (ex * ex + ey * ey > STANDOFF_RADIUS * STANDOFF_RADIUS) continue;
                if (!sim.getGrid().inBounds(x, y) || !sim.getGrid().isWalkable(x, y)) continue;
                if ((x != mx || y != my)
                        && occupied[sim.getGrid().index(x, y)] != 0) continue;
                int distance = Math.abs(x - mx) + Math.abs(y - my);
                if (distance < bestDistance
                        || (distance == bestDistance
                        && (y < bestY || (y == bestY && x < bestX)))) {
                    bestX = x;
                    bestY = y;
                    bestDistance = distance;
                }
            }
        }
        return bestX >= 0 ? new int[]{bestX, bestY} : null;
    }
}
