package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.task.TaskPoint;
import com.dillon.starsectormarines.battle.task.TaskPointService;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JobBoardFocusResolverTest {

    private record Site(int id, RoomPurpose purpose, int centreX, int centreY)
            implements JobSite {
        @Override
        public boolean contains(int cellX, int cellY) {
            return true;
        }
    }

    @Test
    void runtimePlacementMovesTheStandAndContactTogether() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        }
        TaskPointService points = new TaskPointService(grid);
        FixtureTask task = FixtureTask.servingBerth(2, 3, 0, 8, 8);
        Site bay = new Site(7, RoomPurpose.VEHICLE_BAY, 6, 6);

        JobBoard.publish(points, List.of(task), bay, new boolean[]{true},
                ignored -> new JobBoard.Placement(4.5f, 6.5f, 5.25f, 6.75f));
        TaskPoint published = points.claimNearest(
                1L, JobBoard.group(bay.id(), task.affordance(), task.berth()), 0f, 0f);

        assertNotNull(published);
        assertEquals(4.5f, published.worldX(), 0.001f);
        assertEquals(6.5f, published.worldY(), 0.001f);
        assertEquals(5.25f, published.focusX(), 0.001f);
        assertEquals(6.75f, published.focusY(), 0.001f);
    }

    @Test
    void fixtureWorkRetainsItsAuthoredFocusWhenTheHostDeclinesToResolveIt() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        }
        TaskPointService points = new TaskPointService(grid);
        FixtureTask task = FixtureTask.at(2, 3, Affordance.FABRICATE, 8, 9);
        Site bay = new Site(8, RoomPurpose.VEHICLE_BAY, 6, 6);

        JobBoard.publish(points, List.of(task), bay, new boolean[0], ignored -> null);
        TaskPoint published = points.claimNearest(
                1L, JobBoard.group(bay.id(), task.affordance()), 0f, 0f);

        assertNotNull(published);
        assertEquals(8.5f, published.focusX(), 0.001f);
        assertEquals(9.5f, published.focusY(), 0.001f);
    }

    @Test
    void workAtAFixtureStandsUpAgainstItAndASentryKeepsItsCellCentre() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        }
        TaskPointService points = new TaskPointService(grid);
        FixtureTask console = FixtureTask.at(4, 6, Affordance.WATCH, 5, 6);
        FixtureTask hatch = FixtureTask.at(4, 2, Affordance.GUARD, 4, 1);
        Site bridge = new Site(9, RoomPurpose.BRIDGE, 6, 6);

        JobBoard.publish(points, List.of(console, hatch), bridge, new boolean[0], ignored -> null);
        TaskPoint watch = points.claimNearest(1L, JobBoard.group(bridge.id(), Affordance.WATCH), 0f, 0f);
        TaskPoint guard = points.claimNearest(2L, JobBoard.group(bridge.id(), Affordance.GUARD), 0f, 0f);

        assertEquals(4.5f + JobBoard.FIXTURE_LEAN_CELLS, watch.worldX(), 0.001f);
        assertEquals(6.5f, watch.worldY(), 0.001f);
        assertEquals(4.5f, guard.worldX(), 0.001f);
        assertEquals(2.5f, guard.worldY(), 0.001f);
    }
}
