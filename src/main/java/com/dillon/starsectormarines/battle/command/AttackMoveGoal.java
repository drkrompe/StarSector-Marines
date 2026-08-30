package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.AttackMove;
import com.dillon.starsectormarines.battle.mech.ExecuteMechDoctrine;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

import java.util.List;

/**
 * Attacker-side "go there and clear what is in the way" order.
 *
 * <p><b>The distinction from {@link AdvanceAssignedTrackGoal} is the whole
 * point of the kind.</b> That goal zeroes its own relevance on
 * {@link Predicate#HAS_TARGET}, deliberately handing a squad in contact to the
 * ENGAGEMENT bucket — which is right for a staging order, whose job is to reach
 * a line and stop. It is wrong for an attack move, because the engagement goals
 * carry no memory of the destination: the squad fights, wins, and stands there.
 * This goal therefore keeps its MISSION relevance through contact and lets
 * {@link AttackMove} own infantry fighting, or the shared Mech doctrine
 * dispatcher own Mech fighting, so the objective is still there afterwards.
 *
 * <p>Morale remains the escape. A squad with every fire team broken releases
 * the order like any other mission goal; a single broken team peels below the
 * plan without the squad giving up its objective.
 */
public final class AttackMoveGoal implements Goal {

    public static final AttackMoveGoal INSTANCE = new AttackMoveGoal();

    private AttackMoveGoal() {}

    @Override public String name() { return "AttackMove"; }
    @Override public Priority priority() { return Priority.MISSION; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null
                || assignment.kind() != AssignmentKind.ATTACK_MOVE) return 0f;
        if (assignment.targetCellX() < 0 || assignment.targetCellY() < 0) return 0f;
        if (state.get(Predicate.MORALE_BROKEN)
                || squad.isMechSquad() && squad.rescuePickupMech) return 0f;
        // Deliberately no HAS_TARGET release — see the class note.
        return squad.isMechSquad() ? 2f : 0.85f;
    }

    @Override public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null
                || assignment.kind() != AssignmentKind.ATTACK_MOVE) return null;
        int x = assignment.targetCellX();
        int y = assignment.targetCellY();
        if (squad.isMechSquad()) {
            return new SquadPlan(List.of(
                    new SquadPlan.Step(ExecuteMechDoctrine.INSTANCE)));
        }

        // Plan stickiness. The squad replans on every contact edge, doctrine
        // flip and casualty; re-synthesizing the same step each time would
        // discard the advance's commit state and bounding progress with it.
        SquadPlan current = squad.currentPlan;
        if (current != null && !current.isComplete()) {
            SquadPlan.Step step = current.currentStep();
            if (step != null && step.action instanceof AttackMove move
                    && move.destX() == x && move.destY() == y) {
                return current;
            }
        }
        return new SquadPlan(List.of(new SquadPlan.Step(new AttackMove(x, y))));
    }
}
