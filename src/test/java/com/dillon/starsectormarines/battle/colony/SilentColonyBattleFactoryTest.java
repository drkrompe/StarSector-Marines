package com.dillon.starsectormarines.battle.colony;

import com.dillon.starsectormarines.battle.command.SilentColonyCommand;
import com.dillon.starsectormarines.battle.command.objective.ColonyArchiveObjective;
import com.dillon.starsectormarines.battle.command.objective.CivilianEvacuationObjective;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
