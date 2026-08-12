package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CivilianEvacuationMissionOutcomeTest {

    @Test
    void unfinishedCohortDoesNotManufactureARescueReport() {
        BattleSimulation sim = simulation();
        registerFullCohort(sim.getCivilianEvacuationTracker());
        sim.getCivilianEvacuationTracker().markEvacuated(1L);

        MissionOutcome outcome = MissionResolver.compute(
                sim, eventMission(120), null);

        assertEquals(-1, outcome.civiliansRescued);
    }

    @Test
    void sealedPartialCohortScalesIntoMissionOutcomeWithoutUsingVictory() {
        BattleSimulation sim = simulation();
        CivilianEvacuationTracker tracker =
                sim.getCivilianEvacuationTracker();
        registerFullCohort(tracker);
        tracker.markEvacuated(1L);
        tracker.markEvacuated(2L);
        tracker.markEvacuated(3L);
        tracker.seal();

        MissionOutcome outcome = MissionResolver.compute(
                sim, eventMission(120), null);

        assertEquals(false, outcome.victory);
        assertEquals(45, outcome.civiliansRescued);
    }

    @Test
    void sealedZeroIsExplicitButGenericMissionsIgnoreTheTracker() {
        BattleSimulation sim = simulation();
        CivilianEvacuationTracker tracker =
                sim.getCivilianEvacuationTracker();
        registerFullCohort(tracker);
        tracker.seal();

        assertEquals(0, MissionResolver.compute(
                sim, eventMission(120), null).civiliansRescued);

        Mission generic = lineageMission(MissionSource.GENERATED, 120);
        assertEquals(-1, MissionResolver.compute(
                sim, generic, null).civiliansRescued);
    }

    private static BattleSimulation simulation() {
        return new BattleSimulation(new NavigationGrid(8, 8),
                new CellTopology(8, 8));
    }

    private static void registerFullCohort(CivilianEvacuationTracker tracker) {
        for (long id = 1L;
             id <= CivilianEvacuationTracker.V1_REPRESENTATIVE_COUNT;
             id++) {
            tracker.register(id);
        }
    }

    private static Mission eventMission(int civiliansAtRisk) {
        return lineageMission(MissionSource.CAMPAIGN_EVENT, civiliansAtRisk);
    }

    private static Mission lineageMission(MissionSource source,
                                          int civiliansAtRisk) {
        return new Mission("civilian-rescue:7", "Civilian Evacuation",
                MissionType.EXTRACTION, source,
                0, RiskLevel.HIGH, "Committed relief response", "",
                0.5f, 0.5f, FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                4, 0, "Arcadia", null, "independent",
                -1L, 7L, 3, civiliansAtRisk,
                (byte) 0, (byte) 0, (byte) 100,
                (byte) 0, (byte) 0, Collections.emptyList());
    }
}
