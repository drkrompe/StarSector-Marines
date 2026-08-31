package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The floor of the goal ladder. What is under test is that it is always
 * available and that standing on it is a state rather than a task — the two
 * properties that stop a yielded order becoming a null plan.
 */
public class AwaitOrdersTest {

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(24, 24);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(24, 24));
    }

    private static Squad soloSquad(BattleSimulation sim, int x, int y) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        EntitySpec spec = new EntitySpec("m", Faction.MARINE, UnitType.MARINE, x, y);
        spec.squad(squadId);
        sim.spawn(spec);
        Squad squad = sim.getSquad(squadId);
        squad.originalSize = 1;
        squad.aliveMembers = 1;
        return squad;
    }

    @Test
    public void livesAtTheBottomOfTheLadder() {
        assertEquals(Goal.Priority.IDLE, AwaitOrdersGoal.INSTANCE.priority());
    }

    @Test
    public void isAlwaysAvailable() {
        BattleSimulation sim = openSim();
        Squad squad = soloSquad(sim, 5, 5);
        assertTrue(AwaitOrdersGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim) > 0f,
                "a floor that can decline is not a floor");
    }

    @Test
    public void staysAvailableForABrokenSquad() {
        BattleSimulation sim = openSim();
        Squad squad = soloSquad(sim, 5, 5);
        WorldState broken = WorldState.EMPTY.with(Predicate.MORALE_BROKEN, true);
        assertTrue(AwaitOrdersGoal.INSTANCE.relevance(broken, squad, sim) > 0f,
                "survival outranks this by bucket; guarding on morale here would hand a "
                        + "broken squad back to the null plan this goal exists to remove");
    }

    @Test
    public void yieldsToAmbientEngagementWithinTheIdleBucket() {
        assertEquals(AmbientEngagementGoal.INSTANCE.priority(),
                AwaitOrdersGoal.INSTANCE.priority(),
                "both are the idle bucket, so relevance is the only ordering between them");
        BattleSimulation sim = openSim();
        Squad squad = soloSquad(sim, 5, 5);
        // A squad holding a cue must close on it rather than stand about, so
        // whatever AmbientEngagement scores when it fires has to beat this.
        assertTrue(AwaitOrdersGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim) < 1f,
                "AmbientEngagement scores 1 when it holds a cue and must win that tie");
    }

    @Test
    public void plansAStandingStep() {
        BattleSimulation sim = openSim();
        Squad squad = soloSquad(sim, 5, 5);
        SquadPlan plan = AwaitOrdersGoal.INSTANCE.customPlan(squad, sim);
        assertInstanceOf(AwaitOrders.class, plan.currentStep().action);
    }

    @Test
    public void keepsTheRunningPlanRatherThanReSynthesising() {
        BattleSimulation sim = openSim();
        Squad squad = soloSquad(sim, 5, 5);
        squad.currentPlan = AwaitOrdersGoal.INSTANCE.customPlan(squad, sim);
        assertSame(squad.currentPlan, AwaitOrdersGoal.INSTANCE.customPlan(squad, sim));
    }

    @Test
    public void standingNeverSucceeds() {
        BattleSimulation sim = openSim();
        Squad squad = soloSquad(sim, 5, 5);
        long member = sim.squadMemberAt(squad.id, 0);
        // Twice, because the failure this pins is a step that completes on its
        // second tick and takes the squad round the replan loop every tick
        // for as long as it has nothing to do.
        assertEquals(ActionStatus.RUNNING, AwaitOrders.INSTANCE.execute(member, squad, sim));
        assertEquals(ActionStatus.RUNNING, AwaitOrders.INSTANCE.execute(member, squad, sim));
    }

    @Test
    public void dropsThePathTheWithdrawnOrderAuthored() {
        BattleSimulation sim = openSim();
        Squad squad = soloSquad(sim, 5, 5);
        long member = sim.squadMemberAt(squad.id, 0);
        sim.setPath(member, GridPathfinder.findPath(sim.getGrid(), 5, 5, 20, 20,
                sim.getOccupancyMap()));
        assertTrue(Paths.cellCount(sim.world().path(member)) > 0, "fixture needs a live path");

        AwaitOrders.INSTANCE.execute(member, squad, sim);

        assertEquals(0, Paths.cellCount(sim.world().path(member)),
                "that route belonged to an order the squad no longer holds");
    }
}
