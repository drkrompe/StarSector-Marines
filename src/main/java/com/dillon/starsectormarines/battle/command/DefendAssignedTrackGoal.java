package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.DefendTrack;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

import java.util.List;

/** Shared infantry/mech mission goal for a coarse Conquest defensive rally. */
public final class DefendAssignedTrackGoal implements Goal {
    public static final DefendAssignedTrackGoal INSTANCE = new DefendAssignedTrackGoal();

    private DefendAssignedTrackGoal() {}

    @Override public String name() { return "DefendAssignedTrack"; }
    @Override public Priority priority() { return Priority.MISSION; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        if (assignment == null || assignment.kind() != AssignmentKind.DEFEND_TRACK) return 0f;
        if (assignment.targetCellX() < 0 || assignment.targetCellY() < 0) return 0f;
        if (state.get(Predicate.MORALE_BROKEN) || state.get(Predicate.HAS_TARGET)) return 0f;
        return 0.85f;
    }

    @Override public WorldState desiredState(Squad squad, BattleView sim) { return WorldState.EMPTY; }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        if (assignment == null || assignment.kind() != AssignmentKind.DEFEND_TRACK) return null;
        int x = assignment.targetCellX();
        int y = assignment.targetCellY();
        SquadPlan current = squad.currentPlan;
        if (current != null && !current.isComplete()) {
            SquadPlan.Step step = current.currentStep();
            if (step != null && step.action instanceof DefendTrack defend
                    && defend.targetX() == x && defend.targetY() == y) return current;
        }
        return new SquadPlan(List.of(new SquadPlan.Step(new DefendTrack(x, y))));
    }
}
