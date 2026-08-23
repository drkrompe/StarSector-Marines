package com.dillon.starsectormarines.battle.mech;

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
 * Mission-tier point doctrine for a mech squad containing an
 * {@link MechRole#ASSAULT} member. An explicit zone assignment is preferred;
 * a known local contact is enough to activate the same behavior for a side
 * without a commander, including defender patrols.
 */
public final class AssaultAssignedObjectiveGoal implements Goal {

    public static final AssaultAssignedObjectiveGoal INSTANCE =
            new AssaultAssignedObjectiveGoal();

    private static final WorldState DESIRED = WorldState.EMPTY
            .with(Predicate.ENEMY_DAMAGED, true);

    private AssaultAssignedObjectiveGoal() {}

    @Override public String name() { return "AssaultAssignedObjective"; }
    @Override public Priority priority() { return Priority.MISSION; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        if (squad.rescuePickupMech || state.get(Predicate.MORALE_BROKEN)) return 0f;
        if (!hasAssaultMember(squad, sim)) return 0f;

        ObjectiveAssignment assignment = squad.assignedObjective;
        if (assignment != null && assignment.targetZoneId() >= 0
                && sim.getZoneGraph().zoneById(assignment.targetZoneId()) != null) {
            return 1.2f;
        }
        return state.get(Predicate.HAS_TARGET)
                || squad.lastSeenEnemyX >= 0 && squad.lastSeenEnemyY >= 0
                ? 1f : 0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return DESIRED;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        return new SquadPlan(List.of(new SquadPlan.Step(BreachAndAssault.INSTANCE)));
    }

    private static boolean hasAssaultMember(Squad squad, BattleView sim) {
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long member = sim.squadMemberAt(squad.id, i);
            MechLoadoutComponent loadout = sim.world().mechLoadout(member);
            if (loadout != null && loadout.role == MechRole.ASSAULT) return true;
        }
        return false;
    }
}
