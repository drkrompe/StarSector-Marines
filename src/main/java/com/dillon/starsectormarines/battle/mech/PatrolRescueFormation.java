package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.infantry.PatrolMotion;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;

import java.util.ArrayList;
import java.util.List;

/** Cycles the rescue mech through the LZ center and five militia anchors. */
public final class PatrolRescueFormation implements Action {

    public static final PatrolRescueFormation INSTANCE =
            new PatrolRescueFormation();

    private final PatrolMotion.WaypointSource waypointSource =
            this::nextWaypoint;

    private PatrolRescueFormation() {}

    @Override public String name() { return "PatrolRescueFormation"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState state, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        ActionStatus status = PatrolMotion.advance(member, squad, sim,
                waypointSource, false);
        fireFromPatrol(member, sim);
        return status;
    }

    @Override
    public List<int[]> highlightCells(Squad squad, BattleView sim) {
        List<int[]> result = new ArrayList<>();
        ObjectiveAssignment assignment = squad.assignedObjective;
        if (assignment != null && assignment.targetCellX() >= 0
                && assignment.targetCellY() >= 0) {
            result.add(new int[]{assignment.targetCellX(),
                    assignment.targetCellY()});
        }
        if (squad.rescuePatrolCells == null) return result;
        for (int i = 0; i + 1 < squad.rescuePatrolCells.length; i += 2) {
            result.add(new int[]{squad.rescuePatrolCells[i],
                    squad.rescuePatrolCells[i + 1]});
        }
        return result;
    }

    private int[] nextWaypoint(long member, Squad squad, BattleView sim) {
        int[] cells = squad.rescuePatrolCells;
        if (cells == null || cells.length < 2) return null;
        if (squad.rescuePatrolIndex < 0) {
            ObjectiveAssignment assignment = squad.assignedObjective;
            squad.rescuePatrolIndex = 0;
            if (assignment != null && assignment.targetCellX() >= 0
                    && assignment.targetCellY() >= 0) {
                return new int[]{assignment.targetCellX(),
                        assignment.targetCellY()};
            }
        }
        int count = cells.length / 2;
        int index = Math.floorMod(squad.rescuePatrolIndex, count);
        squad.rescuePatrolIndex = (index + 1) % count;
        return new int[]{cells[index * 2], cells[index * 2 + 1]};
    }

    /** Fires every installed track without replacing the authored LZ route. */
    private static void fireFromPatrol(long member, BattleControl sim) {
        long target = MechTargeting.refreshTarget(member, sim);
        sim.world().setTargetId(member, target);
        if (target == 0L) return;
        MechLoadoutComponent loadout = sim.world().mechLoadout(member);
        if (loadout == null) return;
        float distance = TacticalScoring.cellDistance(
                sim.world().x(member), sim.world().y(member),
                sim.world().x(target), sim.world().y(target));
        if (distance > sim.world().attackRange(member)) return;
        boolean visible = sim.getGrid().hasLineOfSight(
                sim.world().cellX(member), sim.world().cellY(member),
                sim.world().cellX(target), sim.world().cellY(target));
        MechCombatantBehavior.tryFireMechWeapons(member, loadout, target,
                distance, sim, visible);
    }
}
