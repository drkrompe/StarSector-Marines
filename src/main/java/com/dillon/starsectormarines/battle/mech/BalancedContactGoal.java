package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

import java.util.List;

/**
 * Lets an assigned Balanced mech service an actionable contact before
 * resuming its command destination. Generic command goals own movement while
 * the route is clear; this bounded MISSION-tier interruption exists because
 * those goals otherwise outrank the ambient engagement goal.
 */
public final class BalancedContactGoal implements Goal {

    public static final BalancedContactGoal INSTANCE = new BalancedContactGoal();

    private static final WorldState DESIRED = WorldState.EMPTY
            .with(Predicate.ENEMY_DAMAGED, true);

    private BalancedContactGoal() {}

    @Override public String name() { return "BalancedContact"; }
    @Override public Priority priority() { return Priority.MISSION; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        if (squad.rescuePickupMech || state.get(Predicate.MORALE_BROKEN)
                || !state.get(Predicate.HAS_TARGET)) return 0f;

        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment == null || assignment.kind() == AssignmentKind.WITHDRAW) {
            return 0f;
        }

        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long member = sim.squadMemberAt(squad.id, i);
            if (!squad.availableToPlan(member, sim)) continue;
            MechLoadoutComponent loadout = sim.world().mechLoadout(member);
            if (loadout != null && loadout.effectiveRole() == MechRole.BALANCED) {
                return 1f;
            }
        }
        return 0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return DESIRED;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        return new SquadPlan(List.of(
                new SquadPlan.Step(ExecuteMechDoctrine.INSTANCE)));
    }
}
