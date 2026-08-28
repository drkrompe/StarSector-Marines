package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.DefendTrack;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

import java.util.List;

/** Moves a squad onto an authored objective interaction cell and holds it. */
public final class ServiceAssignedObjectiveGoal implements Goal {
    public static final ServiceAssignedObjectiveGoal INSTANCE =
            new ServiceAssignedObjectiveGoal();

    private ServiceAssignedObjectiveGoal() { }

    @Override public String name() { return "ServiceAssignedObjective"; }
    @Override public Priority priority() { return Priority.MISSION; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (!valid(assignment) || state.get(Predicate.MORALE_BROKEN)) return 0f;
        return 0.95f;
    }

    @Override public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        return planFor(squad, AssignmentKind.RUSH_OBJECTIVE);
    }

    static SquadPlan planFor(Squad squad, AssignmentKind kind) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null || assignment.kind() != kind
                || assignment.targetCellX() < 0
                || assignment.targetCellY() < 0) return null;
        int x = assignment.targetCellX();
        int y = assignment.targetCellY();
        SquadPlan current = squad.currentPlan;
        if (current != null && !current.isComplete()) {
            SquadPlan.Step step = current.currentStep();
            if (step != null && step.action instanceof DefendTrack action
                    && action.assignmentKind() == kind
                    && action.targetX() == x && action.targetY() == y) {
                return current;
            }
        }
        return new SquadPlan(List.of(new SquadPlan.Step(
                new DefendTrack(kind, x, y))));
    }

    private static boolean valid(ObjectiveAssignment assignment) {
        return assignment != null
                && assignment.kind() == AssignmentKind.RUSH_OBJECTIVE
                && assignment.objectiveId() >= 0
                && assignment.targetCellX() >= 0
                && assignment.targetCellY() >= 0;
    }
}
