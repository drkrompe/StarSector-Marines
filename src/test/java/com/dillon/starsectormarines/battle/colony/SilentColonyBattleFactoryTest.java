package com.dillon.starsectormarines.battle.colony;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.command.SilentColonyCommand;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.objective.ColonyArchiveObjective;
import com.dillon.starsectormarines.battle.command.objective.CivilianEvacuationObjective;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilentColonyBattleFactoryTest {

    @Test
    void dedicatedFactoryInstallsIndependentObjectivesAndAutomatedThreat() {
        BattleSimulation sim = BattleSetup.createSilentColony(
                100L, 2L, 12, Collections.emptyList(), RiskLevel.HIGH);

        assertEquals(12,
                sim.getCivilianEvacuationTracker().registeredCount());
        assertEquals(1, sim.getObjectives().stream()
                .filter(CivilianEvacuationObjective.class::isInstance)
                .count());
        assertEquals(1, sim.getObjectives().stream()
                .filter(ColonyArchiveObjective.class::isInstance)
                .count());
        assertTrue(sim.getCommander(Faction.MARINE)
                instanceof SilentColonyCommand);
        for (long shuttleId : sim.getAirEntityIds()) {
            ShuttleMission mission = sim.world().mission(shuttleId);
            assertNotNull(mission.commandClaim);
            assertEquals(CommandAuthority.MISSION_COMMAND,
                    mission.commandClaim.authority());
            assertEquals(SilentColonyCommand.ISSUER,
                    mission.commandClaim.issuer());
        }
        Squad commanded = addMarineSquad(sim, "marine-command-test");
        Squad payloadOwned = addMarineSquad(sim, "marine-payload-test");
        ObjectiveAssignment payloadOrder = ObjectiveAssignment.escort(
                payloadOwned.id, 1, 1);
        sim.assignSquadCommand(payloadOrder, CommandAuthority.PAYLOAD,
                "payload-owner", "external survivor duty");
        sim.claimSquadCommand(commanded.id, CommandAuthority.MISSION_COMMAND,
                SilentColonyCommand.ISSUER,
                "Silent Colony expedition force");
        sim.advance(3f);
        assertTrue(sim.getCommanderSnapshot(Faction.MARINE).detail()
                instanceof com.dillon.starsectormarines.battle.command
                .SilentColonyCommandSnapshot);
        int commandedMarineSquads = 0;
        for (var squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE || squad.aliveMembers <= 0) {
                continue;
            }
            CommandDirective directive = sim.getSquadCommandDirective(squad.id);
            assertNotNull(directive);
            if (squad == payloadOwned) {
                assertEquals("payload-owner", directive.issuer());
                assertEquals(payloadOrder, squad.assignedObjective);
                continue;
            }
            assertEquals(CommandAuthority.MISSION_COMMAND,
                    directive.authority());
            assertEquals("silent-colony", directive.issuer());
            commandedMarineSquads++;
        }
        assertEquals(1, commandedMarineSquads);
        assertNotNull(sim.getSquadCommandDirective(commanded.id));

        int defenders = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.DEFENDER) continue;
            defenders++;
            UnitType type = sim.identity().type(unit);
            assertTrue(type == UnitType.TURRET
                    || type == UnitType.DRONE_HUB_STRUCTURE);
            assertFalse(type == UnitType.SWARM_RUNNER);
        }
        assertTrue(defenders > 0);
        assertTrue(sim.getReinforcementService().isEmpty());
    }

    private static Squad addMarineSquad(BattleSimulation sim, String name) {
        int spawnX = -1;
        int spawnY = -1;
        for (int y = 0; y < sim.getGrid().getHeight() && spawnX < 0; y++) {
            for (int x = 0; x < sim.getGrid().getWidth(); x++) {
                if (!sim.getGrid().isWalkable(x, y)) continue;
                spawnX = x;
                spawnY = y;
                break;
            }
        }
        long leader = sim.spawn(new EntitySpec(name,
                Faction.MARINE, UnitType.MARINE, spawnX, spawnY));
        int squadId = sim.mintSquad(Faction.MARINE, leader);
        sim.squad().assignSquad(leader, squadId);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 1;
        squad.centroidX = spawnX + 0.5f;
        squad.centroidY = spawnY + 0.5f;
        return squad;
    }

    @Test
    void hiddenSeedFreezesThreatProfileAndPlacement() {
        BattleSimulation first = BattleSetup.createSilentColony(
                101L, 1L, 8, Collections.emptyList(), RiskLevel.HIGH);
        BattleSimulation replay = BattleSetup.createSilentColony(
                999L, 1L, 8, Collections.emptyList(), RiskLevel.HIGH);

        assertEquals(defenderSignature(first), defenderSignature(replay));
        assertEquals(archiveSignature(first), archiveSignature(replay));
    }

    private static List<String> defenderSignature(BattleSimulation sim) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.DEFENDER) continue;
            result.add(sim.identity().type(unit).name() + ":"
                    + sim.world().cellX(unit) + ":"
                    + sim.world().cellY(unit));
        }
        Collections.sort(result);
        return result;
    }

    private static String archiveSignature(BattleSimulation sim) {
        ColonyArchiveObjective archive = sim.getObjectives().stream()
                .filter(ColonyArchiveObjective.class::isInstance)
                .map(ColonyArchiveObjective.class::cast)
                .findFirst().orElseThrow();
        return archive.cellX() + ":" + archive.cellY();
    }
}
