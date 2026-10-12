package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class InstallationFloorsTest {
    @Test void finishFollowsRotatedConcaveFloorAndAperturesWithoutChangingNavigationOrAuthoredSurfaces() {
        for (RoomPose pose : RoomPose.all()) {
            var grid = new NavigationGrid(12, 12);
            var topology = new CellTopology(12, 12);
            var ctx = new GenContext(grid, topology, new Random(42), 12, 12, 42);
            var shape = RoomShape.of("####", "##..", "##..").posed(pose);
            var room = new RoomPacker.Placed(shape, pose, 4, 4, RoomPurpose.CONTROL_ROOM, List.of());
            for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
            for (int[] cell : shape.wall()) {
                grid.setWalkable(4 + cell[0], 4 + cell[1], false);
                grid.setWallHp(4 + cell[0], 4 + cell[1], 100);
            }
            int[] aperture = shape.wall()[0], fixture = shape.filled()[0], authored = shape.filled()[1];
            grid.setWalkableFloor(4 + aperture[0], 4 + aperture[1]);
            topology.setWindow(4 + aperture[0], 4 + aperture[1], true);
            grid.setWalkable(4 + fixture[0], 4 + fixture[1], false);
            topology.setFixture(4 + fixture[0], 4 + fixture[1], true);
            topology.setSurface(4 + authored[0], 4 + authored[1], topology.surfaceIndex("floor.authored"));
            var kinds = topology.copyGroundKinds();
            InstallationFloors.dress(ctx, List.of(room));
            assertArrayEquals(kinds, topology.copyGroundKinds());
            assertEquals("installation.command-floor", topology.getSurfaceId(4 + aperture[0], 4 + aperture[1]));
            assertEquals("installation.command-floor", topology.getSurfaceId(4 + fixture[0], 4 + fixture[1]));
            assertFalse(grid.isWalkable(4 + fixture[0], 4 + fixture[1]));
            assertEquals("floor.authored", topology.getSurfaceId(4 + authored[0], 4 + authored[1]));
            for (int[] cell : shape.wall()) {
                int x = 4 + cell[0], y = 4 + cell[1];
                if (topology.isWindow(x, y)) continue;
                assertNull(topology.getSurfaceId(x, y));
                assertEquals(100, grid.getWallHp(x, y));
            }
            assertNull(topology.getSurfaceId(0, 0));
            assertEquals(new Random(42).nextLong(), ctx.rng.nextLong());
        }
    }
}
