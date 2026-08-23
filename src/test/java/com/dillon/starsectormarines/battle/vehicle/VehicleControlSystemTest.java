package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleControlSystemTest {

    @Test
    void onGridPlannerFailureHoldsInsteadOfDrivingTheCoarseElbow() {
        NavigationGrid grid = new NavigationGrid(30, 30);
        carve(grid, 10, 0, 12, 12);
        carve(grid, 10, 10, 25, 12);
        NavigationService navigation = new NavigationService(grid, new CellTopology(30, 30));
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(30, 30), null);
        ConvoyService convoy = roster.convoy();
        VehicleMission mission = new VehicleMission(
                new float[]{11.5f, 11.5f, 24.5f},
                new float[]{3.5f, 11.5f, 11.5f},
                new float[]{24.5f, 11.5f},
                new float[]{11.5f, 3.5f},
                0f, 4);
        long id = convoy.spawn(VehicleType.HEAVY_APC, Faction.DEFENDER, mission);
        VehicleControlSystem controls = new VehicleControlSystem(convoy, navigation);
        GroundBody body = convoy.body(id);

        for (int i = 0; i < 20; i++) controls.tick(id, 0.1f, true);

        assertFalse(convoy.control(id).hasTrajectory(), "the elbow has no executable forward trajectory");
        assertEquals(11.5f, body.x, 0.001f,
                "an on-grid null plan must not fall through to coarse-corridor pursuit");
        assertEquals(3.5f, body.y, 0.001f);
        assertEquals(0f, body.speed, 0.001f);
        assertTrue(convoy.control(id).localPlanFailureRerouteAttempted,
                "an uninterrupted null plan makes only its immediate re-route attempt once");
    }

    private static void carve(NavigationGrid grid, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) grid.setWalkableFloor(x, y);
        }
    }
}
