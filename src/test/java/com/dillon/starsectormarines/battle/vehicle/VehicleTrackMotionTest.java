package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleTrackMotionTest {
    @ParameterizedTest
    @EnumSource(value = VehicleType.class, names = {"BREAKTHROUGH_TANK", "HEAVY_APC", "LRM_CARRIER"})
    void controllerCountsOnlyTravelAcceptedByTheWallSweep(VehicleType type) {
        var grid = new NavigationGrid(24, 14);
        for (int y = 0; y < 14; y++) for (int x = 0; x < 24; x++) grid.setWalkableFloor(x, y);
        for (int y = 0; y < 14; y++) grid.setWalkable(14, y, false);
        var topology = new CellTopology(24, 14);
        try (var sim = new BattleSimulation(grid, topology)) {
            long tank = sim.convoy().spawn(type, Faction.ALLY,
                    VehicleMission.deployed(9, 7));
            var body = sim.convoy().body(tank);
            body.teleport(9, 7, -90);
            var controller = new VehicleControlSystem(sim.convoy(), new NavigationService(grid, topology));
            for (int tick = 0; tick < 180; tick++) controller.tickManual(tank, 1f / 30, 1, 0);
            assertTrue(body.x > 9);
            assertTrue(body.x < 14);
            assertEquals(body.x - 9, body.visualTravelCells, .0001);
            double stopped = body.visualTravelCells;
            for (int tick = 0; tick < 30; tick++) controller.tickManual(tank, 1f / 30, 1, 0);
            assertEquals(stopped, body.visualTravelCells, .0001);
            for (int tick = 0; tick < 30; tick++) controller.tickManual(tank, 1f / 30, -1, 0);
            assertTrue(body.visualTravelCells < stopped);
        }
    }

    @Test void acceptedTravelStopsAtRestReversesAndResetsOnTeleport() {
        GroundBody body = VehicleType.BREAKTHROUGH_TANK.createBody();
        body.teleport(10, 10, 0);
        body.y = 12;
        body.recordVisualTravel(10, 10, 0);
        assertEquals(2, body.visualTravelCells, .0001);
        // Even a stale nonzero speed cannot animate a blocked chassis.
        body.speed = 3;
        body.recordVisualTravel(10, 12, 0);
        assertEquals(2, body.visualTravelCells, .0001);
        body.y = 11;
        body.recordVisualTravel(10, 12, 0);
        assertEquals(1, body.visualTravelCells, .0001);
        body.teleport(100, 100, 90);
        assertEquals(0, body.visualTravelCells);
        assertEquals(0, body.visualYawRadians);
    }

    @Test void turningUsesTheShortYawAcrossWrapAndDifferentTrackDistances() {
        GroundBody body = VehicleType.BREAKTHROUGH_TANK.createBody();
        body.teleport(10, 10, 359);
        body.facingDegrees = 1;
        body.y = 11;
        body.recordVisualTravel(10, 10, 359);
        assertEquals(1, body.visualTravelCells, .0001);
        assertEquals(Math.toRadians(2), body.visualYawRadians, .0001);
        assertTrue(body.visualTravelCells + body.visualYawRadians
                > body.visualTravelCells - body.visualYawRadians);
    }
}
