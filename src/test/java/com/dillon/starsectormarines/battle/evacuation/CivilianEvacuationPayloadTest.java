package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CivilianEvacuationPayloadTest {

    @Test
    void installsExactlyOneCompleteMissionOnlyCohortAndObjective() {
        BattleSimulation sim = simulation();

        CivilianEvacuationPayload payload =
                CivilianEvacuationPayload.install(sim,
                        List.of(residential(10, 8)), 71L);

        assertNotNull(payload);
        assertEquals(8, payload.size());
        assertEquals(8, sim.getCivilianEvacuationTracker().registeredCount());
        assertEquals(1, sim.getObjectives().size());
        assertSame(payload.objective, sim.getObjectives().get(0));
        int engineers = 0;
        int scientists = 0;
        for (int i = 0; i < payload.size(); i++) {
            long id = payload.entityId(i);
            assertEquals(Faction.CIVILIAN, sim.identity().faction(id));
            assertEquals(UnitRole.VIP, sim.role().role(id));
            if (sim.identity().type(id) == UnitType.ENGINEER) engineers++;
            if (sim.identity().type(id) == UnitType.SCIENTIST) scientists++;
            assertEquals(payload.placement.spawnX(i), sim.world().cellX(id));
            assertEquals(payload.placement.spawnY(i), sim.world().cellY(id));
        }
        assertEquals(1, engineers);
        assertEquals(1, scientists);

        assertNull(CivilianEvacuationPayload.install(sim,
                List.of(residential(4, 4)), 72L));
        assertEquals(8, sim.getCivilianEvacuationTracker().registeredCount());
        assertEquals(1, sim.getObjectives().size());
    }

    @Test
    void failedPlanningLeavesSimulationUntouched() {
        BattleSimulation sim = simulation();

        assertNull(CivilianEvacuationPayload.install(sim,
                List.of(new PointOfInterest(PointOfInterest.Kind.DEPOT,
                        4, 4, 8, 8, 6, 6, 6, 6)), 1L));

        assertEquals(0, sim.liveUnitCount());
        assertEquals(0, sim.getCivilianEvacuationTracker().registeredCount());
        assertEquals(0, sim.getObjectives().size());
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(22, 18);
        for (int y = 0; y < 18; y++) {
            for (int x = 0; x < 22; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(22, 18));
    }

    private static PointOfInterest residential(int x, int y) {
        return new PointOfInterest(PointOfInterest.Kind.RESIDENTIAL,
                x - 2, y - 2, x + 2, y + 2, x, y, x, y);
    }
}
