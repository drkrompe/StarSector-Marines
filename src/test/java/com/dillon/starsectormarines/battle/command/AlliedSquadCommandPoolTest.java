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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An allied squad is nobody's to command but its own side's, and this story
 * gives allies no commander at all — they are held by garrison authority from
 * the producer that placed them. What has to be true for that to be safe is
 * that the player's commander can neither <em>see</em> an allied squad nor
 * <em>write</em> to one, and both are asked here rather than assumed from the
 * absence of an allied commander.
 */
class AlliedSquadCommandPoolTest {

    private static final String MARINE_COMMANDER = "marine-command";

    @Test
    void anAlliedSquadIsNotInTheMarineCommandFrame() {
        BattleSimulation sim = openSim();
        Squad marines = addSquad(sim, Faction.MARINE, 2, 2);
        Squad allies = addSquad(sim, Faction.ALLY, 4, 4);

        CommandFrame frame = CommandFrame.freeze(sim, Faction.MARINE,
                CommandTopology.freeze(sim),
                new CommandAssignmentSnapshot(Map.of()));

        assertNotNull(frame.squad(marines.id));
        assertNull(frame.squad(allies.id),
                "a friendly non-player side is not the player's to plan for");
        assertEquals(1, frame.squads().size());
    }

    @Test
    void aMarineCommandProposalOverAnAlliedSquadIsRefused() {
        BattleSimulation sim = openSim();
        Squad allies = addSquad(sim, Faction.ALLY, 4, 4);
        AssignmentArbiter arbiter = new AssignmentArbiter();

        ObjectiveAssignment order = ObjectiveAssignment.attackMove(allies.id, 8, 8);
        CommandPlan<String> plan = new CommandPlan<>(Faction.MARINE,
                MARINE_COMMANDER, "ADVANCE", sim.getSimTickIndex(), -1, 1, 0,
                List.of(), List.of(CommandProposal.assign(order,
                        CommandAuthority.MISSION_COMMAND, "take the hill")),
                "detail");

        CommandDirective refused = arbiter.commit(plan, sim,
                CommandTopology.freeze(sim)).directiveFor(allies.id);

        assertEquals(CommandDirective.Status.REJECTED, refused.status());
        assertNull(allies.assignedObjective,
                "a refused proposal must not leave the order on the squad");
        assertNull(arbiter.activeDirective(allies.id));
    }

    /**
     * The other half of the arrangement: garrison authority is how an allied
     * squad actually gets its orders, and that path is deliberately open.
     * Without this the test above would pass just as well on an ally nobody
     * can command at all, which is a different and much worse outcome.
     */
    @Test
    void garrisonAuthorityStillOwnsAnAlliedSquad() {
        BattleSimulation sim = openSim();
        Squad allies = addSquad(sim, Faction.ALLY, 4, 4);

        sim.assignSquadCommand(
                ObjectiveAssignment.defendArea(allies.id, 4, 4, 6),
                CommandAuthority.GARRISON, "allied-garrison", "hold the pad");

        CommandDirective held = sim.getSquadCommandDirective(allies.id);
        assertNotNull(held);
        assertEquals(CommandAuthority.GARRISON, held.authority());
        assertEquals(Faction.ALLY, allies.faction);
        assertTrue(allies.assignedObjective != null);
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(16, 16));
    }

    private static Squad addSquad(BattleSimulation sim, Faction faction,
                                  int x, int y) {
        int squadId = sim.mintSquad(faction, UnitType.MILITIA);
        long member = sim.spawn(new EntitySpec(
                faction + "-" + squadId, faction, UnitType.MILITIA, x, y)
                .squad(squadId));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = member;
        squad.aliveMembers = 1;
        squad.centroidX = x;
        squad.centroidY = y;
        return squad;
    }
}
