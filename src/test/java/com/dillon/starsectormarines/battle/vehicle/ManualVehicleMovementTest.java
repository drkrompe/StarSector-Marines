package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualVehicleMovementTest {
    private static final float DT = 1f / 30f;

    @Test
    void manualWheelInputRetainsAccelerationSlewAndReverseYaw() {
        BicycleBody forward = (BicycleBody) VehicleType.HEAVY_APC.createBody();
        forward.teleport(10f, 10f, 0f);
        forward.tickManual(1f, 1f, DT);
        assertEquals(VehicleType.HEAVY_APC.accel * DT, forward.speed, .00001f);
        assertEquals(Math.toRadians(150f * DT), forward.getSteeringRad(), .00001f);
        assertTrue(forward.facingDegrees > 0f);
        BicycleBody reverse = (BicycleBody) VehicleType.HEAVY_APC.createBody();
        reverse.teleport(10f, 10f, 0f);
        reverse.tickManual(-1f, 1f, DT);
        assertTrue(reverse.speed < 0f);
        assertTrue(reverse.facingDegrees < 0f, "the same wheel angle reverses yaw when backing");
        for (int i = 0; i < 90; i++) reverse.tickManual(-1f, 0f, DT);
        assertEquals(-VehicleType.HEAVY_APC.maxSpeed * .5f, reverse.speed, .00001f);
        for (int i = 0; i < 90; i++) reverse.tickManual(0f, 0f, DT);
        assertEquals(0f, reverse.speed);
        assertEquals(0f, reverse.getSteeringRad());
    }

    @Test
    void manualControllerStopsAtAClosedEdgeWithoutBankingSpeedThenReversesOrDrivesThroughOpening() {
        Fixture f = new Fixture();
        f.body.teleport(3f, 6f, -90f);
        for (int y = 0; y < 20; y++) f.grid.blockSharedEdge(7, y, Direction.E);
        for (int i = 0; i < 180; i++) f.controls.tickManual(f.id, DT, 1f, 0f);
        assertTrue(f.body.x > 6.7f && f.body.x <= 6.8f);
        assertEquals(0f, f.body.speed);
        assertTrue(f.legal());
        float stoppedX = f.body.x;
        f.controls.tickManual(f.id, DT, 0f, 0f);
        assertEquals(stoppedX, f.body.x);
        for (int i = 0; i < 30; i++) f.controls.tickManual(f.id, DT, -1f, 0f);
        assertTrue(f.body.x < stoppedX - .3f);
        for (int y = 0; y < 20; y++) f.grid.openSharedEdge(7, y, Direction.E);
        for (int i = 0; i < 120; i++) f.controls.tickManual(f.id, DT, 1f, 0f);
        assertTrue(f.body.x > 8f);
        assertTrue(f.legal());
    }

    @Test
    void deployedManualDriveHasSolidMapBoundsAndNeutralBraking() {
        Fixture f = new Fixture();
        f.body.teleport(17f, 6f, -90f);
        f.body.speed = 2f;
        f.controls.tickManual(f.id, DT, 0f, 0f);
        assertEquals(2f - VehicleType.HEAVY_APC.brakingAccel * DT, f.body.speed, .00001f);
        for (int i = 0; i < 120; i++) f.controls.tickManual(f.id, DT, 1f, 0f);
        assertTrue(f.body.x <= 20f - VehicleType.HEAVY_APC.visualLengthCells * .5f);
        assertEquals(0f, f.body.speed);
        assertTrue(f.legal());
    }

    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(20, 20);
        final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(20, 20), null);
        final ConvoyService convoy = roster.convoy();
        final VehicleControlSystem controls = new VehicleControlSystem(convoy,
                new NavigationService(grid, new CellTopology(20, 20)));
        final long id;
        final GroundBody body;

        Fixture() {
            for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
            id = convoy.spawn(VehicleType.HEAVY_APC, Faction.MARINE,
                    new VehicleMission(new float[]{3f, 15f}, new float[]{6f, 6f},
                            new float[]{15f, 3f}, new float[]{6f, 6f}, 0f, 0));
            body = convoy.body(id);
        }

        boolean legal() {
            return VehicleFootprint.isPoseFeasible(body.x, body.y, body.facingDegrees,
                    VehicleType.HEAVY_APC.visualLengthCells, VehicleType.HEAVY_APC.visualWidthCells, grid);
        }
    }
}
