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
    /** The lead squad must enter the shelter's physical relief trigger. */
    static final int RELIEF_RADIUS = 2;
    /** The remaining force forms a broad perimeter with room for debug rosters. */
    static final int ESCORT_RADIUS = 6;

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
        int standoffRadius = standoffRadius(squad, sim);
        int dx = sim.world().cellX(member) - tx;
        int dy = sim.world().cellY(member) - ty;
        if (dx * dx + dy * dy <= standoffRadius * standoffRadius) {
            PatrolMotion.hold(member, sim);
            PatrolMotion.fireIfAble(member, sim);
            return ActionStatus.RUNNING;
        }

        int[] rally = retainedRallyCell(member, tx, ty, standoffRadius, sim);
        if (rally == null) {
            rally = nearestOpenRallyCell(member, tx, ty, standoffRadius, sim);
        }
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

    /**
     * Keeps an in-flight destination while it remains inside the current
     * perimeter. Destination cells are occupancy claims, so resampling them as
     * though they belonged to another unit resets movement every tick.
     */
    private static int[] retainedRallyCell(long member, int tx, int ty,
                                            int radius, BattleView sim) {
        int[] path = sim.movement().path(member);
        if (sim.movement().pathIdx(member) >= Paths.cellCount(path)) return null;
        int x = Paths.destX(path);
        int y = Paths.destY(path);
        int dx = x - tx;
        int dy = y - ty;
        if (dx * dx + dy * dy > radius * radius) return null;
        return sim.getGrid().inBounds(x, y) && sim.getGrid().isWalkable(x, y)
                ? new int[]{x, y} : null;
    }

    static int standoffRadius(Squad squad, BattleView sim) {
        if (squad.rescuePickupGuard) return ESCORT_RADIUS;
        if (sim.isCivilianEvacuationTriggered()) return ESCORT_RADIUS;
        int leadSquadId = Integer.MAX_VALUE;
        for (Squad candidate : sim.getSquads()) {
            if (candidate.faction != squad.faction || candidate.aliveMembers <= 0
                    || candidate.rescuePickupGuard) continue;
            leadSquadId = Math.min(leadSquadId, candidate.id);
        }
        return squad.id == leadSquadId ? RELIEF_RADIUS : ESCORT_RADIUS;
    }

    private static int[] nearestOpenRallyCell(long member, int tx, int ty,
                                               int radius, BattleView sim) {
        int mx = sim.world().cellX(member);
        int my = sim.world().cellY(member);
        byte[] occupied = sim.getOccupancyMap();
        int bestX = -1;
        int bestY = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = ty - radius; y <= ty + radius; y++) {
            for (int x = tx - radius; x <= tx + radius; x++) {
                int ex = x - tx;
                int ey = y - ty;
                if (ex * ex + ey * ey > radius * radius) continue;
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
