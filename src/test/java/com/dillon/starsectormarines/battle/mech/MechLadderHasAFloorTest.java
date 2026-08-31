package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Planner;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Why the mech ladder does not need the floor the infantry ladder was given,
 * and what has to stay true for that to keep being so.
 *
 * <p>The infantry ladder could end with no goal at all: its engagement floor,
 * {@code EliminateEnemiesGoal}, wants {@code ENEMY_DAMAGED}, and the only
 * actions that supply it need line of sight and range already held. With no
 * target the chain broke, nothing beneath caught the squad, and it stood
 * plan-less — which is what {@code AwaitOrdersGoal} now answers.
 *
 * <p><b>The mech ladder is safe for a reason that lives in its action library
 * rather than in its goals.</b> {@code MechEliminateEnemiesGoal} keeps the same
 * non-zero relevance floor, so the engagement bucket is never empty; and
 * {@code ExecuteMechDoctrine} carries no preconditions while satisfying
 * {@code ENEMY_DAMAGED}, so that goal can always be planned from any world
 * state whatsoever. The bucket is therefore always occupied <em>and</em> always
 * plannable, the ladder never descends past it, and the idle floor beneath is
 * unreachable. A floor goal added there today would be code that cannot run.
 *
 * <p><b>That is an accident of the library, not a guarantee, which is why it is
 * pinned here.</b> Give any of the doctrine actions a precondition, or let the
 * engagement goal return zero, and the mech ladder silently acquires the exact
 * defect the infantry one was just cured of — a lance standing still under
 * orders with no way to recover until its commander's next pulse. If this test
 * fails, the fix is not to relax it: it is to give {@code MECH_GOALS} a floor,
 * the way {@code INFANTRY_GOALS} has one, with a hold that suits a lance rather
 * than borrowing infantry cohesion.
 */
public class MechLadderHasAFloorTest {

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(16, 16));
    }

    /** Every planner fact false — the barest world a squad can be asked to act in. */
    private static WorldState nothingIsTrue() {
        WorldState state = WorldState.EMPTY;
        for (Predicate predicate : Predicate.values()) {
            state = state.with(predicate, false);
        }
        return state;
    }

    @Test
    public void theEngagementFloorIsAlwaysWorthWanting() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        Squad squad = sim.getSquad(squadId);
        assertTrue(MechEliminateEnemiesGoal.INSTANCE.relevance(nothingIsTrue(), squad, sim) > 0f,
                "a zero here empties the engagement bucket and the ladder falls through "
                        + "to an idle floor the mech library does not have");
    }

    @Test
    public void theEngagementFloorIsAlwaysReachable() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        Squad squad = sim.getSquad(squadId);

        assertNotNull(Planner.plan(
                        nothingIsTrue(),
                        MechEliminateEnemiesGoal.INSTANCE.desiredState(squad, sim),
                        GoapMechBehavior.MECH_ACTIONS,
                        squad,
                        sim,
                        256),
                "some mech action must satisfy ENEMY_DAMAGED from a world where nothing "
                        + "is true, or a lance with no target has no plan and no floor");
    }

    @Test
    public void nothingOccupiesTheMechIdleBucket() {
        // Not a rule, a record: the two facts above are what keep the mech
        // ladder safe, and this says out loud that nothing else is doing it.
        // When it stops being true, the two above have stopped mattering.
        boolean anyIdle = GoapMechBehavior.MECH_GOALS.stream()
                .anyMatch(goal -> goal.priority() == Goal.Priority.IDLE);
        assertTrue(!anyIdle,
                "if a mech idle goal has been added, the invariants above are no longer "
                        + "the thing keeping a lance from standing plan-less - update this test");
    }
}
