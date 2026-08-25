package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.SweepSector;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

import java.util.List;

/**
 * Assault search-and-destroy goal. While the squad has no identified contact,
 * it executes the commander's sector waypoint instead of selecting the
 * impossible no-target {@link EliminateEnemiesGoal} plan. Direct or
 * source-linked audio belief makes {@link Predicate#HAS_TARGET} true and
 * yields immediately to ordinary engagement. Anonymous noise remains an
 * investigation bearing consumed by {@link SweepSector}.
 */
public final class SweepAssignedSectorGoal implements Goal {

    public static final SweepAssignedSectorGoal INSTANCE = new SweepAssignedSectorGoal();

    private SweepAssignedSectorGoal() {}

    @Override
    public String name() {
        return "SweepAssignedSector";
    }

    @Override
    public Priority priority() {
        return Priority.MISSION;
    }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null || assignment.kind() != AssignmentKind.SWEEP_SECTOR) return 0f;
        if (assignment.targetCellX() < 0 || assignment.targetCellY() < 0) return 0f;
        if (state.get(Predicate.MORALE_BROKEN) || state.get(Predicate.HAS_TARGET)) return 0f;
        return 0.8f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null || assignment.kind() != AssignmentKind.SWEEP_SECTOR) return null;
        int x = assignment.targetCellX();
        int y = assignment.targetCellY();
        SquadPlan current = squad.currentPlan;
        if (current != null && !current.isComplete()) {
            SquadPlan.Step step = current.currentStep();
            if (step != null && step.action instanceof SweepSector sweep
                    && sweep.targetX() == x && sweep.targetY() == y) {
                return current;
            }
        }
        return new SquadPlan(List.of(new SquadPlan.Step(new SweepSector(x, y))));
    }
}
