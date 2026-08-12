package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.command.objective.CivilianEvacuationObjective;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivilianRescueBattleFactoryTest {

    @Test
    void dedicatedFactoryInstallsRealEvacuationPayload() {
        BattleSimulation sim = BattleSetup.createCivilianRescue(
                5_005L, Collections.emptyList(), false, RiskLevel.LOW);

        assertEquals(8,
                sim.getCivilianEvacuationTracker().registeredCount());
        assertFalse(sim.getCivilianEvacuationTracker().isSealed());
        assertEquals(1, sim.getObjectives().stream()
                .filter(CivilianEvacuationObjective.class::isInstance)
                .count());
        assertTrue(sim.getObjectives().stream()
                .anyMatch(objective -> objective.owningFaction()
                        == com.dillon.starsectormarines.battle.unit.Faction.DEFENDER));
    }
}
