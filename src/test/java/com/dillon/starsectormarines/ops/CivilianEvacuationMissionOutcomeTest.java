package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;


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
        return Mission.builder()
                .id("civilian-rescue:7")
                .name("Civilian Evacuation")
                .type(MissionType.EXTRACTION)
                .source(source)
                .risk(RiskLevel.HIGH)
                .requirements("Committed relief response")
                .mapPosition(0.5f, 0.5f)
                .requiredDrops(4)
                .targetPlanetName("Arcadia")
                .targetFactionId("independent")
                .campaignEventId(7L)
                .campaignEventMarketId(3)
                .civiliansAtRisk(civiliansAtRisk)
                .build();
    }
}
