package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two questions about leaving a drop point, and why only one of them is
 * safe to dispatch on.
 *
 * <p>Recorded on seed 8919: a road five cells wide at the drop point that opens
 * out a few cells back up the approach. The docking maneuver would have turned
 * the APC round comfortably — it is evaluated from one trigger distance back,
 * in the wide part — so a gate that asked about docking said yes and dispatch
 * committed. Docking is an attempt rather than a guarantee, though; this truck
 * arrived through the plain distance gate instead, and spent the rest of the
 * battle standing on the drop point facing back the way it came.
 *
 * <p>The divergence between the two answers is recorded on that map rather than
 * reproduced here: a fixture contorted until the docking question happens to
 * say yes would be proving the case rather than the contract. What is pinned
 * here is the contract — the strict question refuses a drop point with no room
 * at it, and does not refuse one that has room or one that needs no turn.
 */
class LzDepartureFeasibilityTest {

    private static final float WEST = 90f;
    private static final float EAST = -90f;
    private static final float LZ_X = 28.5f;
    private static final float LZ_Y = 101.5f;

    /**
     * The recorded shape: a five-cell band through the drop point, opening to
     * ten cells from x=32 eastward — which is where the docking maneuver would
     * have been evaluated.
     */
    private static NavigationGrid narrowAtTheDropPointWiderBack() {
        NavigationGrid grid = new NavigationGrid(160, 130);
        for (int y = 100; y <= 104; y++) {
            for (int x = 10; x <= 150; x++) grid.setWalkableFloor(x, y);
        }
        for (int y = 105; y <= 109; y++) {
            for (int x = 32; x <= 150; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    /** An eastbound exit route leaving the drop point. */
    private static float[][] eastboundExit() {
        return new float[][]{
                {LZ_X, 60.5f, 100.5f, 140.5f},
                {LZ_Y, LZ_Y, LZ_Y, LZ_Y}};
    }

    @Test
    void theQuestionWithNoRunUpSaysNo() {
        float[][] exit = eastboundExit();
        assertFalse(VehicleController.canTurnOntoRouteAt(narrowAtTheDropPointWiderBack(),
                        VehicleType.HEAVY_APC, LZ_X, LZ_Y, WEST, exit[0], exit[1]),
                "standing on the drop point itself there is no room to come round,"
                        + " and that is the situation a truck that never docked is in");
    }

    @Test
    void aRoadWideEnoughAtTheDropPointPassesBoth() {
        NavigationGrid wide = new NavigationGrid(160, 130);
        for (int y = 94; y <= 110; y++) {
            for (int x = 10; x <= 150; x++) wide.setWalkableFloor(x, y);
        }
        float[][] exit = eastboundExit();
        assertTrue(VehicleController.canReverseDirectionAt(wide, VehicleType.HEAVY_APC,
                LZ_X, LZ_Y, WEST, EAST));
        assertTrue(VehicleController.canTurnOntoRouteAt(wide, VehicleType.HEAVY_APC,
                        LZ_X, LZ_Y, WEST, exit[0], exit[1]),
                "the strict question must not reject a drop point that genuinely has room");
    }

    @Test
    void anExitTheTruckIsAlreadyPointedDownNeedsNoRoom() {
        // Arriving west and leaving west asks for no turn, so even the narrow
        // band passes. A gate that rejected this would starve ordinary
        // drive-through deliveries.
        float[][] westboundExit = new float[][]{
                {LZ_X, 20.5f, 14.5f}, {LZ_Y, LZ_Y, LZ_Y}};
        assertTrue(VehicleController.canTurnOntoRouteAt(narrowAtTheDropPointWiderBack(),
                        VehicleType.HEAVY_APC, LZ_X, LZ_Y, WEST,
                        westboundExit[0], westboundExit[1]),
                "a straight-through exit needs no room and must not be gated");
    }
}
