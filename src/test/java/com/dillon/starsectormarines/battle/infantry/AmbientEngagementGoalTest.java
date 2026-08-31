package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.AmbientAdvance;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.perception.NoiseKind;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadBeliefTestAccess;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The floor of the goal ladder. What is under test is which local cue the goal
 * closes on and when it declines — the advance itself is
 * {@link AmbientAdvance}'s and the push family's.
 */
public class AmbientEngagementGoalTest {

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(40, 20);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid,
                new CellTopology(grid.getWidth(), grid.getHeight()));
    }

    private static Squad squadAt(BattleSimulation sim, float x, float y) {
        Squad squad = new Squad(1, Faction.MARINE);
        squad.aliveMembers = 1;
        squad.centroidX = x;
        squad.centroidY = y;
        return squad;
    }

    private static long spawnHostile(BattleSimulation sim, String name, int x, int y) {
        EntitySpec spec = new EntitySpec(name, Faction.DEFENDER, UnitType.MARINE, x, y);
        spec.moveSpeed = 0f;
        return sim.spawn(spec);
    }

    @Test
    public void livesInTheIdleBucket() {
        assertEquals(Goal.Priority.IDLE, AmbientEngagementGoal.INSTANCE.priority(),
                "the fall-through goal must sit below engagement, never above it");
    }

    @Test
    public void declinesWithNoBeliefAndNoBearing() {
        BattleSimulation sim = openSim();
        Squad squad = squadAt(sim, 5f, 5f);
        assertEquals(0f, AmbientEngagementGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "a squad that believes in nobody and has heard nothing has nothing to advance on");
        assertNull(AmbientEngagementGoal.INSTANCE.customPlan(squad, sim));
    }

    @Test
    public void investigatesAFreshBearing() {
        BattleSimulation sim = openSim();
        sim.simTickIndex = 100;
        Squad squad = squadAt(sim, 5f, 5f);
        SquadBeliefTestAccess.observeAudible(squad, 20, 12, 100, 0.6f, NoiseKind.SHOT);

        assertTrue(AmbientEngagementGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim) > 0f);
        SquadPlan plan = AmbientEngagementGoal.INSTANCE.customPlan(squad, sim);
        assertNotNull(plan);
        AmbientAdvance advance = assertInstanceOf(AmbientAdvance.class,
                plan.currentStep().action);
        assertEquals(20, advance.destX());
        assertEquals(12, advance.destY());
    }

    @Test
    public void declinesABearingOlderThanTheInvestigationWindow() {
        BattleSimulation sim = openSim();
        Squad squad = squadAt(sim, 5f, 5f);
        SquadBeliefTestAccess.observeAudible(squad, 20, 12, 0, 0.6f, NoiseKind.SHOT);
        sim.simTickIndex = Math.round(
                AmbientEngagementGoal.BEARING_INVESTIGATION_SECONDS
                        / BattleSimulation.TICK_DT) + 1;

        assertEquals(0f, AmbientEngagementGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "an expired bearing walks the squad to where a fight was, not where one is");
    }

    @Test
    public void believedHostileOutranksAHeardBearing() {
        BattleSimulation sim = openSim();
        sim.simTickIndex = 50;
        Squad squad = squadAt(sim, 5f, 5f);
        long hostile = spawnHostile(sim, "defender", 30, 8);
        SquadBeliefTestAccess.observeAudible(squad, 20, 12, 50, 0.6f, NoiseKind.SHOT);
        SquadBeliefTestAccess.observeDirect(squad, hostile, 30, 8, 50);

        SquadPlan plan = AmbientEngagementGoal.INSTANCE.customPlan(squad, sim);
        AmbientAdvance advance = assertInstanceOf(AmbientAdvance.class,
                plan.currentStep().action);
        assertEquals(30, advance.destX(), "belief is the stronger claim and wins the cue");
        assertEquals(8, advance.destY());
    }

    @Test
    public void yieldsToSurvivalWhenMoraleIsBroken() {
        BattleSimulation sim = openSim();
        sim.simTickIndex = 100;
        Squad squad = squadAt(sim, 5f, 5f);
        SquadBeliefTestAccess.observeAudible(squad, 20, 12, 100, 0.6f, NoiseKind.SHOT);
        WorldState broken = WorldState.EMPTY.with(Predicate.MORALE_BROKEN, true);

        assertEquals(0f, AmbientEngagementGoal.INSTANCE.relevance(broken, squad, sim),
                "a broken squad belongs to SurviveContact, not to the floor goal");
    }

    @Test
    public void declinesACueTheSquadIsAlreadyStandingOn() {
        BattleSimulation sim = openSim();
        sim.simTickIndex = 100;
        Squad squad = squadAt(sim, 20f, 12f);
        SquadBeliefTestAccess.observeAudible(squad, 20, 12, 100, 0.6f, NoiseKind.SHOT);

        assertEquals(0f, AmbientEngagementGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim),
                "a reached cue must stop being one, or the goal replans every tick "
                        + "until the evidence expires");
        assertNull(AmbientEngagementGoal.INSTANCE.customPlan(squad, sim));
    }

    @Test
    public void keepsTheRunningPlanForAnUnchangedCue() {
        BattleSimulation sim = openSim();
        sim.simTickIndex = 100;
        Squad squad = squadAt(sim, 5f, 5f);
        SquadBeliefTestAccess.observeAudible(squad, 20, 12, 100, 0.6f, NoiseKind.SHOT);

        squad.currentPlan = AmbientEngagementGoal.INSTANCE.customPlan(squad, sim);
        SquadPlan again = AmbientEngagementGoal.INSTANCE.customPlan(squad, sim);
        assertSame(squad.currentPlan, again,
                "re-synthesizing would discard the advance's commit and bounding state");
    }
}
