package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile.Bucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Exact start-footprint rejection, not a substitute for proving the forward motion. */
class HybridAStarPlannerStartTest {
    private static final VehicleType TYPE = VehicleType.HEAVY_APC;
    private static final int SIZE = 24;
    private TickInnerProfile previous;
    private TickInnerProfile profile;

    @BeforeEach void bindProfile() {
        previous = TickInnerProfile.currentIfBound();
        profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
    }

    @AfterEach void restoreProfile() { TickInnerProfile.setCurrent(previous); }

    @Test void legalChassisWithInvalidTrackingPadSkipsAllHeuristicStorage() {
        NavigationGrid grid = openGrid();
        grid.setWalkable(7, 8, false);
        Pose start = new Pose(6.1f, 8.5f, 0f);
        assertTrue(chassisClear(start, grid));

        assertNull(plan(start, grid, true));

        assertRefusal(0, 1, 0, 1);
        assertNoSearchWork();
    }

    @Test void paddedMapEdgeFailureIsNotTerrainOrAnInvalidPhysicalChassis() {
        NavigationGrid grid = openGrid();
        Pose start = new Pose(.85f, 8.5f, 0f);
        assertTrue(chassisClear(start, grid));

        assertNull(plan(start, grid, true));

        assertRefusal(0, 1, 1, 0);
        assertNoSearchWork();
    }

    @Test void actualChassisOverlapIsDistinguishedFromPaddingOnlyFailure() {
        NavigationGrid grid = openGrid();
        grid.setWalkable(8, 8, false);
        Pose start = new Pose(8.5f, 8.5f, 0f);
        assertFalse(chassisClear(start, grid));

        assertNull(plan(start, grid, true));

        assertRefusal(1, 0, 0, 1);
        assertNoSearchWork();
    }

    @Test void reciprocalClosedEdgeCanInvalidateOnlyTheTrackingPad() {
        NavigationGrid grid = openGrid();
        // Only the far cell refuses this edge; the near cell remains open.
        grid.setEdgePassable(7, 8, Direction.W, false);
        Pose start = new Pose(6.1f, 8.5f, 0f);
        assertTrue(chassisClear(start, grid));

        assertNull(plan(start, grid, true));

        assertRefusal(0, 1, 0, 1);
        assertNoSearchWork();
    }

    @Test void validStartRetainsTheSameSuccessfulTrajectoryAsTheControl() {
        NavigationGrid grid = openGrid();
        Pose start = new Pose(8.5f, 8.5f, 0f);
        float[][] subject = plan(start, grid, true);
        float[][] control = plan(start, grid, false);

        assertNotNull(subject);
        assertNotNull(control);
        for (int axis = 0; axis < 3; axis++) assertArrayEquals(control[axis], subject[axis]);
        assertEquals(0, profile.countOf(Bucket.VEHICLE_LOCAL_INVALID_START));
        assertEquals(2, profile.countOf(Bucket.VEHICLE_LOCAL_HEURISTIC));
    }

    @Test void validStartWithEveryFirstMoveBlockedStillRunsTheSearch() {
        NavigationGrid grid = openGrid();
        for (int x = 0; x < SIZE; x++) grid.setWalkable(x, 11, false);
        Pose start = new Pose(8.5f, 8.5f, 0f);
        assertTrue(VehicleFootprint.isPoseFeasible(start.x, start.y, start.facingDeg,
                TYPE.visualLengthCells + HybridAStarPlanner.PLANNER_CLEARANCE,
                TYPE.visualWidthCells + HybridAStarPlanner.PLANNER_CLEARANCE, grid));

        assertNull(plan(start, grid, true));

        assertEquals(0, profile.countOf(Bucket.VEHICLE_LOCAL_INVALID_START));
        assertEquals(1, profile.countOf(Bucket.VEHICLE_LOCAL_EXPANDED));
        assertEquals(SIZE * SIZE, profile.countOf(Bucket.VEHICLE_HEURISTIC_STORAGE_CELL));
    }

    @Test void startOutsideTheSearchWindowCanStillEnterItWithoutChangingTheTrajectory() {
        NavigationGrid grid = openGrid();
        Pose start = new Pose(8.5f, 8.5f, 0f);
        Pose goal = new Pose(8.5f, 18.5f, 0f);
        // Successors, not the starting pose, must belong to the search window.
        float[][] subject = HybridAStarPlanner.planLocal(start, goal, 1.5f,
                0, 10, SIZE - 1, SIZE - 1, 4000, TYPE, grid, true);
        float[][] control = HybridAStarPlanner.planLocal(start, goal, 1.5f,
                0, 10, SIZE - 1, SIZE - 1, 4000, TYPE, grid, false);

        assertNotNull(subject);
        assertNotNull(control);
        for (int axis = 0; axis < 3; axis++) assertArrayEquals(control[axis], subject[axis]);
        assertEquals(0, profile.countOf(Bucket.VEHICLE_LOCAL_INVALID_START));
    }

    @Test void controlPaysTheOldWorkForTheSameNullAnswerAndReportsTheSameCause() {
        NavigationGrid grid = openGrid();
        grid.setWalkable(7, 8, false);
        Pose start = new Pose(6.1f, 8.5f, 0f);

        assertNull(plan(start, grid, false));

        assertRefusal(0, 1, 0, 1);
        assertEquals(1, profile.countOf(Bucket.VEHICLE_LOCAL_HEURISTIC));
        assertEquals(1, profile.countOf(Bucket.VEHICLE_LOCAL_LATTICE));
        assertEquals(1, profile.countOf(Bucket.VEHICLE_LOCAL_EXPANDED));
        assertEquals(SIZE * SIZE, profile.countOf(Bucket.VEHICLE_HEURISTIC_STORAGE_CELL));
    }

    @Test void outerLocalPlanStillReportsOneNoTrajectory() {
        NavigationGrid grid = openGrid();
        grid.setWalkable(7, 8, false);
        ReferenceCorridor corridor = new ReferenceCorridor(
                new float[]{6.1f, 6.1f}, new float[]{8.5f, 22f}, 1);

        assertNull(LocalTrajectoryPlanner.plan(new Pose(6.1f, 8.5f, 0f), corridor, TYPE, grid));

        assertEquals(1, profile.countOf(Bucket.VEHICLE_LOCAL_PLAN));
        assertEquals(1, profile.countOf(Bucket.VEHICLE_LOCAL_NO_TRAJECTORY));
        assertEquals(1, profile.countOf(Bucket.VEHICLE_LOCAL_INVALID_START));
    }

    private static float[][] plan(Pose start, NavigationGrid grid, boolean rejectInvalidStart) {
        return HybridAStarPlanner.planLocal(start, new Pose(start.x, 18.5f, 0f), 1.5f,
                0, 0, SIZE - 1, SIZE - 1, 4000, TYPE, grid, rejectInvalidStart);
    }

    private void assertRefusal(int chassis, int padding, int bounds, int terrain) {
        assertEquals(1, profile.countOf(Bucket.VEHICLE_LOCAL_INVALID_START));
        assertEquals(chassis, profile.countOf(Bucket.VEHICLE_LOCAL_INVALID_CHASSIS_START));
        assertEquals(padding, profile.countOf(Bucket.VEHICLE_LOCAL_PADDING_ONLY_START));
        assertEquals(bounds, profile.countOf(Bucket.VEHICLE_LOCAL_START_OUT_OF_BOUNDS));
        assertEquals(terrain, profile.countOf(Bucket.VEHICLE_LOCAL_START_TERRAIN));
    }

    private void assertNoSearchWork() {
        assertEquals(0, profile.countOf(Bucket.VEHICLE_LOCAL_HEURISTIC));
        assertEquals(0, profile.countOf(Bucket.VEHICLE_LOCAL_LATTICE));
        assertEquals(0, profile.countOf(Bucket.VEHICLE_LOCAL_EXPANDED));
        assertEquals(0, profile.countOf(Bucket.VEHICLE_HEURISTIC_STORAGE_CELL));
    }

    private static boolean chassisClear(Pose start, NavigationGrid grid) {
        return VehicleFootprint.isPoseFeasible(start.x, start.y, start.facingDeg,
                TYPE.visualLengthCells, TYPE.visualWidthCells, grid);
    }

    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
