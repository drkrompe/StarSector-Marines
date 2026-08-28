package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.ExtractionDefenseSnapshot.Role;
import com.dillon.starsectormarines.battle.command.objective.ExtractionObjective;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractionDefenderCommandTest {

    @Test
    void holdsMobileReserveUntilPublicAlarmMobilizesBoundedResponse() {
        try (BattleSimulation sim = simulation()) {
            int[] route = {15, 8, 14, 8, 13, 8, 12, 8, 11, 8,
                    10, 8, 9, 8, 8, 8, 7, 8, 6, 8, 5, 8, 4, 8};
            ExtractionObjective objective = new ExtractionObjective(
                    "EXTRACTION-01", "recovery package",
                    sim.getZoneGraph().zoneIdAt(15, 8), route);
            sim.addObjective(objective);
            Set<Integer> mobile = new TreeSet<>();
            for (int i = 0; i < 4; i++) {
                mobile.add(squad(sim, 20, 3 + i * 2));
            }
            ExtractionDefenderCommand command =
                    new ExtractionDefenderCommand(mobile);

            CommanderService.runSingle(command,
                    ExtractionDefenderCommandDisclosure.INSTANCE, sim);
            ExtractionDefenseSnapshot quiet = command.defenseSnapshot();
            assertEquals(ExtractionDefenseSnapshot.Phase.ROUTINE_SECURITY,
                    quiet.phase());
            assertEquals(1, assigned(sim, mobile));
            assertEquals(3, quiet.reserveCount());
            assertEquals(1, quiet.squadIntents().stream()
                    .filter(intent -> intent.role() == Role.SOURCE_GUARD)
                    .count());
            assertFalse(quiet.alarmActive());

            sim.spawn(new EntitySpec("escort", Faction.MARINE,
                    UnitType.MARINE, 15, 8));
            objective.tick(sim);
            CommanderService.runSingle(command,
                    ExtractionDefenderCommandDisclosure.INSTANCE, sim);
            ExtractionDefenseSnapshot alarm = command.defenseSnapshot();

            assertTrue(alarm.alarmActive());
            assertEquals(ExtractionDefenseSnapshot.Phase.ALARM_INTERDICTION,
                    alarm.phase());
            assertEquals(ExtractionDefenderCommand.ALARM_RESPONSE_LIMIT,
                    assigned(sim, mobile));
            assertEquals(1, alarm.reserveCount());
            assertEquals(-1, ExtractionDefenderCommandFacts.freeze(sim)
                    .payload().egressCellX());
            assertEquals(-1, ExtractionDefenderCommandFacts.freeze(sim)
                    .payload().payloadCellX());
            assertEquals(0f, ExtractionDefenderCommandFacts.freeze(sim)
                    .payload().progress());
        }
    }

    private static long assigned(BattleSimulation sim, Set<Integer> squads) {
        return squads.stream().filter(id ->
                sim.getSquad(id).assignedObjective != null).count();
    }

    private static int squad(BattleSimulation sim, int x, int y) {
        int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        long member = sim.spawn(new EntitySpec("d" + squadId,
                Faction.DEFENDER, UnitType.MILITIA, x, y).squad(squadId));
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = member;
        squad.aliveMembers = 1;
        squad.centroidX = x;
        squad.centroidY = y;
        return squadId;
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(24, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 24; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(24, 16));
    }
}
