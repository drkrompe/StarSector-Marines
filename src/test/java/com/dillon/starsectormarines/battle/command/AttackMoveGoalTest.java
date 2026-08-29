package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.AttackMove;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The order's defining property: it keeps its destination through contact,
 * where the staging order deliberately gives its up.
 */
public class AttackMoveGoalTest {

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(64, 32);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(64, 32));
    }

    private static Squad squadWith(BattleSimulation sim, ObjectiveAssignment assignment) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = sim.spawn(new EntitySpec("m0", Faction.MARINE,
                UnitType.MARINE, 10, 15).squad(squad.id));
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.assignedObjective = assignment;
        return squad;
    }

    @Test
    public void anAttackMoveKeepsItsOrderWhileTheStagingOrderGivesItUp() {
        BattleSimulation sim = openSim();
        WorldState inContact = WorldState.EMPTY.with(Predicate.HAS_TARGET, true);

        Squad attacking = squadWith(sim, ObjectiveAssignment.attackMove(0, 50, 15));
        Squad staging = squadWith(sim, ObjectiveAssignment.advanceTrack(1, 50, 15));

        assertTrue(AttackMoveGoal.INSTANCE.relevance(inContact, attacking, sim) > 0f,
                "an attack move is precisely an order that survives contact");
        assertEquals(0f,
                AdvanceAssignedTrackGoal.INSTANCE.relevance(inContact, staging, sim),
                1e-6f,
                "the staging order still yields to the engagement bucket");
    }

    @Test
    public void aBrokenSquadStillReleasesTheOrder() {
        BattleSimulation sim = openSim();
        Squad squad = squadWith(sim, ObjectiveAssignment.attackMove(0, 50, 15));
        WorldState broken = WorldState.EMPTY
                .with(Predicate.HAS_TARGET, true)
                .with(Predicate.MORALE_BROKEN, true);

        assertEquals(0f, AttackMoveGoal.INSTANCE.relevance(broken, squad, sim), 1e-6f,
                "surviving contact is not the same as ignoring a finished squad");
    }

    @Test
    public void thePlanIsStickyAcrossTheReplansContactCauses() {
        BattleSimulation sim = openSim();
        Squad squad = squadWith(sim, ObjectiveAssignment.attackMove(0, 50, 15));

        SquadPlan first = AttackMoveGoal.INSTANCE.customPlan(squad, sim);
        assertInstanceOf(AttackMove.class, first.currentStep().action);
        squad.currentPlan = first;

        // A squad under attack-move replans on every contact edge, doctrine
        // flip and casualty. Re-synthesizing would throw away the advance's
        // commit state each time.
        assertSame(first, AttackMoveGoal.INSTANCE.customPlan(squad, sim));
    }

    @Test
    public void aNewDestinationReplacesThePlan() {
        BattleSimulation sim = openSim();
        Squad squad = squadWith(sim, ObjectiveAssignment.attackMove(0, 50, 15));
        squad.currentPlan = AttackMoveGoal.INSTANCE.customPlan(squad, sim);

        squad.assignedObjective = ObjectiveAssignment.attackMove(squad.id, 20, 25);
        SquadPlan replanned = AttackMoveGoal.INSTANCE.customPlan(squad, sim);

        AttackMove step = assertInstanceOf(AttackMove.class, replanned.currentStep().action);
        assertEquals(20, step.destX());
        assertEquals(25, step.destY());
    }
}
