package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.DefendTrack;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

import java.util.List;

/** Shared infantry/mech goal for an attacker-side Conquest lane rally. */
public final class AdvanceAssignedTrackGoal implements Goal {
    public static final AdvanceAssignedTrackGoal INSTANCE =
            new AdvanceAssignedTrackGoal();

    private AdvanceAssignedTrackGoal() {}

    @Override public String name() { return "AdvanceAssignedTrack"; }
    @Override public Priority priority() { return Priority.MISSION; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null
                || assignment.kind() != AssignmentKind.ADVANCE_TRACK) return 0f;
        if (assignment.targetCellX() < 0 || assignment.targetCellY() < 0) return 0f;
        if (state.get(Predicate.MORALE_BROKEN)
                || state.get(Predicate.HAS_TARGET)) return 0f;
        return 0.85f;
    }

    @Override public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null
                || assignment.kind() != AssignmentKind.ADVANCE_TRACK) return null;
        int x = assignment.targetCellX();
        int y = assignment.targetCellY();
        SquadPlan current = squad.currentPlan;
        if (current != null && !current.isComplete()) {
            SquadPlan.Step step = current.currentStep();
            if (step != null && step.action instanceof DefendTrack advance
                    && advance.assignmentKind() == AssignmentKind.ADVANCE_TRACK
                    && advance.targetX() == x && advance.targetY() == y) {
                return current;
            }
        }
        return new SquadPlan(List.of(new SquadPlan.Step(
                new DefendTrack(AssignmentKind.ADVANCE_TRACK, x, y))));
    }
}
