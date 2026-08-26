package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.AssaultCommand;
import com.dillon.starsectormarines.battle.command.AssaultDefenderCommand;
import com.dillon.starsectormarines.battle.command.AssaultDefenseSnapshot;
import com.dillon.starsectormarines.battle.command.CommanderService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssaultBattleSetupTest {

    @Test
    void productionAssaultInstallsAutonomousSearchCommander() {
        try (BattleSimulation sim = BattleSetup.createPlaceholder(90210L)) {
            assertTrue(sim.getCommander(Faction.MARINE) instanceof AssaultCommand);
            assertTrue(sim.getCommander(Faction.DEFENDER)
                    instanceof AssaultDefenderCommand);
            sim.advance(CommanderService.COMMANDER_TICK_PERIOD
                    + BattleSimulation.TICK_DT);
            assertNotNull(sim.getCommanderSnapshot(Faction.MARINE));
            assertNotNull(sim.getCommanderSnapshot(Faction.DEFENDER));
            assertTrue(sim.getCommanderSnapshot(Faction.DEFENDER).detail()
                    instanceof AssaultDefenseSnapshot);
            AssaultDefenseSnapshot defense = (AssaultDefenseSnapshot)
                    sim.getCommanderSnapshot(Faction.DEFENDER).detail();
            AssaultDefenseSnapshot.SquadDirective reserve = defense.directives()
                    .stream().filter(row -> row.role()
                            == AssaultDefenseSnapshot.Role.RESERVE)
                    .findFirst().orElseThrow();
            int reserveMembers = defense.squads().stream()
                    .filter(row -> row.squadId() == reserve.squadId())
                    .findFirst().orElseThrow().aliveMembers();
            int largestRoutine = defense.directives().stream()
                    .filter(row -> row.role()
                            == AssaultDefenseSnapshot.Role.ROUTINE_SECURITY)
                    .mapToInt(row -> defense.squads().stream()
                            .filter(squad -> squad.squadId() == row.squadId())
                            .findFirst().orElseThrow().aliveMembers())
                    .max().orElse(0);
            assertEquals(largestRoutine, reserveMembers,
                    "the held reserve should be a complete patrol when available");
        }
    }
}
