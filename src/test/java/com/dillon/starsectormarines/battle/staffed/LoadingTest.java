package com.dillon.starsectormarines.battle.staffed;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadingTest {

    /**
     * A 40x40 yard of open floor with a walled building at x 4..14, y 4..14: one door in its east
     * wall at (14, 9) onto the yard, and one in its north wall at (9, 4) into a one-cell pocket
     * walled off from everything.
     */
    private static NavigationGrid yard() {
        NavigationGrid grid = new NavigationGrid(40, 40);
        for (int x = 0; x < 40; x++) for (int y = 0; y < 40; y++) grid.setWalkableFloor(x, y);
        for (int x = 4; x <= 14; x++) for (int y = 4; y <= 14; y++) {
            boolean wall = x == 4 || x == 14 || y == 4 || y == 14;
            if (wall) grid.setWalkable(x, y, false);
        }
        // The north door opens into a one-cell pocket walled off from the rest of the yard.
        for (int x = 0; x < 40; x++) for (int y = 0; y <= 3; y++) grid.setWalkable(x, y, false);
        grid.setWalkableFloor(9, 3);
        grid.setWalkableFloor(9, 4);
        grid.setDoorway(9, 4, true);
        grid.setWalkableFloor(14, 9);
        grid.setDoorway(14, 9, true);
        return grid;
    }

    private static final PointOfInterest BUILDING =
            new PointOfInterest(PointOfInterest.Kind.DEPOT, 4, 4, 14, 14, 15, 9, 9, 9);

    @Test
    void theTruckStandsAtTheDoorAFootprintFitsOutside() {
        NavigationGrid grid = yard();
        VehicleClearance clearance = VehicleClearance.erode(grid,
                VehicleClearance.radiusForWidth(VehicleType.CARGO_CARRIER.visualWidthCells));
        int[][] parking = Loading.parking(grid, clearance, BUILDING);
        assertNotNull(parking);
        assertEquals(15, parking[0][0], "loads through the east door");
        assertEquals(9, parking[0][1]);
        assertTrue(clearance.isPassable(parking[1][0], parking[1][1]), "parks where a truck fits");
    }

    @Test
    void aBuildingWithNoDoorATruckCanReachHasNoParking() {
        NavigationGrid grid = yard();
        grid.setDoorway(14, 9, false);
        grid.setWalkable(14, 9, false);
        VehicleClearance clearance = VehicleClearance.erode(grid,
                VehicleClearance.radiusForWidth(VehicleType.CARGO_CARRIER.visualWidthCells));
        assertNull(Loading.parking(grid, clearance, BUILDING));
    }

    @Test
    void cratesAreSetDownBehindTheTruckApartAndNeverUnderIt() {
        NavigationGrid grid = yard();
        int[] park = {22, 9};
        // Facing east, away from the door: the tailgate is the west end.
        List<float[]> places = Loading.tailgate(grid, park, new int[]{15, 9}, 0f);
        assertEquals(Loading.TAILGATE_PLACES, places.size());
        float half = VehicleType.CARGO_CARRIER.visualLengthCells / 2f;
        for (float[] p : places) {
            assertTrue(p[0] < park[0] + .5f - half, "behind the truck's rear: " + p[0]);
        }
        float[] a = places.get(0), b = places.get(1);
        assertFalse(Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) < 1.5f, "two places, not one");
    }
}
