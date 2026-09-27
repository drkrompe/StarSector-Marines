package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile.Bucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleWorkProfileTest {
    @AfterEach
    void releaseProfile() { TickInnerProfile.releaseCurrentThread(); }

    @Test
    void boundedHybridSearchReportsSeparateSetupAndExistingExpansionCount() {
        NavigationGrid grid = openGrid(24, 24);
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        assertNull(HybridAStarPlanner.planLocal(new Pose(8.5f, 8.5f, 0f),
                new Pose(8.5f, 18.5f, 0f), 1.5f,
                4, 4, 20, 22, 1, VehicleType.HEAVY_APC, grid));
        assertEquals(1, profile.countOf(Bucket.VEHICLE_LOCAL_HEURISTIC));
        assertEquals(1, profile.countOf(Bucket.VEHICLE_LOCAL_LATTICE));
        assertEquals(1, profile.countOf(Bucket.VEHICLE_LOCAL_EXPANDED));
        assertEquals(24 * 24, profile.countOf(Bucket.VEHICLE_HEURISTIC_STORAGE_CELL),
                "the allocation is map-sized even when the flood window is smaller");
        assertEquals(0, profile.nanosOf(Bucket.VEHICLE_LOCAL_EXPANDED));
        assertEquals(0, profile.countOf(Bucket.CONVOY_ROUTE_PROOF_STEP),
                "controller work must not masquerade as dispatch proof");
    }

    @Test
    void rejectedLocalGoalReportsOneFailureWithoutInventingSearchWork() {
        NavigationGrid grid = openGrid(24, 24);
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        ReferenceCorridor corridor = new ReferenceCorridor(
                new float[]{20.5f, 40f}, new float[]{12.5f, 12.5f}, 1);
        assertNull(LocalTrajectoryPlanner.plan(new Pose(20.5f, 12.5f, 270f),
                corridor, VehicleType.HEAVY_APC, grid));
        assertEquals(1, profile.countOf(Bucket.VEHICLE_LOCAL_PLAN));
        assertEquals(1, profile.countOf(Bucket.VEHICLE_LOCAL_NO_TRAJECTORY));
        assertEquals(0, profile.countOf(Bucket.VEHICLE_LOCAL_HEURISTIC));
        assertEquals(0, profile.countOf(Bucket.VEHICLE_LOCAL_EXPANDED));
    }

    @Test
    void recoveryCountsOnDemandSearchesSeparatelyFromDispatchProof() {
        NavigationGrid grid = openGrid(24, 24);
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        VehicleRoutePlanner.RescueRoute route = VehicleRoutePlanner.routeAvoidingForwardFirstOnDemand(
                8, 8, 8, 18, 0f, 0, grid, index -> 1f, index -> true,
                new int[0], new int[0], 0, 1f, VehicleType.HEAVY_APC);
        assertNotNull(route);
        assertTrue(profile.countOf(Bucket.VEHICLE_RECOVERY_ATTEMPT) > 0);
        assertTrue(profile.countOf(Bucket.VEHICLE_RECOVERY_EXPANDED) > 0);
        assertEquals(0, profile.nanosOf(Bucket.VEHICLE_RECOVERY_EXPANDED));
        assertEquals(0, profile.countOf(Bucket.CONVOY_ROUTE_PROOF_STEP));
    }

    @Test
    void standalonePlannerDoesNotCreateAProcessGlobalProfile() {
        TickInnerProfile.releaseCurrentThread();
        HybridAStarPlanner.planLocal(new Pose(8.5f, 8.5f, 0f),
                new Pose(8.5f, 18.5f, 0f), 1.5f,
                4, 4, 20, 22, 1, VehicleType.HEAVY_APC, openGrid(24, 24));
        assertNull(TickInnerProfile.currentIfBound());
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
