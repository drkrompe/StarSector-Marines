package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Doctrine;

import java.util.List;

/** Keeps a squad planted after generic pursuit rejects a hostile cluster. */
public final class HoldEngagementLineGoal implements Goal {

    public static final HoldEngagementLineGoal INSTANCE = new HoldEngagementLineGoal();

    private HoldEngagementLineGoal() {}

    @Override public String name() { return "HoldEngagementLine"; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        if (state.get(Predicate.THREAT_DENSITY_HIGH_AT_TARGET)) return 2f;
        return squad.contactPicture.hasContacts()
                && squad.contactPicture.doctrine() == Doctrine.HOLD ? 2.5f : 0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        return new SquadPlan(List.of(new SquadPlan.Step(OverwatchPosture.INSTANCE)));
    }
}
