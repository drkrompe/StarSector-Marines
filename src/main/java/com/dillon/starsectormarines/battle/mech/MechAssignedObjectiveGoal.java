package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

import java.util.List;

/** Keeps non-attack-move command geometry authoritative for mixed mech lances. */
public final class MechAssignedObjectiveGoal implements Goal {

    public static final MechAssignedObjectiveGoal INSTANCE =
            new MechAssignedObjectiveGoal();

    private MechAssignedObjectiveGoal() {}

    @Override public String name() { return "MechAssignedObjective"; }
    @Override public Priority priority() { return Priority.MISSION; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        var assignment = squad.assignmentForExecution();
        if (squad.rescuePickupMech || state.get(Predicate.MORALE_BROKEN)
                || MechAssignmentBoundary.isAttackMove(squad)
                || assignment != null
                && assignment.kind() == AssignmentKind.DEFEND_AREA) {
            return 0f;
        }
        return MechAssignmentBoundary.hasSupportedAssignment(squad, sim)
                ? 1.5f : 0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        return new SquadPlan(List.of(
                new SquadPlan.Step(ExecuteMechDoctrine.INSTANCE)));
    }
}
