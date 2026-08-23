package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.BreakContact;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Doctrine;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

import java.util.List;

/** Pulls an uncommitted squad out of a locally untenable contact. */
public final class DisengageFromContactGoal implements Goal {

    public static final DisengageFromContactGoal INSTANCE = new DisengageFromContactGoal();

    private DisengageFromContactGoal() {}

    @Override public String name() { return "DisengageFromContact"; }

    @Override public Priority priority() { return Priority.SURVIVAL; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        return squad.contactPicture.hasContacts()
                && squad.contactPicture.doctrine() == Doctrine.DISENGAGE ? 0.9f : 0f;
    }

    @Override public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override public SquadPlan customPlan(Squad squad, BattleView sim) {
        return new SquadPlan(List.of(new SquadPlan.Step(BreakContact.INSTANCE)));
    }
}
