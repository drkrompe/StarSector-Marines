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
    void runtimeBodyFocusDoesNotMoveTheAuthoredPlaceToStand() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        }
        TaskPointService points = new TaskPointService(grid);
        FixtureTask task = FixtureTask.servingBerth(2, 3, 0, 8, 8);
        Site bay = new Site(7, RoomPurpose.VEHICLE_BAY, 6, 6);

        JobBoard.publish(points, List.of(task), bay, new boolean[]{true},
                ignored -> new JobBoard.Focus(5.25f, 6.75f));
        TaskPoint published = points.claimNearest(
                1L, JobBoard.group(bay.id(), task.affordance(), task.berth()), 0f, 0f);

        assertNotNull(published);
        assertEquals(2.5f, published.worldX(), 0.001f);
        assertEquals(3.5f, published.worldY(), 0.001f);
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
}
