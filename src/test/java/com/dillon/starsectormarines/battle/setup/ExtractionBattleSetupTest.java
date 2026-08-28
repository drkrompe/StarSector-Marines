package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.objective.EliminateFactionObjective;
import com.dillon.starsectormarines.battle.command.objective.ExtractionObjective;
import com.dillon.starsectormarines.battle.command.ExtractionCommand;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractionBattleSetupTest {

    @Test
    void productionExtractionInstallsPayloadContractInsteadOfElimination() {
        try (BattleSimulation sim = BattleSetup.createExtraction(141_418L)) {
            ExtractionObjective objective = sim.getObjectives().stream()
                    .filter(ExtractionObjective.class::isInstance)
                    .map(ExtractionObjective.class::cast)
                    .findFirst().orElseThrow();

            assertTrue(sim.getGrid().isWalkable(objective.sourceCellX(),
                    objective.sourceCellY()));
            assertTrue(sim.getGrid().isWalkable(objective.egressCellX(),
                    objective.egressCellY()));
            assertTrue(objective.routeCellCount() > 1);
            assertFalse(sim.getObjectives().stream()
                    .filter(row -> row.owningFaction() == Faction.MARINE)
                    .anyMatch(EliminateFactionObjective.class::isInstance));
            assertEquals(1, sim.getObjectives().stream()
                    .filter(row -> row.owningFaction() == Faction.DEFENDER)
                    .filter(EliminateFactionObjective.class::isInstance)
                    .count());
            assertTrue(sim.getCommander(Faction.MARINE)
                    instanceof ExtractionCommand);
            assertEquals(null, sim.getCommander(Faction.DEFENDER));
        }
    }
}
