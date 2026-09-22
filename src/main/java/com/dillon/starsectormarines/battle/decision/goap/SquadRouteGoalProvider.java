package com.dillon.starsectormarines.battle.decision.goap;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;

/** A shared movement destination that can be captured before unit execution. */
public interface SquadRouteGoalProvider {
    record Goal(int x, int y) {}

    /** Null when this action currently uses individual or live tactical destinations. */
    Goal squadRouteGoal(Squad squad, BattleView sim);
}
