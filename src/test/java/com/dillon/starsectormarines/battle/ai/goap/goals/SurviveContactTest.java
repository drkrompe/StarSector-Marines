package com.dillon.starsectormarines.battle.ai.goap.goals;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.ai.goap.Goal;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.SquadPlan;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.actions.BreakContact;
import com.dillon.starsectormarines.battle.ai.goap.world.WorldStateBuilder;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link SurviveContact} — the Story B trigger, now driven by
 * the morale variable rather than raw alive count. Verifies relevance reads
 * {@link Predicate#MORALE_BROKEN}, the priority lives in SURVIVAL, and the
 * custom plan emits a single {@link BreakContact} step.
 */
public class SurviveContactTest {

    private static final int W = 10;
    private static final int H = 10;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    @Test
    public void priorityIsSurvival() {
        assertEquals(Goal.Priority.SURVIVAL, SurviveContact.INSTANCE.priority(),
                "SurviveContact lives in the SURVIVAL bucket — beats ENGAGEMENT, loses to MISSION");
    }

    @Test
    public void relevanceZeroWhenMoraleHolding() {
        assertEquals(0f, SurviveContact.INSTANCE.relevance(
                WorldState.EMPTY.with(Predicate.MORALE_BROKEN, false),
                null, null),
                "intact morale should not trigger SurviveContact");
    }

    @Test
    public void relevancePositiveWhenMoraleBroken() {
        assertTrue(SurviveContact.INSTANCE.relevance(
                WorldState.EMPTY.with(Predicate.MORALE_BROKEN, true),
                null, null) > 0f,
                "broken morale must trigger SurviveContact");
    }

    @Test
    public void customPlanIsSingleBreakContactStep() {
        BattleSimulation sim = openSim();
        Squad squad = new Squad(1, Faction.MARINE);
        squad.originalSize = 4;
        squad.aliveMembers = 1;
        squad.moraleBroken = true;

        SquadPlan plan = SurviveContact.INSTANCE.customPlan(squad, sim);
        assertNotNull(plan, "SurviveContact always synthesizes a plan");
        assertEquals(1, plan.stepCount(), "one BreakContact step covers the whole goal");
        assertTrue(plan.steps().get(0).action instanceof BreakContact,
                "the step must be a BreakContact instance");
    }

    @Test
    public void evaluatorReadsMoraleBrokenFlag() {
        BattleSimulation sim = openSim();
        Squad squad = new Squad(1, Faction.MARINE);
        squad.originalSize = 4;
        squad.aliveMembers = 4;
        squad.moraleBroken = true;

        WorldState ws = WorldStateBuilder.build(squad, sim);
        assertTrue(ws.get(Predicate.MORALE_BROKEN),
                "evaluator must read the hysteresis flag directly, not raw morale");
    }

    @Test
    public void evaluatorClearWhenFlagDown() {
        BattleSimulation sim = openSim();
        Squad squad = new Squad(1, Faction.MARINE);
        squad.originalSize = 4;
        squad.aliveMembers = 4;
        squad.moraleBroken = false;

        WorldState ws = WorldStateBuilder.build(squad, sim);
        assertFalse(ws.get(Predicate.MORALE_BROKEN),
                "intact squad reads MORALE_BROKEN == false");
    }

    @Test
    @SuppressWarnings("deprecation")
    public void deprecatedPredicateStaysStubbedFalse() {
        // Belt-and-braces — if a lingering caller reads
        // SQUAD_BELOW_HALF_STRENGTH it must read false, not whatever the old
        // alive-count math would have said. Otherwise the deprecation isn't a
        // safe migration step.
        BattleSimulation sim = openSim();
        Squad squad = new Squad(1, Faction.MARINE);
        squad.originalSize = 4;
        squad.aliveMembers = 1;  // would have been "true" under the old eval

        WorldState ws = WorldStateBuilder.build(squad, sim);
        assertFalse(ws.get(Predicate.SQUAD_BELOW_HALF_STRENGTH),
                "deprecated predicate must read false now that morale supersedes it");
    }
}
