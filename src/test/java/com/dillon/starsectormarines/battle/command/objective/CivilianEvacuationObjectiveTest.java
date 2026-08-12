package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationReport;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivilianEvacuationObjectiveTest {

    @Test
    void onlyRegisteredCiviliansCrossingTheZoneCount() {
        BattleSimulation sim = simulation();
        CivilianEvacuationTracker tracker =
                sim.getCivilianEvacuationTracker();
        CivilianEvacuationObjective objective =
                new CivilianEvacuationObjective(tracker, 6, 6, 0);
        long[] cohort = spawnCohort(sim, tracker, 1, 1);
        spawnCivilian(sim, 6, 6, "Ambient");

        sim.world().setCellPos(cohort[0], 6, 6);
        objective.tick(sim);

        assertEquals(1, tracker.evacuatedCount());
        assertEquals(7, tracker.activeCount());
        assertFalse(objective.isComplete());
        assertFalse(objective.isFailed());
    }

    @Test
    void partialEvacuationCompletesAfterEveryOtherMemberIsLost() {
        BattleSimulation sim = simulation();
        CivilianEvacuationTracker tracker =
                sim.getCivilianEvacuationTracker();
        CivilianEvacuationObjective objective =
                new CivilianEvacuationObjective(tracker, 6, 6, 1);
        long[] cohort = spawnCohort(sim, tracker, 1, 1);

        sim.world().setCellPos(cohort[0], 5, 6);
        sim.world().setCellPos(cohort[1], 7, 7);
        for (int i = 2; i < cohort.length; i++) {
            sim.releaseFromRegistry(cohort[i]);
        }
        objective.tick(sim);

        assertTrue(objective.isComplete());
        assertFalse(objective.isFailed());
        CivilianEvacuationReport report = tracker.report();
        assertEquals(8, report.initial);
        assertEquals(2, report.evacuated);
        assertEquals(6, report.lost);
    }

    @Test
    void measuredZeroEvacuationFailsInsteadOfBecomingNoReport() {
        BattleSimulation sim = simulation();
        CivilianEvacuationTracker tracker =
                sim.getCivilianEvacuationTracker();
        CivilianEvacuationObjective objective =
                new CivilianEvacuationObjective(tracker, 6, 6, 0);
        long[] cohort = spawnCohort(sim, tracker, 1, 1);
        for (long id : cohort) sim.releaseFromRegistry(id);

        objective.tick(sim);

        assertFalse(objective.isComplete());
        assertTrue(objective.isFailed());
        assertEquals(0, tracker.report().evacuated);
        assertEquals(8, tracker.report().lost);
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(10, 10);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(10, 10));
    }

    private static long[] spawnCohort(BattleSimulation sim,
                                      CivilianEvacuationTracker tracker,
                                      int x, int y) {
        long[] ids = new long[CivilianEvacuationTracker.V1_REPRESENTATIVE_COUNT];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = spawnCivilian(sim, x + i % 2, y + i / 2,
                    "Evacuee " + (i + 1));
            assertTrue(tracker.register(ids[i]));
        }
        return ids;
    }

    private static long spawnCivilian(BattleSimulation sim, int x, int y,
                                       String name) {
        return sim.spawn(new EntitySpec(name, Faction.CIVILIAN,
                UnitType.CIVILIAN, x, y).role(UnitRole.FLEE));
    }
}
