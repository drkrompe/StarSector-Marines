package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

import java.util.List;

/** Mission-priority consumer for the rescue commander's moving escort assignment. */
public final class EscortAssignedCiviliansGoal implements Goal {

    public static final EscortAssignedCiviliansGoal INSTANCE =
            new EscortAssignedCiviliansGoal();

    private EscortAssignedCiviliansGoal() {}

    @Override public String name() { return "EscortAssignedCivilians"; }
    @Override public Priority priority() { return Priority.MISSION; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        if (state.get(Predicate.MORALE_BROKEN)) return 0f;
        ObjectiveAssignment assignment = squad.assignedObjective;
        return assignment != null && assignment.kind() == AssignmentKind.ESCORT
                && assignment.targetCellX() >= 0
                && assignment.targetCellY() >= 0 ? 0.9f : 0f;
    }

    @Override public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        SquadPlan current = squad.currentPlan;
        if (current != null && !current.isComplete()
                && current.currentStep().action
                instanceof EscortAssignedCivilians) {
            return current;
        }
        return new SquadPlan(List.of(new SquadPlan.Step(
                EscortAssignedCivilians.INSTANCE)));
    }
}
