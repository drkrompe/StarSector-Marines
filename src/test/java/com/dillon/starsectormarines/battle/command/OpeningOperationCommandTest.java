package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.infantry.RoutinePatrol;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpeningOperationCommandTest {

    @Test
    void mobileRaidersAdvanceWhileLocalGarrisonKeepsItsPost() {
        BattleSimulation sim = openSim();
        Squad locals = squad(sim, Faction.MARINE, UnitType.MILITIA, 2, 5);
        locals.assignedNode = new TacticalNode(TacticalNode.Kind.OBJECTIVE,
                2, 5, 1, 4, 3, 6, Faction.MARINE, 50, 4);
        Squad raiders = squad(sim, Faction.DEFENDER, UnitType.MILITIA, 16, 5);
        ObjectiveAssignment localPost = ObjectiveAssignment.holdNode(
                locals.id, locals.assignedNode);
        sim.assignSquadCommand(localPost, CommandAuthority.GARRISON,
                "local-garrison", "preserve opening position");

        new OpeningOperationCommand(Faction.MARINE, Faction.DEFENDER, true)
                .tick(sim, sim);
        new OpeningOperationCommand(Faction.DEFENDER, Faction.MARINE, false)
                .tick(sim, sim);

        assertEquals(localPost, locals.assignedObjective);
        assertEquals("local-garrison",
                sim.getSquadCommandDirective(locals.id).issuer());
        assertNotNull(raiders.assignedObjective);
        assertEquals(AssignmentKind.CLEAR_ZONE,
                raiders.assignedObjective.kind());
        CommandDirective directive = sim.getSquadCommandDirective(raiders.id);
        assertNotNull(directive);
        assertEquals(CommandAuthority.MISSION_COMMAND, directive.authority());
        assertEquals(OpeningOperationCommand.ISSUER_PREFIX + Faction.DEFENDER,
                directive.issuer());
        assertEquals(0f, RoutinePatrol.INSTANCE.relevance(
                WorldState.EMPTY, raiders, sim));
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(20, 10);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(20, 10));
    }

    private static Squad squad(BattleSimulation sim, Faction faction,
                               UnitType type, int x, int y) {
        long leader = sim.spawn(new EntitySpec(
                faction + "-" + x, faction, type, x, y));
        int squadId = sim.mintSquad(faction, leader);
        sim.squad().assignSquad(leader, squadId);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;
        squad.centroidX = x + 0.5f;
        squad.centroidY = y + 0.5f;
        return squad;
    }
}
