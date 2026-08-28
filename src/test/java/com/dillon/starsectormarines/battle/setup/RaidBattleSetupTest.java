package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.CommanderService;
import com.dillon.starsectormarines.battle.command.RaidCommand;
import com.dillon.starsectormarines.battle.command.RaidCommandSnapshot;
import com.dillon.starsectormarines.battle.command.RaidDefenderCommand;
import com.dillon.starsectormarines.battle.command.objective.EliminateFactionObjective;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidBattleSetupTest {

    @Test
    void productionRaidInstallsStrikeContractAndPairedCommanders() {
        try (BattleSimulation sim = BattleSetup.createRaid(607_898L)) {
            RaidObjective objective = sim.getObjectives().stream()
                    .filter(RaidObjective.class::isInstance)
                    .map(RaidObjective.class::cast)
                    .findFirst().orElseThrow();
            assertTrue(sim.getGrid().isWalkable(objective.targetCellX(),
                    objective.targetCellY()));
            assertTrue(sim.getGrid().isWalkable(objective.egressCellX(),
                    objective.egressCellY()));
            assertFalse(sim.getObjectives().stream()
                    .filter(objectiveRow -> objectiveRow.owningFaction()
                            == Faction.MARINE)
                    .anyMatch(EliminateFactionObjective.class::isInstance));
            assertEquals(1, sim.getObjectives().stream()
                    .filter(objectiveRow -> objectiveRow.owningFaction()
                            == Faction.DEFENDER)
                    .filter(EliminateFactionObjective.class::isInstance).count());
            assertTrue(sim.getCommander(Faction.MARINE) instanceof RaidCommand);
            assertTrue(sim.getCommander(Faction.DEFENDER)
                    instanceof RaidDefenderCommand);

            sim.advance(CommanderService.COMMANDER_TICK_PERIOD
                    + BattleSimulation.TICK_DT);
            assertNotNull(sim.getCommanderSnapshot(Faction.MARINE));
            assertNotNull(sim.getCommanderSnapshot(Faction.DEFENDER));
            RaidCommandSnapshot defender = (RaidCommandSnapshot)
                    sim.getCommanderSnapshot(Faction.DEFENDER).detail();
            assertEquals(-1, defender.egressCellX(),
                    "defender disclosure must not reveal attacker egress");
        }
    }
}
