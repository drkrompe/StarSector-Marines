package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ExtractionObjective;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractionObjectiveDisclosureTest {

    @Test
    void defenderReceivesAlarmButNotEgressPayloadOrCohortTruth() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        }
        try (BattleSimulation sim = new BattleSimulation(
                grid, new CellTopology(20, 20))) {
            int[] route = GridPathfinder.findPath(grid, 10, 10, 2, 2);
            ExtractionObjective objective = new ExtractionObjective(
                    "EXTRACTION-01", "recovery package",
                    sim.getZoneGraph().zoneIdAt(10, 10), route);
            sim.addObjective(objective);
            sim.spawn(new EntitySpec("escort", Faction.MARINE,
                    UnitType.MARINE, 10, 10));
            objective.tick(sim);

            ExtractionObjectiveFacts marine = ExtractionObjectiveDisclosure
                    .freeze(sim, Faction.MARINE).get(0);
            ExtractionObjectiveFacts defender = ExtractionObjectiveDisclosure
                    .freeze(sim, Faction.DEFENDER).get(0);

            assertEquals(2, marine.egressCellX());
            assertEquals(objective.payloadCellX(), marine.payloadCellX());
            assertEquals(-1, defender.egressCellX());
            assertEquals(-1, defender.payloadCellX());
            assertEquals(-1, defender.activeElements());
            assertEquals("ALARM", defender.phase());
            assertTrue(defender.alarmActive());
        }
    }
}
