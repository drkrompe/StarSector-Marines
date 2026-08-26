package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.AssaultCommand;
import com.dillon.starsectormarines.battle.command.CommanderService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssaultBattleSetupTest {

    @Test
    void productionAssaultInstallsAutonomousSearchCommander() {
        try (BattleSimulation sim = BattleSetup.createPlaceholder(90210L)) {
            assertTrue(sim.getCommander(Faction.MARINE) instanceof AssaultCommand);
            sim.advance(CommanderService.COMMANDER_TICK_PERIOD
                    + BattleSimulation.TICK_DT);
            assertNotNull(sim.getCommanderSnapshot(Faction.MARINE));
        }
    }
}
