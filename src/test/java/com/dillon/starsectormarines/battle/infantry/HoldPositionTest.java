package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Story H goal-selection and planted-fire coverage. */
public class HoldPositionTest {

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(12, 8);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(grid.getWidth(), grid.getHeight()));
    }

    private static TacticalNode node(boolean mustHold) {
        return new TacticalNode(TacticalNode.Kind.COMMAND_POST, 4, 4,
                3, 3, 5, 5, Faction.DEFENDER, 95, 4, mustHold);
    }

    private static Squad brokenRemnant(TacticalNode node) {
        Squad squad = new Squad(1, Faction.DEFENDER);
        squad.originalSize = 4;
        squad.aliveMembers = 1;
        squad.moraleBroken = true;
        squad.assignedNode = node;
        return squad;
    }

    @Test
    public void mustHoldRemnantSelectsMissionOverSurvival() {
        BattleSimulation sim = openSim();
        Squad squad = brokenRemnant(node(true));
        WorldState state = WorldStateBuilder.build(squad, sim);

        assertTrue(state.get(Predicate.NODE_IS_MUST_HOLD));
        Goal picked = Goal.pickMostRelevant(
                List.of(SurviveContact.INSTANCE, HoldPosition.INSTANCE), state, squad, sim);
        assertEquals(HoldPosition.INSTANCE, picked,
                "the explicit mission order must defeat broken-morale survival");
        assertEquals(Goal.Priority.MISSION, HoldPosition.INSTANCE.priority());
    }

    @Test
    public void ordinaryRemnantStillSelectsSurvival() {
        BattleSimulation sim = openSim();
        Squad squad = brokenRemnant(node(false));
        WorldState state = WorldStateBuilder.build(squad, sim);

        assertFalse(state.get(Predicate.NODE_IS_MUST_HOLD));
        Goal picked = Goal.pickMostRelevant(
                List.of(HoldPosition.INSTANCE, SurviveContact.INSTANCE), state, squad, sim);
        assertEquals(SurviveContact.INSTANCE, picked);
    }

    @Test
    public void commanderHoldAssignmentIsTheActiveNode() {
        BattleSimulation sim = openSim();
        Squad squad = brokenRemnant(node(false));
        squad.assignedObjective = ObjectiveAssignment.holdNode(squad.id, node(true));

        assertTrue(WorldStateBuilder.build(squad, sim).get(Predicate.NODE_IS_MUST_HOLD),
                "a commander HOLD_NODE target overrides the spawn-time post");
    }

    @Test
    public void intactMustHoldSquadKeepsOrdinaryGarrisonBehavior() {
        BattleSimulation sim = openSim();
        Squad squad = brokenRemnant(node(true));
        squad.aliveMembers = 4;
        WorldState state = WorldStateBuilder.build(squad, sim);

        assertEquals(0f, HoldPosition.INSTANCE.relevance(state, squad, sim),
                "the special posture begins only for the actual last survivor");
    }

    @Test
    public void customPlanIsOnePerpetualLastStandStep() {
        SquadPlan plan = HoldPosition.INSTANCE.customPlan(brokenRemnant(node(true)), openSim());
        assertEquals(1, plan.stepCount());
        assertTrue(plan.steps().get(0).action instanceof LastStandHold);
    }

    @Test
    public void survivorHoldsHomeAndAuthorsStancedOpportunityFire() {
        BattleSimulation sim = openSim();
        Squad squad = brokenRemnant(node(true));
        long survivor = sim.spawn(new EntitySpec("last", Faction.DEFENDER, UnitType.MARINE, 4, 4)
                .home(4, 4));
        long enemy = sim.spawn(new EntitySpec("enemy", Faction.MARINE, UnitType.MARINE, 6, 4));
        sim.world().setAttackRange(survivor, 10f);
        sim.setPath(survivor, new int[]{5, 4});

        assertEquals(ActionStatus.RUNNING, LastStandHold.INSTANCE.execute(survivor, squad, sim));
        assertTrue(Paths.isEmpty(sim.world().path(survivor)), "holding clears movement");
        assertTrue(InfantryUnitPrep.tryOpportunityPrimary(survivor, sim));
        assertEquals(enemy, sim.combat().fireTargetId(survivor));
        assertEquals(FireStance.STANCED.ordinal(),
                sim.getRoster().entityWorld().getInt(survivor, sim.getRoster().components().COMBAT,
                        BattleComponents.COMBAT_FIRE_STANCE));
    }

    @Test
    public void displacedSurvivorPathsHomeInsteadOfTowardEnemy() {
        BattleSimulation sim = openSim();
        Squad squad = brokenRemnant(node(true));
        long survivor = sim.spawn(new EntitySpec("last", Faction.DEFENDER, UnitType.MARINE, 2, 4)
                .home(4, 4));
        sim.spawn(new EntitySpec("enemy", Faction.MARINE, UnitType.MARINE, 0, 4));

        LastStandHold.INSTANCE.execute(survivor, squad, sim);

        int[] path = sim.world().path(survivor);
        assertFalse(Paths.isEmpty(path));
        assertEquals(4, Paths.destX(path));
        assertEquals(4, Paths.destY(path));
    }
}
