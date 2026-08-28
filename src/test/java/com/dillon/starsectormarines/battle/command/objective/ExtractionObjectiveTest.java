package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractionObjectiveTest {

    private static final float DT = BattleSimulation.TICK_DT;
    private static final int[] ROUTE = {10, 10, 8, 8, 6, 6, 4, 4, 2, 2};

    @Test
    void uncontestedControlEscortAndBoardingWinWithoutElimination() {
        try (BattleSimulation sim = simulation()) {
            ExtractionObjective objective = objective(sim, DT, DT, DT,
                    DT * 2f, DT * 3f);
            long marine = sim.spawn(new EntitySpec("escort", Faction.MARINE,
                    UnitType.MARINE, 10, 10));
            sim.spawn(new EntitySpec("distant guard", Faction.DEFENDER,
                    UnitType.MILITIA, 18, 18));
            EliminateFactionObjective defenderObjective =
                    new EliminateFactionObjective(
                            Faction.DEFENDER, Faction.MARINE);

            objective.tick(sim);
            assertEquals(ExtractionPayloadObjective.Phase.IN_TRANSIT,
                    objective.extractionPhase());
            assertTrue(objective.alarmActive());

            while (objective.extractionPhase()
                    == ExtractionPayloadObjective.Phase.IN_TRANSIT) {
                sim.world().setCellPos(marine, objective.payloadCellX(),
                        objective.payloadCellY());
                objective.tick(sim);
            }
            sim.world().setCellPos(marine, objective.egressCellX(),
                    objective.egressCellY());
            objective.tick(sim);

            assertTrue(objective.isComplete());
            assertFalse(objective.isFailed());
            assertEquals(1, objective.boardedElements());
            assertEquals(1f, objective.normalizedProgress());
            defenderObjective.tick(sim);
            WinCheckSystem.WinResult result = new WinCheckSystem().tick(
                    List.of(objective, defenderObjective));
            assertTrue(result.complete());
            assertEquals(Faction.MARINE, result.winner(),
                    "boarding wins while the distant defender remains alive");
        }
    }

    @Test
    void unescortedPayloadIsLostAfterSustainedDefenderControl() {
        try (BattleSimulation sim = simulation()) {
            ExtractionObjective objective = objective(sim, DT, DT, DT,
                    DT * 2f, DT * 6f);
            long marine = sim.spawn(new EntitySpec("escort", Faction.MARINE,
                    UnitType.MARINE, 10, 10));
            objective.tick(sim);
            sim.world().setCellPos(marine, 18, 18);
            sim.spawn(new EntitySpec("interdictor", Faction.DEFENDER,
                    UnitType.MILITIA, objective.payloadCellX(),
                    objective.payloadCellY()));

            objective.tick(sim);
            objective.tick(sim);

            assertTrue(objective.isFailed());
            assertEquals(ExtractionPayloadObjective.Failure.LOST,
                    objective.failureReason());
            assertEquals(1, objective.lostElements());
            EliminateFactionObjective defenderObjective =
                    new EliminateFactionObjective(
                            Faction.DEFENDER, Faction.MARINE);
            defenderObjective.tick(sim);
            WinCheckSystem.WinResult result = new WinCheckSystem().tick(
                    List.of(objective, defenderObjective));
            assertTrue(result.complete());
            assertEquals(Faction.DEFENDER, result.winner(),
                    "payload loss defeats Marines while both factions survive");
        }
    }

    @Test
    void releasedPayloadCanBeAbandonedWithoutInventingATimeoutWinner() {
        try (BattleSimulation sim = simulation()) {
            ExtractionObjective objective = objective(sim, DT, DT, DT,
                    DT * 8f, DT * 2f);
            long marine = sim.spawn(new EntitySpec("escort", Faction.MARINE,
                    UnitType.MARINE, 10, 10));
            objective.tick(sim);
            sim.world().setCellPos(marine, 18, 18);

            objective.tick(sim);
            objective.tick(sim);

            assertTrue(objective.isFailed());
            assertEquals(ExtractionPayloadObjective.Failure.ABANDONED,
                    objective.failureReason());
            assertEquals(1, objective.activeElements());
            assertEquals(0, objective.lostElements());
        }
    }

    private static ExtractionObjective objective(
            BattleSimulation sim, float secure, float travel, float boarding,
            float loss, float abandonment) {
        return new ExtractionObjective("EXTRACTION-01", "recovery package",
                sim.getZoneGraph().zoneIdAt(10, 10), ROUTE,
                secure, travel, boarding, loss, abandonment);
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(20, 20));
    }
}
