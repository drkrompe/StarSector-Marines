package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OpeningOperationCommandTest {

    @Test
    void reliefPreservesTheAuthoredLineAndAssaultsItsPublicAnchor() {
        BattleSimulation sim = openSim(false);
        Squad local = squad(sim, Faction.MARINE, UnitRole.GARRISON, 5, 10);
        local.assignedNode = node(5, 10, Faction.MARINE);
        Squad relief = squad(sim, Faction.MARINE, UnitRole.COMBATANT, 2, 4);
        Squad payload = squad(sim, Faction.MARINE, UnitRole.COMBATANT, 3, 16);
        Squad raider = squad(sim, Faction.DEFENDER, UnitRole.PATROL, 25, 10);
        OpeningOperationCommandFacts facts = new OpeningOperationCommandFacts(
                OpeningOperationKind.RELIEF, "relief-anchor",
                "Relief anchor", 7, 10);

        CommanderService service = pairedService(sim, facts, false);
        service.assignments().assignExternal(local,
                ObjectiveAssignment.holdNode(local.id, local.assignedNode),
                CommandAuthority.GARRISON, "local-garrison",
                "preserve authored relief post", 0);
        claim(service, relief);
        claim(service, raider);
        ObjectiveAssignment payloadOrder = ObjectiveAssignment.escort(
                payload.id, 3, 16);
        service.assignments().assignExternal(payload, payloadOrder,
                CommandAuthority.PAYLOAD, "opening-test-payload",
                "preserve scenario payload", 0);
        service.tick(CommanderService.COMMANDER_TICK_PERIOD, sim);

        assertEquals(AssignmentKind.HOLD_NODE, local.assignedObjective.kind());
        assertEquals(AssignmentKind.DEFEND_AREA, relief.assignedObjective.kind());
        assertEquals(AssignmentKind.SWEEP_SECTOR, raider.assignedObjective.kind());
        assertEquals(payloadOrder, payload.assignedObjective);
        assertNear(facts, relief.assignedObjective);
        assertNear(facts, raider.assignedObjective);

        OpeningOperationCommandPicture marine = picture(service, Faction.MARINE);
        OpeningOperationCommandPicture defender = picture(service, Faction.DEFENDER);
        assertEquals(OpeningOperationCommandPicture.Phase.PRESERVE_RELIEF_ANCHOR,
                marine.phase());
        assertEquals(OpeningOperationCommandPicture.Role.AUTHORED_POST,
                marine.intentFor(local.id).role());
        assertEquals(OpeningOperationCommandPicture.Role.PRESERVE_ELEMENT,
                marine.intentFor(relief.id).role());
        assertEquals(OpeningOperationCommandPicture.Role.EXTERNAL,
                marine.intentFor(payload.id).role());
        assertEquals(OpeningOperationCommandPicture.Phase.ASSAULT_RELIEF_ANCHOR,
                defender.phase());
        assertEquals(OpeningOperationCommandPicture.Role.ASSAULT_ELEMENT,
                defender.intentFor(raider.id).role());
    }

    @Test
    void counterattackUsesTheDepotEvenWhenNoHostileExists() {
        BattleSimulation sim = openSim(false);
        Squad marine = squad(sim, Faction.MARINE, UnitRole.COMBATANT, 2, 4);
        OpeningOperationCommandFacts facts = new OpeningOperationCommandFacts(
                OpeningOperationKind.COUNTERATTACK, "bandit-depot",
                "Bandit depot", 24, 10);
        CommanderService service = new CommanderService();
        OpeningOperationCommandDisclosure disclosure =
                new OpeningOperationCommandDisclosure(facts);
        service.setAutonomousCommander(Faction.MARINE,
                new OpeningOperationCommand(Faction.MARINE), disclosure);
        claim(service, marine);

        service.tick(CommanderService.COMMANDER_TICK_PERIOD, sim);

        assertNotNull(marine.assignedObjective);
        assertEquals(AssignmentKind.SWEEP_SECTOR,
                marine.assignedObjective.kind());
        assertNear(facts, marine.assignedObjective);
        OpeningOperationCommandPicture picture = picture(service, Faction.MARINE);
        assertEquals(OpeningOperationCommandPicture.Phase.SECURE_BANDIT_DEPOT,
                picture.phase());
        assertEquals(OpeningOperationCommandPicture.Reason.BANDIT_DEPOT_SECURE,
                picture.intentFor(marine.id).reason());
        CommandDirective first = service.activeDirective(marine.id);
        service.tick(CommanderService.COMMANDER_TICK_PERIOD, sim);
        CommandDirective second = service.activeDirective(marine.id);
        assertEquals(first.issuedTick(), second.issuedTick());
        assertEquals(first.stableUntilTick(), second.stableUntilTick());
    }

    @Test
    void playerLeaseIsPreservedUntilExpiryThenReturnsToScenarioCommand() {
        BattleSimulation sim = openSim(false);
        Squad marine = squad(sim, Faction.MARINE, UnitRole.COMBATANT, 2, 4);
        OpeningOperationCommandFacts facts = new OpeningOperationCommandFacts(
                OpeningOperationKind.COUNTERATTACK, "bandit-depot",
                "Bandit depot", 24, 10);
        CommanderService service = new CommanderService();
        service.setAutonomousCommander(Faction.MARINE,
                new OpeningOperationCommand(Faction.MARINE),
                new OpeningOperationCommandDisclosure(facts));
        ObjectiveAssignment intervention = ObjectiveAssignment.defendTrack(
                marine.id, 6, 6);
        service.assignments().assignExternal(marine, intervention,
                CommandAuthority.PLAYER_INTERVENTION, "player-intervention",
                "hold here", 0, 1);

        service.tick(CommanderService.COMMANDER_TICK_PERIOD, sim);

        assertEquals(intervention, marine.assignedObjective);
        assertEquals(OpeningOperationCommandPicture.Role.EXTERNAL,
                picture(service, Faction.MARINE).intentFor(marine.id).role());

        sim.simTickIndex = 2;
        service.tick(CommanderService.COMMANDER_TICK_PERIOD, sim);

        assertEquals(AssignmentKind.SWEEP_SECTOR,
                marine.assignedObjective.kind());
        assertNear(facts, marine.assignedObjective);
        assertEquals(OpeningOperationCommand.issuer(Faction.MARINE),
                service.activeDirective(marine.id).issuer());
        assertEquals(OpeningOperationCommandPicture.Role.SECURE_ELEMENT,
                picture(service, Faction.MARINE).intentFor(marine.id).role());
    }

    @Test
    void hiddenHostileLocationDoesNotChangeTheAuthoredPlan() {
        assertEquals(counterattackMarineOrder(-1),
                counterattackMarineOrder(18));
    }

    @Test
    void reversingRegistrationOrderDoesNotChangePairedOrders() {
        assertEquals(counterattackOrders(false), counterattackOrders(true));
    }

    @Test
    void disconnectedPlaceLeavesAnExplainedOwnershipOnlyDirective() {
        BattleSimulation sim = openSim(true);
        Squad marine = squad(sim, Faction.MARINE, UnitRole.COMBATANT, 2, 4);
        OpeningOperationCommandFacts facts = new OpeningOperationCommandFacts(
                OpeningOperationKind.COUNTERATTACK, "bandit-depot",
                "Bandit depot", 24, 10);
        CommanderService service = new CommanderService();
        service.setAutonomousCommander(Faction.MARINE,
                new OpeningOperationCommand(Faction.MARINE),
                new OpeningOperationCommandDisclosure(facts));
        claim(service, marine);

        service.tick(CommanderService.COMMANDER_TICK_PERIOD, sim);

        assertNull(marine.assignedObjective);
        CommandDirective directive = service.activeDirective(marine.id);
        assertNotNull(directive);
        assertEquals("OBJECTIVE_UNREACHABLE", directive.reason());
        assertNull(directive.assignment());
        assertEquals(OpeningOperationCommandPicture.Role.UNASSIGNED,
                picture(service, Faction.MARINE).intentFor(marine.id).role());
    }

    private static Map<Faction, ObjectiveAssignment> counterattackOrders(
            boolean reverseRegistration) {
        BattleSimulation sim = openSim(false);
        Squad marine = squad(sim, Faction.MARINE, UnitRole.COMBATANT, 2, 4);
        Squad defender = squad(sim, Faction.DEFENDER, UnitRole.PATROL, 25, 10);
        OpeningOperationCommandFacts facts = new OpeningOperationCommandFacts(
                OpeningOperationKind.COUNTERATTACK, "bandit-depot",
                "Bandit depot", 24, 10);
        CommanderService service = pairedService(sim, facts, reverseRegistration);
        claim(service, marine);
        claim(service, defender);
        service.tick(CommanderService.COMMANDER_TICK_PERIOD, sim);
        Map<Faction, ObjectiveAssignment> orders = new LinkedHashMap<>();
        orders.put(Faction.MARINE, marine.assignedObjective);
        orders.put(Faction.DEFENDER, defender.assignedObjective);
        return orders;
    }

    private static ObjectiveAssignment counterattackMarineOrder(int hostileX) {
        BattleSimulation sim = openSim(false);
        Squad marine = squad(sim, Faction.MARINE, UnitRole.COMBATANT, 2, 4);
        if (hostileX >= 0) {
            squad(sim, Faction.DEFENDER, UnitRole.PATROL, hostileX, 2);
        }
        OpeningOperationCommandFacts facts = new OpeningOperationCommandFacts(
                OpeningOperationKind.COUNTERATTACK, "bandit-depot",
                "Bandit depot", 24, 10);
        CommanderService service = new CommanderService();
        service.setAutonomousCommander(Faction.MARINE,
                new OpeningOperationCommand(Faction.MARINE),
                new OpeningOperationCommandDisclosure(facts));
        claim(service, marine);
        service.tick(CommanderService.COMMANDER_TICK_PERIOD, sim);
        return marine.assignedObjective;
    }

    private static CommanderService pairedService(
            BattleSimulation sim, OpeningOperationCommandFacts facts,
            boolean reverseRegistration) {
        CommanderService service = new CommanderService();
        OpeningOperationCommandDisclosure disclosure =
                new OpeningOperationCommandDisclosure(facts);
        Faction first = reverseRegistration ? Faction.DEFENDER : Faction.MARINE;
        Faction second = first == Faction.MARINE
                ? Faction.DEFENDER : Faction.MARINE;
        service.setAutonomousCommander(first,
                new OpeningOperationCommand(first), disclosure);
        service.setAutonomousCommander(second,
                new OpeningOperationCommand(second), disclosure);
        return service;
    }

    private static void claim(CommanderService service, Squad squad) {
        service.assignments().claimExternal(squad,
                CommandAuthority.MISSION_COMMAND,
                OpeningOperationCommand.issuer(squad.faction),
                "opening-operation force", 0);
    }

    private static OpeningOperationCommandPicture picture(
            CommanderService service, Faction faction) {
        CommanderSnapshot<?> snapshot = service.snapshot(faction);
        assertNotNull(snapshot);
        return (OpeningOperationCommandPicture) snapshot.detail();
    }

    private static void assertNear(OpeningOperationCommandFacts facts,
                                   ObjectiveAssignment assignment) {
        int distance = Math.abs(facts.cellX() - assignment.targetCellX())
                + Math.abs(facts.cellY() - assignment.targetCellY());
        org.junit.jupiter.api.Assertions.assertTrue(distance <= 4,
                "assignment must use the authored place, not a hostile cell");
    }

    private static BattleSimulation openSim(boolean divideMap) {
        NavigationGrid grid = new NavigationGrid(30, 20);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!divideMap || x != 15) grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(30, 20));
    }

    private static TacticalNode node(int x, int y, Faction faction) {
        return new TacticalNode(TacticalNode.Kind.OBJECTIVE,
                x, y, x - 1, y - 1, x + 1, y + 1, faction, 50, 4);
    }

    private static Squad squad(BattleSimulation sim, Faction faction,
                               UnitRole role, int x, int y) {
        long leader = sim.spawn(new EntitySpec(
                faction + "-" + x, faction, UnitType.MILITIA, x, y)
                .role(role));
        int squadId = sim.mintSquad(faction, leader);
        sim.squad().assignSquad(leader, squadId);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;
        squad.centroidX = x + 0.5f;
        squad.centroidY = y + 0.5f;
        return squad;
    }
}
