package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

/** Mission-priority return to an authored egress. */
public final class WithdrawAssignedGoal implements Goal {
    public static final WithdrawAssignedGoal INSTANCE = new WithdrawAssignedGoal();

    private WithdrawAssignedGoal() { }

    @Override public String name() { return "WithdrawAssigned"; }
    @Override public Priority priority() { return Priority.MISSION; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null || assignment.kind() != AssignmentKind.WITHDRAW
                || assignment.targetCellX() < 0 || assignment.targetCellY() < 0
                || state.get(Predicate.MORALE_BROKEN)) return 0f;
        return 1f;
    }

    @Override public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override public SquadPlan customPlan(Squad squad, BattleView sim) {
        return ServiceAssignedObjectiveGoal.planFor(squad, AssignmentKind.WITHDRAW);
    }
}
