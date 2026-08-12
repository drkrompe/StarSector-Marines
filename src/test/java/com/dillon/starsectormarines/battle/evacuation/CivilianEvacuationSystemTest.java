package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivilianEvacuationSystemTest {

    @Test
    void installedCohortRoutesBoardsAndCompletesWithoutCombatVictoryInference() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload =
                CivilianEvacuationPayload.install(sim,
                        List.of(residential(12, 10)), 300L);
        assertNotNull(payload);

        for (int tick = 0; tick < 1_200 && !sim.isComplete(); tick++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertTrue(sim.isComplete());
        assertEquals(Faction.MARINE, sim.getWinner());
        CivilianEvacuationReport report =
                sim.getCivilianEvacuationTracker().report();
        assertNotNull(report);
        assertEquals(8, report.evacuated);
        assertEquals(0, report.lost);
        assertEquals(0, sim.liveUnitCount());
        for (int i = 0; i < payload.size(); i++) {
            assertEquals(0L, sim.resolveUnit(payload.entityId(i)));
            assertFalse(sim.getCivilianEvacuationTracker()
                    .state(payload.entityId(i))
                    == CivilianEvacuationTracker.State.LOST);
        }
    }

    @Test
    void boardingCreatesNoCorpseOrLoss() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload =
                CivilianEvacuationPayload.install(sim,
                        List.of(residential(12, 10)), 301L);
        assertNotNull(payload);

        long first = payload.entityId(0);
        sim.world().setCellPos(first, payload.placement.liftX,
                payload.placement.liftY);
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(CivilianEvacuationTracker.State.EVACUATED,
                sim.getCivilianEvacuationTracker().state(first));
        assertEquals(0L, sim.resolveUnit(first));
        assertEquals(0, sim.getCivilianEvacuationTracker().lostCount());
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(26, 22);
        for (int y = 0; y < 22; y++) {
            for (int x = 0; x < 26; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(26, 22));
    }

    private static PointOfInterest residential(int x, int y) {
        return new PointOfInterest(PointOfInterest.Kind.RESIDENTIAL,
                x - 2, y - 2, x + 2, y + 2, x, y, x, y);
    }
}
