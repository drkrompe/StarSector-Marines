package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.command.RescueEscortCommand;
import com.dillon.starsectormarines.battle.command.objective.CivilianEvacuationObjective;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

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
                        == Faction.DEFENDER));
        int defenders = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long entity = sim.liveUnitAt(i);
            if (sim.identity().faction(entity) != Faction.DEFENDER) continue;
            defenders++;
            assertEquals(UnitType.SWARM_RUNNER,
                    sim.identity().type(entity));
        }
        assertEquals(SwarmDefenseRoster.LOW_COUNT, defenders);
        assertTrue(sim.getReinforcementService().isEmpty());
        assertTrue(sim.isSwarmReinforcementConfigured());
        assertTrue(sim.isRescuePickupSupportConfigured());
        assertEquals(8, sim.liveRescuePickupGuards());
        assertEquals(SwarmDefenseRoster.LOW_COUNT,
                sim.swarmTargetPopulation());
        int pickupCraft = 0;
        for (long id : sim.getAirEntityIds()) {
            if (sim.world().airFaction(id) != Faction.CIVILIAN) continue;
            ShuttleMission mission = sim.world().mission(id);
            pickupCraft++;
            assertTrue(mission.awaitingEvacuees);
            assertEquals(8, mission.evacueeCapacity);
            assertEquals(0, mission.marinesRemaining);
        }
        assertEquals(1, pickupCraft);
        assertTrue(sim.getCommander(Faction.MARINE)
                instanceof RescueEscortCommand);
    }

    @Test
    void dedicatedFactoryAcceptsForceScaledDebugSwarm() {
        BattleSimulation sim = BattleSetup.createCivilianRescue(
                5_006L, Collections.emptyList(), false, RiskLevel.LOW, 180);

        int defenders = 0;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long entity = sim.liveUnitAt(i);
            if (sim.identity().faction(entity) == Faction.DEFENDER) defenders++;
        }
        assertEquals(180, defenders);
    }

    @Test
    void forceScaledDebugBattleHasAnOpeningBeforeCivilianDefeat() {
        List<ShuttleAssignment> manifest = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            manifest.add(new ShuttleAssignment(ShuttleType.VALKYRIE, 5));
        }
        int swarmCount = SwarmDefenseRoster.debugCountFor(
                RiskLevel.LOW, 8 * ShuttleType.VALKYRIE.capacity);
        BattleSimulation sim = BattleSetup.createCivilianRescue(
                5_007L, manifest, false, RiskLevel.LOW, swarmCount);

        for (int tick = 0; tick < 90; tick++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertFalse(sim.isComplete());
        assertTrue(sim.getCivilianEvacuationTracker().activeCount() > 0);
    }
}
