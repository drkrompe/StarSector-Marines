package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoapActionProfileTest {
    @Test
    void actionScopeAttributesSearchesAndDoesNotLeakAfterReturnOrFailure() {
        TickInnerProfile previous = TickInnerProfile.currentIfBound();
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        try {
            for (boolean fail : new boolean[]{false, true}) {
                profile.reset();
                Squad squad = new Squad(17, Faction.DEFENDER);
                SquadPlan.Step step = new SquadPlan.Step(new ProbeAction(fail));
                if (fail) {
                    assertThrows(IllegalStateException.class,
                            () -> GoapInfantryBehavior.executeTimed(step, 42L, squad, null));
                } else {
                    assertEquals(ActionStatus.RUNNING,
                            GoapInfantryBehavior.executeTimed(step, 42L, squad, null));
                }
                TickInnerProfile.PathSearch actionSearch = profile.slowPathSearches().get(0);
                assertEquals(42L, actionSearch.memberId());
                assertEquals(17, actionSearch.squadId());
                assertEquals("DefendSite", actionSearch.action());
                assertEquals("assignment-formation", actionSearch.routeReason());
                assertEquals(1L, profile.actions().get("DefendSite")[1]);
                profile.recordPathSearch(100L, 1, 1, 2, 2, false, 2, 1);
                TickInnerProfile.PathSearch outside = profile.slowPathSearches().get(0);
                assertEquals(0L, outside.memberId());
                assertEquals(-1, outside.squadId());
                assertEquals("", outside.action());
                assertEquals("", outside.routeReason());
            }
        } finally {
            TickInnerProfile.setCurrent(previous);
        }
    }

    /** Executes just the profiler seam, without constructing a battle. */
    private static final class ProbeAction implements Action {
        private final boolean fail;
        ProbeAction(boolean fail) { this.fail = fail; }
        @Override public String name() {
            throw new AssertionError("a display name must not be evaluated for profiling");
        }
        @Override public String profilingName() { return "DefendSite"; }
        @Override public WorldState preconditions() { return WorldState.EMPTY; }
        @Override public WorldState effects() { return WorldState.EMPTY; }
        @Override public float cost(WorldState state, Squad squad, BattleView sim) { return 1f; }
        @Override public int requiredMembers() { return 1; }
        @Override public ActionStatus execute(long member, Squad squad, BattleControl sim) {
            TickInnerProfile profile = TickInnerProfile.currentIfBound();
            profile.routeReason("assignment-formation");
            profile.recordPathSearch(10L, 1, 1, 2, 2, true, 2, 1);
            if (fail) throw new IllegalStateException("probe failed");
            return ActionStatus.RUNNING;
        }
    }
}
