package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

import java.util.List;

/** Quiet-state route for the allied mech stationed inside a rescue pickup star. */
public final class PatrolRescueFormationGoal implements Goal {

    public static final PatrolRescueFormationGoal INSTANCE =
            new PatrolRescueFormationGoal();

    private PatrolRescueFormationGoal() {}

    @Override public String name() { return "PatrolRescueFormation"; }
    @Override public Priority priority() { return Priority.MISSION; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        return squad.rescuePickupMech
                && squad.rescuePatrolCells != null
                && squad.rescuePatrolCells.length >= 2
                && squad.alertLevel == SquadAlertLevel.UNAWARE
                && !state.get(Predicate.MORALE_BROKEN) ? 1f : 0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        return new SquadPlan(List.of(
                new SquadPlan.Step(PatrolRescueFormation.INSTANCE)));
    }
}
