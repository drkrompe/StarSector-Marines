package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Once the player commands a side, mission command may own its squads but no
 * longer sends them anywhere: the player clears the map, not the commander.
 */
class AssignmentArbiterPlayerSideTest {

    private static final String COMMANDER = "test-command";

    @Test
    void aCommanderErrandForThePlayersSquadIsRefused() {
        BattleSimulation sim = openSim();
        Squad squad = addSquad(sim, Faction.MARINE);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        arbiter.yieldToPlayer(Faction.MARINE, sim);

        CommandDirective refused = commit(arbiter, sim, Faction.MARINE, CommandProposal.assign(
                ObjectiveAssignment.attackMove(squad.id, 8, 8), CommandAuthority.MISSION_COMMAND, "push"));
        arbiter.assignExternal(squad, ObjectiveAssignment.attackMove(squad.id, 8, 8),
                CommandAuthority.MISSION_COMMAND, COMMANDER, "direct write", 0);

        assertEquals(CommandDirective.Status.REJECTED, refused.status());
        assertEquals("the player commands this squad", refused.dispositionReason());
        assertNull(squad.assignedObjective, "a squad the player has not ordered holds where it stands");
    }

    @Test
    void aWithdrawalAndAnotherSidesErrandStillLand() {
        BattleSimulation sim = openSim();
        Squad marines = addSquad(sim, Faction.MARINE);
        Squad allies = addSquad(sim, Faction.ALLY);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        arbiter.yieldToPlayer(Faction.MARINE, sim);

        ObjectiveAssignment pullOut = ObjectiveAssignment.withdraw(marines.id, 1, 1);
        commit(arbiter, sim, Faction.MARINE, CommandProposal.assign(
                pullOut, CommandAuthority.MISSION_COMMAND, "extract"));
        ObjectiveAssignment push = ObjectiveAssignment.attackMove(allies.id, 8, 8);
        commit(arbiter, sim, Faction.ALLY, CommandProposal.assign(
                push, CommandAuthority.MISSION_COMMAND, "zone push"));

        assertEquals(pullOut, marines.assignedObjective, "leaving the field is the mission ending, not an errand");
        assertEquals(push, allies.assignedObjective, "allied forces keep their commander");
    }

    @Test
    void yieldingTakesBackAnErrandAlreadyGiven() {
        BattleSimulation sim = openSim();
        Squad squad = addSquad(sim, Faction.MARINE);
        AssignmentArbiter arbiter = new AssignmentArbiter();
        arbiter.assignExternal(squad, ObjectiveAssignment.clearZone(squad.id, 7),
                CommandAuthority.MISSION_COMMAND, COMMANDER, "zone push", 0);

        arbiter.yieldToPlayer(Faction.MARINE, sim);

        assertNull(squad.assignedObjective);
        assertEquals(COMMANDER, arbiter.activeDirective(squad.id).issuer(), "ownership stays; only the errand goes");
    }

    private static CommandDirective commit(AssignmentArbiter arbiter, BattleSimulation sim,
                                           Faction side, CommandProposal proposal) {
        CommandPlan<String> plan = new CommandPlan<>(side, COMMANDER,
                "ADVANCE", sim.getSimTickIndex(), -1, 1, 0, List.of(),
                List.of(proposal), "detail");
        return arbiter.commit(plan, sim, CommandTopology.freeze(sim))
                .directiveFor(proposal.squadId());
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(10, 10);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(10, 10));
    }

    private static Squad addSquad(BattleSimulation sim, Faction side) {
        int squadId = sim.mintSquad(side, UnitType.MARINE);
        long member = sim.spawn(new EntitySpec("unit-" + squadId, side,
                UnitType.MARINE, 2, 2).squad(squadId));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = member;
        squad.aliveMembers = 1;
        squad.centroidX = 2;
        squad.centroidY = 2;
        return squad;
    }
}
