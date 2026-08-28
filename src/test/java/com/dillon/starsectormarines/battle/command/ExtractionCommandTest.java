package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.ExtractionCommandSnapshot.Role;
import com.dillon.starsectormarines.battle.command.objective.ExtractionObjective;
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
import java.util.HashSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractionCommandTest {

    @Test
    void keepsPayloadAndEscortRolesStableWhileMovingCorridorTargets() {
        try (BattleSimulation sim = simulation()) {
            int[] route = {18, 10, 17, 10, 16, 10, 15, 10, 14, 10,
                    13, 10, 12, 10, 11, 10, 10, 10, 9, 10, 8, 10,
                    7, 10, 6, 10, 5, 10, 4, 10, 3, 10};
            int zone = sim.getZoneGraph().zoneIdAt(18, 10);
            ExtractionObjective objective = new ExtractionObjective(
                    "EXTRACTION-01", "package", zone, route);
            sim.addObjective(objective);
            int first = squad(sim, 2, 8);
            squad(sim, 2, 10);
            squad(sim, 2, 12);
            squad(sim, 3, 10);
            ExtractionCommand command = new ExtractionCommand();

            CommanderService.runSingle(command,
                    ExtractionCommandDisclosure.INSTANCE, sim);
            ExtractionCommandSnapshot initial = command.extractionSnapshot();
            Map<Integer, Role> initialRoles = roles(initial);

            assertEquals(4, initial.squadIntents().size());
            assertEquals(1, count(initial, Role.PAYLOAD_ELEMENT));
            assertEquals(1, count(initial, Role.CLOSE_ESCORT));
            assertEquals(2, initial.squadIntents().stream()
                    .filter(intent -> intent.role().name().endsWith("SCREEN"))
                    .count());
            assertEquals(2, initial.squadIntents().stream()
                    .filter(intent -> intent.assignmentKind()
                            == AssignmentKind.ESCORT).count());
            assertEquals(2, initial.squadIntents().stream()
                    .filter(intent -> intent.assignmentKind()
                            == AssignmentKind.DEFEND_SITE).count());
            assertEquals(4, new HashSet<>(initial.squadIntents().stream()
                    .map(intent -> List.of(intent.targetCellX(),
                            intent.targetCellY())).toList()).size());

            CommanderService.runSingle(command,
                    ExtractionCommandDisclosure.INSTANCE, sim);
            assertEquals(initialRoles, roles(command.extractionSnapshot()));

            int payloadSquad = initial.squadIntents().stream()
                    .filter(intent -> intent.role() == Role.PAYLOAD_ELEMENT)
                    .findFirst().orElseThrow().squadId();
            Squad squad = sim.getSquad(payloadSquad);
            long escort = sim.spawn(new EntitySpec("source escort",
                    Faction.MARINE, UnitType.MARINE, 18, 10)
                    .squad(payloadSquad));
            assertNotEquals(0L, escort);
            squad.aliveMembers++;
            for (int i = 0; i < 150; i++) objective.tick(sim);
            assertEquals("IN_TRANSIT", objective.extractionPhase().name());

            CommanderService.runSingle(command,
                    ExtractionCommandDisclosure.INSTANCE, sim);
            ExtractionCommandSnapshot moving = command.extractionSnapshot();
            assertEquals(initialRoles, roles(moving));
            assertEquals(AssignmentKind.ESCORT,
                    sim.getSquad(payloadSquad).assignedObjective.kind());
            assertEquals(objective.payloadCellX(),
                    moving.intentFor(payloadSquad).targetCellX());
            assertEquals(objective.payloadCellY(),
                    moving.intentFor(payloadSquad).targetCellY());
            assertNotNull(sim.getSquad(first).assignedObjective);
        }
    }

    private static long count(ExtractionCommandSnapshot snapshot, Role role) {
        return snapshot.squadIntents().stream()
                .filter(intent -> intent.role() == role).count();
    }

    private static Map<Integer, Role> roles(
            ExtractionCommandSnapshot snapshot) {
        return snapshot.squadIntents().stream().collect(Collectors.toMap(
                ExtractionCommandSnapshot.SquadIntent::squadId,
                ExtractionCommandSnapshot.SquadIntent::role));
    }

    private static int squad(BattleSimulation sim, int x, int y) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long member = sim.spawn(new EntitySpec("m" + squadId,
                Faction.MARINE, UnitType.MARINE, x, y).squad(squadId));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = member;
        squad.aliveMembers = 1;
        squad.centroidX = x;
        squad.centroidY = y;
        return squadId;
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(24, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 24; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(24, 20));
    }
}
