package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.DefendTrack;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

import java.util.List;

/** Shared infantry/mech mission goal for a coarse Assault defense area. */
public final class DefendAssignedAreaGoal implements Goal {
    public static final DefendAssignedAreaGoal INSTANCE =
            new DefendAssignedAreaGoal();

    private DefendAssignedAreaGoal() { }

    @Override public String name() { return "DefendAssignedArea"; }
    @Override public Priority priority() { return Priority.MISSION; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null || assignment.kind() != AssignmentKind.DEFEND_AREA) {
            return 0f;
        }
        if (assignment.targetCellX() < 0 || assignment.targetCellY() < 0) return 0f;
        if (state.get(Predicate.MORALE_BROKEN) || state.get(Predicate.HAS_TARGET)) {
            return 0f;
        }
        return 0.86f;
    }

    @Override public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null || assignment.kind() != AssignmentKind.DEFEND_AREA) {
            return null;
        }
        int x = assignment.targetCellX();
        int y = assignment.targetCellY();
        SquadPlan current = squad.currentPlan;
        if (current != null && !current.isComplete()) {
            SquadPlan.Step step = current.currentStep();
            if (step != null && step.action instanceof DefendTrack defend
                    && defend.assignmentKind() == AssignmentKind.DEFEND_AREA
                    && defend.targetX() == x && defend.targetY() == y) {
                return current;
            }
        }
        return new SquadPlan(List.of(new SquadPlan.Step(
                new DefendTrack(AssignmentKind.DEFEND_AREA, x, y))));
    }
}
