package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

import java.util.List;

/** Story H mission override for the lone survivor on a must-hold node. */
public final class HoldPosition implements Goal {

    public static final HoldPosition INSTANCE = new HoldPosition();

    /** Beats ordinary mission goals inside the MISSION bucket. */
    private static final float LAST_STAND_RELEVANCE = 2f;

    private HoldPosition() {}

    @Override public String name() { return "HoldPosition"; }

    @Override
    public Priority priority() {
        return Priority.MISSION;
    }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        if (squad == null || squad.aliveMembers != 1) return 0f;
        return state.get(Predicate.NODE_IS_MUST_HOLD) ? LAST_STAND_RELEVANCE : 0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY.with(Predicate.NODE_IS_MUST_HOLD, true);
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        return new SquadPlan(List.of(new SquadPlan.Step(LastStandHold.INSTANCE)));
    }
}
