package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RescuePickupSupportSystemTest {

    @Test
    void casualtiesDispatchCappedMilitiaShuttleWave() {
        BattleSimulation sim = simulation();
        CivilianEvacuationPayload payload = CivilianEvacuationPayload.install(
                sim, List.of(residential()), 901L);
        assertNotNull(payload);
        RescuePickupSupportSystem support = new RescuePickupSupportSystem(
                sim.getCivilianEvacuationTracker());
        assertTrue(support.configure(payload.placement,
                10.5f, 10.5f, -6f, 10.5f, -10f, 10.5f, sim));
        assertEquals(RescuePickupSupportSystem.TARGET_GUARDS,
                support.liveGuardCount(sim));

        List<Long> guards = new ArrayList<>();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().type(unit) == UnitType.MILITIA) guards.add(unit);
        }
        for (int i = 0; i < 4; i++) sim.releaseFromRegistry(guards.get(i));

        support.tick(RescuePickupSupportSystem.WAVE_INTERVAL_SECONDS, sim);

        assertEquals(4, support.liveGuardCount(sim));
        int transports = 0;
        for (long id : sim.getAirEntityIds()) {
            ShuttleMission mission = sim.world().mission(id);
            if (!mission.rescueMilitiaTransport) continue;
            transports++;
            assertEquals(4, mission.marinesRemaining);
            assertEquals(UnitType.MILITIA, mission.deboardUnitType);
            assertEquals(payload.placement.liftX, mission.rescueGuardX);
            assertEquals(payload.placement.liftY, mission.rescueGuardY);
        }
        assertEquals(1, transports);
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(26, 22);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(26, 22));
    }

    private static PointOfInterest residential() {
        return new PointOfInterest(PointOfInterest.Kind.RESIDENTIAL,
                10, 8, 14, 12, 12, 10, 12, 10);
    }
}
